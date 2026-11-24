package com.growit.roadmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.hobby.service.HobbyService;
import com.growit.record.repository.ActivityRecordRepository;
import com.growit.roadmap.dto.response.BlockResponse;
import com.growit.roadmap.dto.response.RoadmapResponse;
import com.growit.roadmap.entity.Roadmap;
import com.growit.roadmap.entity.RoadmapItem;
import com.growit.roadmap.entity.RoadmapItemOrigin;
import com.growit.roadmap.entity.RoadmapStep;
import com.growit.roadmap.repository.RoadmapItemBlockRepository;
import com.growit.roadmap.repository.RoadmapItemRepository;
import com.growit.roadmap.repository.RoadmapRepository;
import com.growit.roadmap.repository.RoadmapStepRepository;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class RoadmapBlockServiceTest {

	@Autowired
	private RoadmapRepository roadmapRepository;

	@Autowired
	private RoadmapStepRepository stepRepository;

	@Autowired
	private RoadmapItemRepository itemRepository;

	@Autowired
	private RoadmapItemBlockRepository blockRepository;

	@Autowired
	private HobbyRepository hobbyRepository;

	@Autowired
	private ActivityRecordRepository activityRecordRepository;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private RoadmapBlockService service() {
		return new RoadmapBlockService(roadmapRepository, stepRepository, itemRepository, blockRepository, objectMapper);
	}

	private RoadmapService roadmapService() {
		return new RoadmapService(roadmapRepository, stepRepository, itemRepository, hobbyRepository, Clock.systemUTC(),
				blockRepository);
	}

	private JsonNode json(String text) {
		try {
			return objectMapper.readTree(text);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private Hobby hobby(Long userId) {
		return hobbyRepository.save(new Hobby(userId, "기타", "코드 전환 익숙해지기"));
	}

	/** 단계 하나, 항목 둘을 가진 로드맵. 첫 항목 id를 돌려주려고 roadmap을 반환한다. */
	private Roadmap seed(Long userId, Long hobbyId) {
		Roadmap roadmap = roadmapRepository.save(new Roadmap(userId, hobbyId, "목표", null, 1));
		RoadmapStep step = stepRepository.save(new RoadmapStep(roadmap.getId(), 1, "단계 1", null));
		itemRepository.save(new RoadmapItem(step.getId(), 1, "항목 1", RoadmapItemOrigin.USER));
		itemRepository.save(new RoadmapItem(step.getId(), 2, "항목 2", RoadmapItemOrigin.USER));
		return roadmap;
	}

	private List<RoadmapItem> items(Roadmap roadmap) {
		RoadmapStep step = stepRepository.findByRoadmapIdOrderBySortOrderAscIdAsc(roadmap.getId()).get(0);
		return itemRepository.findByStepIdOrderBySortOrderAscIdAsc(step.getId());
	}

	private BlockResponse memo(Long userId, Long itemId, String text) {
		return service().create(userId, itemId, "MEMO", json("{\"text\":\"" + text + "\"}"));
	}

	@Test
	void create_list_update_delete_roundTrip() {
		Roadmap roadmap = seed(1L, hobby(1L).getId());
		Long itemId = items(roadmap).get(0).getId();

		BlockResponse memo = memo(1L, itemId, "천천히");
		BlockResponse link = service().create(1L, itemId, "link", json("{\"url\":\"https://example.com/a\",\"category\":\"VIDEO\"}"));

		List<BlockResponse> listed = service().list(1L, itemId);
		assertThat(listed).extracting(BlockResponse::kind).containsExactly("MEMO", "LINK");
		assertThat(listed).extracting(BlockResponse::position).containsExactly(1, 2);
		assertThat(link.payload().path("host").asText()).isEqualTo("example.com");

		BlockResponse updated = service().update(1L, memo.id(), json("{\"text\":\"더 천천히\"}"));
		assertThat(updated.payload().path("text").asText()).isEqualTo("더 천천히");
		assertThat(updated.kind()).isEqualTo("MEMO");

		service().delete(1L, memo.id());
		assertThat(service().list(1L, itemId)).extracting(BlockResponse::id).containsExactly(link.id());
	}

	@Test
	void create_unknownKindOrAiHowto_isRejected() {
		Roadmap roadmap = seed(1L, hobby(1L).getId());
		Long itemId = items(roadmap).get(0).getId();

		assertThatThrownBy(() -> service().create(1L, itemId, "GAME", json("{}")))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
		assertThatThrownBy(() -> service().create(1L, itemId, "AI_HOWTO", json("{\"steps\":[]}")))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
		assertThat(blockRepository.count()).isZero();
	}

	@Test
	void create_linkWithJavascriptScheme_isRejectedAndNothingSaved() {
		Roadmap roadmap = seed(1L, hobby(1L).getId());
		Long itemId = items(roadmap).get(0).getId();

		assertThatThrownBy(() -> service().create(1L, itemId, "LINK", json("{\"url\":\"javascript:alert(1)\"}")))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
		assertThat(blockRepository.count()).isZero();
	}

	@Test
	void update_cannotEditAiHowtoBlock() {
		Roadmap roadmap = seed(1L, hobby(1L).getId());
		Long itemId = items(roadmap).get(0).getId();
		com.growit.roadmap.entity.RoadmapItemBlock saved = blockRepository.save(new com.growit.roadmap.entity.RoadmapItemBlock(
				itemId, com.growit.roadmap.entity.BlockKind.AI_HOWTO, 1, "{\"steps\":[\"a\"]}"));

		assertThatThrownBy(() -> service().update(1L, saved.getId(), json("{\"steps\":[\"b\"]}")))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void create_thirteenthBlockOnOneItem_isRejected() {
		Roadmap roadmap = seed(1L, hobby(1L).getId());
		Long itemId = items(roadmap).get(0).getId();
		for (int i = 0; i < 12; i++) {
			memo(1L, itemId, "메모 " + i);
		}

		assertThatThrownBy(() -> memo(1L, itemId, "열세 번째"))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
		assertThat(blockRepository.countByItemId(itemId)).isEqualTo(12);
	}

	@Test
	void create_hundredTwentyFirstBlockInOneRoadmap_isRejected() {
		Roadmap roadmap = seed(1L, hobby(1L).getId());
		RoadmapStep step = stepRepository.findByRoadmapIdOrderBySortOrderAscIdAsc(roadmap.getId()).get(0);
		// 항목 10개 × 12개 = 120개. 기본으로 있던 항목 둘 중 하나를 포함해 열 개를 채운다.
		List<RoadmapItem> ten = new java.util.ArrayList<>(items(roadmap));
		for (int i = 0; i < 8; i++) {
			ten.add(itemRepository.save(new RoadmapItem(step.getId(), 3 + i, "추가 " + i, RoadmapItemOrigin.USER)));
		}
		for (RoadmapItem item : ten) {
			for (int i = 0; i < 12; i++) {
				memo(1L, item.getId(), "m");
			}
		}
		assertThat(blockRepository.countByRoadmapId(roadmap.getId())).isEqualTo(120);
		RoadmapItem eleventh = itemRepository.save(new RoadmapItem(step.getId(), 11, "열한 번째", RoadmapItemOrigin.USER));

		assertThatThrownBy(() -> memo(1L, eleventh.getId(), "넘침"))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void reorder_appliesGivenOrder_andRejectsIncompleteOrForeignIds() {
		Roadmap roadmap = seed(1L, hobby(1L).getId());
		List<RoadmapItem> items = items(roadmap);
		Long itemId = items.get(0).getId();
		BlockResponse a = memo(1L, itemId, "a");
		BlockResponse b = memo(1L, itemId, "b");
		BlockResponse c = memo(1L, itemId, "c");
		BlockResponse other = memo(1L, items.get(1).getId(), "다른 항목");

		List<BlockResponse> reordered = service().reorder(1L, itemId, List.of(c.id(), a.id(), b.id()));

		assertThat(reordered).extracting(BlockResponse::id).containsExactly(c.id(), a.id(), b.id());
		assertThat(service().list(1L, itemId)).extracting(BlockResponse::id).containsExactly(c.id(), a.id(), b.id());
		assertThatThrownBy(() -> service().reorder(1L, itemId, List.of(a.id(), b.id())))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
		assertThatThrownBy(() -> service().reorder(1L, itemId, List.of(a.id(), b.id(), other.id())))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
		assertThatThrownBy(() -> service().reorder(1L, itemId, List.of(a.id(), a.id(), b.id())))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void otherUsersItemAndBlock_areNotFoundForEveryOperation() {
		Roadmap roadmap = seed(1L, hobby(1L).getId());
		Long itemId = items(roadmap).get(0).getId();
		BlockResponse mine = memo(1L, itemId, "내 메모");

		assertNotFound(() -> service().list(2L, itemId));
		assertNotFound(() -> service().create(2L, itemId, "MEMO", json("{\"text\":\"x\"}")));
		assertNotFound(() -> service().update(2L, mine.id(), json("{\"text\":\"x\"}")));
		assertNotFound(() -> service().delete(2L, mine.id()));
		assertNotFound(() -> service().reorder(2L, itemId, List.of(mine.id())));
		assertNotFound(() -> service().list(1L, 999999L));
		assertThat(blockRepository.count()).isEqualTo(1);
	}

	@Test
	void archivedRoadmap_isReadOnly_butBlocksCanStillBeListed() {
		Roadmap roadmap = seed(1L, hobby(1L).getId());
		Long itemId = items(roadmap).get(0).getId();
		BlockResponse mine = memo(1L, itemId, "남겨 둘 메모");
		roadmap.archive();
		roadmapRepository.saveAndFlush(roadmap);

		assertThat(service().list(1L, itemId)).hasSize(1);
		assertNotFound(() -> memo(1L, itemId, "새 메모"));
		assertNotFound(() -> service().update(1L, mine.id(), json("{\"text\":\"x\"}")));
		assertNotFound(() -> service().delete(1L, mine.id()));
		assertNotFound(() -> service().reorder(1L, itemId, List.of(mine.id())));
	}

	@Test
	void roadmapResponse_carriesBlockCountPerItem() {
		Hobby hobby = hobby(1L);
		Roadmap roadmap = seed(1L, hobby.getId());
		List<RoadmapItem> items = items(roadmap);
		memo(1L, items.get(0).getId(), "a");
		memo(1L, items.get(0).getId(), "b");

		RoadmapResponse response = roadmapService().getActive(1L, hobby.getId());

		assertThat(response.steps().get(0).items()).extracting("blockCount").containsExactly(2, 0);
	}

	@Test
	void deletingItemStepRoadmapHobbyOrAccount_removesBlocksToo() {
		Hobby hobby = hobby(1L);
		Roadmap roadmap = seed(1L, hobby.getId());
		List<RoadmapItem> items = items(roadmap);
		memo(1L, items.get(0).getId(), "a");
		memo(1L, items.get(1).getId(), "b");
		Long stepId = items.get(0).getStepId();

		roadmapService().deleteItem(1L, items.get(0).getId());
		assertThat(blockRepository.count()).isEqualTo(1);

		roadmapService().deleteStep(1L, stepId);
		assertThat(blockRepository.count()).isZero();
	}

	@Test
	void deletingHobby_removesItsBlocks_andKeepsOthers() {
		Hobby mine = hobby(1L);
		Hobby others = hobbyRepository.save(new Hobby(2L, "러닝", "5km"));
		Roadmap myRoadmap = seed(1L, mine.getId());
		Roadmap otherRoadmap = seed(2L, others.getId());
		memo(1L, items(myRoadmap).get(0).getId(), "내 것");
		memo(2L, items(otherRoadmap).get(0).getId(), "남의 것");

		new HobbyService(hobbyRepository, activityRecordRepository, roadmapRepository, stepRepository, itemRepository,
				blockRepository).delete(1L, mine.getId());

		assertThat(blockRepository.count()).isEqualTo(1);
	}

	@Test
	void repositoryDeletes_byRoadmapAndUser_removeOnlyTheirBlocks() {
		Roadmap mine = seed(1L, hobby(1L).getId());
		Roadmap others = seed(2L, hobbyRepository.save(new Hobby(2L, "러닝", "5km")).getId());
		memo(1L, items(mine).get(0).getId(), "내 것");
		memo(2L, items(others).get(0).getId(), "남의 것");

		blockRepository.deleteByRoadmapId(mine.getId());
		assertThat(blockRepository.count()).isEqualTo(1);

		memo(1L, items(mine).get(0).getId(), "다시");
		blockRepository.deleteByUserId(1L);
		assertThat(blockRepository.count()).isEqualTo(1);
		assertThat(blockRepository.countByRoadmapId(others.getId())).isEqualTo(1);
	}

	private void assertNotFound(org.junit.jupiter.api.function.Executable executable) {
		ApiException e = org.junit.jupiter.api.Assertions.assertThrows(ApiException.class, executable);
		assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND);
	}
}
