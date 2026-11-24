package com.growit.roadmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.roadmap.dto.response.RoadmapResponse;
import com.growit.roadmap.dto.response.RoadmapSummaryResponse;
import com.growit.roadmap.entity.Roadmap;
import com.growit.roadmap.entity.RoadmapItem;
import com.growit.roadmap.entity.RoadmapItemOrigin;
import com.growit.roadmap.entity.RoadmapState;
import com.growit.roadmap.entity.RoadmapStep;
import com.growit.roadmap.repository.RoadmapItemRepository;
import com.growit.roadmap.repository.RoadmapRepository;
import com.growit.roadmap.repository.RoadmapStepRepository;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class RoadmapArchiveServiceTest {

	@Autowired
	private com.growit.roadmap.repository.RoadmapItemBlockRepository blockRepository;

	@Autowired
	private RoadmapRepository roadmapRepository;

	@Autowired
	private RoadmapStepRepository stepRepository;

	@Autowired
	private RoadmapItemRepository itemRepository;

	@Autowired
	private HobbyRepository hobbyRepository;

	private RoadmapService roadmapService() {
		return new RoadmapService(roadmapRepository, stepRepository, itemRepository, hobbyRepository,
				Clock.systemUTC(), blockRepository);
	}

	private RoadmapArchiveService service() {
		return new RoadmapArchiveService(roadmapRepository, stepRepository, itemRepository, hobbyRepository,
				roadmapService(), blockRepository);
	}

	private Hobby hobby(Long userId) {
		return hobbyRepository.save(new Hobby(userId, "기타", "목표"));
	}

	/** 단계 둘(각각 항목 둘, 첫 항목은 체크됨)을 가진 로드맵. archived면 보관 상태로 만든다. */
	private Roadmap seed(Long userId, Long hobbyId, String goal, boolean archived) {
		Roadmap roadmap = roadmapRepository.save(new Roadmap(userId, hobbyId, goal, null, 1));
		for (int s = 1; s <= 2; s++) {
			RoadmapStep step = stepRepository.save(new RoadmapStep(roadmap.getId(), s, "단계 " + s, null));
			for (int i = 1; i <= 2; i++) {
				RoadmapItem item = itemRepository
						.save(new RoadmapItem(step.getId(), i, "항목 " + s + "-" + i, RoadmapItemOrigin.AI));
				if (i == 1) {
					item.markDone(true, java.time.LocalDateTime.now());
				}
			}
		}
		if (archived) {
			roadmap.archive();
		}
		return roadmapRepository.saveAndFlush(roadmap);
	}

	private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, ErrorCode code) {
		assertThatThrownBy(callable).isInstanceOf(ApiException.class)
				.extracting(e -> ((ApiException) e).getCode()).isEqualTo(code);
	}

	@Test
	void listArchived_returnsOnlyArchived_newestFirst_withCounts() {
		Hobby hobby = hobby(1L);
		Roadmap first = seed(1L, hobby.getId(), "첫 목표", true);
		Roadmap second = seed(1L, hobby.getId(), "둘째 목표", true);
		seed(1L, hobby.getId(), "진행 중", false);

		List<RoadmapSummaryResponse> list = service().listArchived(1L, hobby.getId());

		assertThat(list).extracting(RoadmapSummaryResponse::id).containsExactly(second.getId(), first.getId());
		RoadmapSummaryResponse top = list.get(0);
		assertThat(top.goalText()).isEqualTo("둘째 목표");
		assertThat(top.stepCount()).isEqualTo(2);
		assertThat(top.itemCount()).isEqualTo(4);
		assertThat(top.doneCount()).isEqualTo(2);
		assertThat(top.createdAt()).isNotNull();
		assertThat(top.archivedAt()).isNotNull();
	}

	@Test
	void listArchived_isLimitedToTwenty_andEmptyWhenNone() {
		Hobby hobby = hobby(1L);
		assertThat(service().listArchived(1L, hobby.getId())).isEmpty();

		for (int i = 0; i < 22; i++) {
			roadmapRepository.save(archivedEmpty(1L, hobby.getId(), "목표 " + i));
		}
		roadmapRepository.flush();

		assertThat(service().listArchived(1L, hobby.getId())).hasSize(20);
	}

	private Roadmap archivedEmpty(Long userId, Long hobbyId, String goal) {
		Roadmap roadmap = new Roadmap(userId, hobbyId, goal, null, 1);
		roadmap.archive();
		return roadmap;
	}

	@Test
	void get_returnsActiveAndArchived_withStateAndSteps() {
		Hobby hobby = hobby(1L);
		Roadmap archived = seed(1L, hobby.getId(), "보관", true);
		Roadmap active = seed(1L, hobby.getId(), "진행", false);

		RoadmapResponse a = service().get(1L, archived.getId());
		assertThat(a.state()).isEqualTo("ARCHIVED");
		assertThat(a.steps()).hasSize(2);
		assertThat(a.steps().get(0).items()).hasSize(2);
		assertThat(service().get(1L, active.getId()).state()).isEqualTo("ACTIVE");
	}

	@Test
	void restore_whenNoActive_makesItActive() {
		Hobby hobby = hobby(1L);
		Roadmap archived = seed(1L, hobby.getId(), "보관", true);

		RoadmapResponse restored = service().restore(1L, archived.getId(), false);

		assertThat(restored.state()).isEqualTo("ACTIVE");
		assertThat(roadmapService().getActive(1L, hobby.getId()).id()).isEqualTo(archived.getId());
		assertThat(service().listArchived(1L, hobby.getId())).isEmpty();
	}

	@Test
	void restore_whenActiveExists_withoutSwap_is409_andChangesNothing() {
		Hobby hobby = hobby(1L);
		Roadmap archived = seed(1L, hobby.getId(), "보관", true);
		Roadmap active = seed(1L, hobby.getId(), "진행", false);

		assertCode(() -> service().restore(1L, archived.getId(), false), ErrorCode.ROADMAP_ACTIVE_EXISTS);

		assertThat(roadmapService().getActive(1L, hobby.getId()).id()).isEqualTo(active.getId());
		assertThat(roadmapRepository.findById(archived.getId()).orElseThrow().getState())
				.isEqualTo(RoadmapState.ARCHIVED);
	}

	@Test
	void restore_withSwap_archivesCurrent_andKeepsExactlyOneActive() {
		Hobby hobby = hobby(1L);
		Roadmap archived = seed(1L, hobby.getId(), "보관", true);
		Roadmap active = seed(1L, hobby.getId(), "진행", false);

		RoadmapResponse restored = service().restore(1L, archived.getId(), true);

		assertThat(restored.id()).isEqualTo(archived.getId());
		assertThat(roadmapRepository.findAll().stream().filter(r -> r.getState() == RoadmapState.ACTIVE)).hasSize(1);
		assertThat(roadmapService().getActive(1L, hobby.getId()).id()).isEqualTo(archived.getId());
		assertThat(service().listArchived(1L, hobby.getId())).extracting(RoadmapSummaryResponse::id)
				.containsExactly(active.getId());
	}

	@Test
	void restore_ofActiveRoadmap_isNotFound() {
		Hobby hobby = hobby(1L);
		Roadmap active = seed(1L, hobby.getId(), "진행", false);

		assertCode(() -> service().restore(1L, active.getId(), true), ErrorCode.NOT_FOUND);
	}

	@Test
	void delete_removesArchivedWithStepsAndItems_butRejectsActive() {
		Hobby hobby = hobby(1L);
		Roadmap archived = seed(1L, hobby.getId(), "보관", true);
		Roadmap active = seed(1L, hobby.getId(), "진행", false);
		long stepsBefore = stepRepository.count();
		long itemsBefore = itemRepository.count();

		assertCode(() -> service().delete(1L, active.getId()), ErrorCode.ROADMAP_NOT_ARCHIVED);

		service().delete(1L, archived.getId());
		roadmapRepository.flush();

		assertThat(roadmapRepository.findById(archived.getId())).isEmpty();
		assertThat(stepRepository.count()).isEqualTo(stepsBefore - 2);
		assertThat(itemRepository.count()).isEqualTo(itemsBefore - 4);
		assertThat(roadmapRepository.findById(active.getId())).isPresent();
	}

	@Test
	void deletedRoadmapItem_isNoLongerPending_soRecordSuggestionIsHidden() {
		Hobby hobby = hobby(1L);
		Roadmap archived = seed(1L, hobby.getId(), "보관", true);
		Long itemId = service().get(1L, archived.getId()).steps().get(0).items().get(1).id();
		RoadmapContextProvider provider = new RoadmapContextProvider(roadmapRepository, stepRepository, itemRepository);

		// 보관 중에도, 삭제된 뒤에도 기록이 가리키던 항목은 "없음"으로 처리돼 화면이 깨지지 않는다.
		assertThat(provider.findPendingItem(1L, itemId)).isNull();
		service().delete(1L, archived.getId());
		assertThat(provider.findPendingItem(1L, itemId)).isNull();
	}

	@Test
	void archivedRoadmap_isNotUsedAsAiContext() {
		Hobby hobby = hobby(1L);
		seed(1L, hobby.getId(), "보관", true);
		RoadmapContextProvider provider = new RoadmapContextProvider(roadmapRepository, stepRepository, itemRepository);

		assertThat(provider.currentContext(1L, hobby.getId())).isNull();
	}

	@Test
	void otherUsers_cannotListGetRestoreOrDelete() {
		Hobby hobby = hobby(1L);
		Roadmap archived = seed(1L, hobby.getId(), "보관", true);

		assertCode(() -> service().listArchived(2L, hobby.getId()), ErrorCode.NOT_FOUND);
		assertCode(() -> service().get(2L, archived.getId()), ErrorCode.NOT_FOUND);
		assertCode(() -> service().restore(2L, archived.getId(), true), ErrorCode.NOT_FOUND);
		assertCode(() -> service().delete(2L, archived.getId()), ErrorCode.NOT_FOUND);
		assertThat(roadmapRepository.findById(archived.getId())).isPresent();
	}
}
