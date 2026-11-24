package com.growit.roadmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.ai.client.AiChatClient;
import com.growit.ai.client.AiChatResponse;
import com.growit.ai.config.AiProperties;
import com.growit.ai.repository.AiLogRepository;
import com.growit.ai.service.AiCallService;
import com.growit.ai.service.AiResponseValidator;
import com.growit.ai.service.AiUsageService;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.record.entity.ActivityRecord;
import com.growit.record.entity.Feeling;
import com.growit.record.repository.ActivityRecordRepository;
import com.growit.roadmap.dto.response.AiHowtoResponse;
import com.growit.roadmap.entity.Roadmap;
import com.growit.roadmap.entity.RoadmapItem;
import com.growit.roadmap.entity.RoadmapItemOrigin;
import com.growit.roadmap.entity.RoadmapStep;
import com.growit.roadmap.repository.RoadmapItemBlockRepository;
import com.growit.roadmap.repository.RoadmapItemRepository;
import com.growit.roadmap.repository.RoadmapRepository;
import com.growit.roadmap.repository.RoadmapStepRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;

@DataJpaTest
class RoadmapHowtoServiceTest {

	private static final String VALID = """
			{"steps":["왼손 검지로 1번 줄을 눌러요","나머지 손가락을 하나씩 올려요","한 줄씩 쳐서 소리를 확인해요"],
			 "checks":["여섯 줄이 모두 또렷하게 울려요"],
			 "mistakes":["손가락 끝이 아니라 면으로 눌러요"],
			 "query":"기타 F 코드 잡는 법"}""";

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

	@Autowired
	private AiLogRepository aiLogRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private final AiChatClient aiChatClient = mock(AiChatClient.class);
	private final ObjectMapper objectMapper = new ObjectMapper();

	private RoadmapHowtoService service(int roadmapDailyLimit) {
		AiProperties properties = new AiProperties();
		properties.setApiKey("key");
		properties.setModel("model");
		properties.setRoadmapUserDailyLimit(roadmapDailyLimit);
		AiUsageService usage = new AiUsageService(aiLogRepository, properties);
		AiCallService call = new AiCallService(properties, aiChatClient, aiLogRepository, usage, objectMapper);
		RoadmapBlockService blocks = new RoadmapBlockService(roadmapRepository, stepRepository, itemRepository,
				blockRepository, objectMapper);
		return new RoadmapHowtoService(roadmapRepository, stepRepository, blockRepository, hobbyRepository,
				activityRecordRepository, blocks, call, new AiResponseValidator(objectMapper), objectMapper,
				Clock.systemUTC(), transactionManager);
	}

	private RoadmapHowtoService service() {
		return service(100);
	}

	private void stub(String... responses) {
		AiChatResponse first = new AiChatResponse(responses[0], 10, "model");
		AiChatResponse[] rest = new AiChatResponse[responses.length - 1];
		for (int i = 1; i < responses.length; i++) {
			rest[i - 1] = new AiChatResponse(responses[i], 10, "model");
		}
		given(aiChatClient.complete(anyString(), anyString(), anyInt())).willReturn(first, rest);
	}

	/** 진행 중 로드맵과 첫 항목 id. 분야에는 기록 네 개가 있다(최근 세 개만 AI 입력에 쓴다). */
	private Long seedItem(Long userId) {
		Hobby hobby = hobbyRepository.save(new Hobby(userId, "기타", "코드 전환 익숙해지기"));
		for (int i = 0; i < 4; i++) {
			activityRecordRepository.save(new ActivityRecord(userId, hobby.getId(), LocalDate.now().minusDays(i),
					"기록 " + i, 20, null, Feeling.PROUD, false));
		}
		Roadmap roadmap = roadmapRepository.save(new Roadmap(userId, hobby.getId(), "코드 전환 익숙해지기", null, 1));
		RoadmapStep step = stepRepository.save(new RoadmapStep(roadmap.getId(), 1, "단계 1", null));
		return itemRepository.save(new RoadmapItem(step.getId(), 1, "F 코드 잡기", RoadmapItemOrigin.USER)).getId();
	}

	private void assertInvalidFailure(Long userId, Long itemId) {
		assertThatThrownBy(() -> service().generate(userId, itemId))
				.isInstanceOfSatisfying(ApiException.class, e -> {
					assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
					assertThat(e.getMessage()).isEqualTo("지금은 방법을 만들지 못했어요. 잠시 후 다시 시도해 주세요.");
				});
	}

	@Test
	void generate_validResponse_savesBlockWithMetadata_andSuggestsSearch() {
		Long itemId = seedItem(1L);
		stub(VALID);

		AiHowtoResponse response = service().generate(1L, itemId);

		assertThat(response.suggestedSearchQuery()).isEqualTo("기타 F 코드 잡는 법");
		assertThat(response.block().kind()).isEqualTo("AI_HOWTO");
		assertThat(response.block().payload().path("steps")).hasSize(3);
		assertThat(response.block().payload().path("promptVersion").asInt()).isEqualTo(RoadmapHowtoService.PROMPT_VERSION);
		assertThat(response.block().payload().path("generatedAt").asText()).isNotBlank();
		assertThat(blockRepository.countByItemId(itemId)).isEqualTo(1);
		verify(aiChatClient, times(1)).complete(anyString(), anyString(), anyInt());
	}

	@Test
	void generate_sendsHobbyGoalStepItemAndOnlyThreeRecentRecords() {
		Long itemId = seedItem(1L);
		org.mockito.ArgumentCaptor<String> userData = org.mockito.ArgumentCaptor.forClass(String.class);
		given(aiChatClient.complete(anyString(), userData.capture(), anyInt()))
				.willReturn(new AiChatResponse(VALID, 10, "model"));

		service().generate(1L, itemId);

		String sent = userData.getValue();
		assertThat(sent).contains("기타", "코드 전환 익숙해지기", "단계 1", "F 코드 잡기", "기록 0", "기록 1", "기록 2");
		assertThat(sent).doesNotContain("기록 3");
	}

	@Test
	void generate_regenerate_overwritesSameBlock_andCountsUpToThreePerDay() {
		Long itemId = seedItem(1L);
		stub(VALID);
		service().generate(1L, itemId);
		Long blockId = blockRepository.findByItemIdOrderBySortOrderAscIdAsc(itemId).get(0).getId();

		for (int i = 0; i < 3; i++) {
			AiHowtoResponse again = service().generate(1L, itemId);
			assertThat(again.block().id()).isEqualTo(blockId);
			assertThat(again.block().payload().path("regenCount").asInt()).isEqualTo(i + 1);
		}

		assertThatThrownBy(() -> service().generate(1L, itemId))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_LIMIT_EXCEEDED));
		assertThat(blockRepository.countByItemId(itemId)).isEqualTo(1);
		verify(aiChatClient, times(4)).complete(anyString(), anyString(), anyInt());
	}

	@Test
	void generate_firstResponseInvalid_retriesOnce_andUsesSecond() {
		Long itemId = seedItem(1L);
		stub("{\"steps\":[\"하나\"],\"checks\":[\"a\"],\"query\":\"q\"}", VALID);

		AiHowtoResponse response = service().generate(1L, itemId);

		assertThat(response.block().payload().path("steps")).hasSize(3);
		verify(aiChatClient, times(2)).complete(anyString(), anyString(), anyInt());
	}

	@Test
	void generate_bothResponsesInvalid_fails400WithGuide_andSavesNothing() {
		Long itemId = seedItem(1L);
		stub("not json", "{\"steps\":[]}");

		assertInvalidFailure(1L, itemId);

		assertThat(blockRepository.count()).isZero();
		verify(aiChatClient, times(2)).complete(anyString(), anyString(), anyInt());
	}

	@Test
	void generate_urlLikeText_isRejected() {
		Long itemId = seedItem(1L);
		String withUrl = VALID.replace("기타 F 코드 잡는 법", "www.example.com 에서 보기");
		String withHttp = VALID.replace("왼손 검지로 1번 줄을 눌러요", "https://example.com 을 열어요");
		String withDomain = VALID.replace("한 줄씩 쳐서 소리를 확인해요", "youtube.com 에서 찾아봐요");
		stub(withUrl, withHttp);
		assertInvalidFailure(1L, itemId);

		stub(withDomain, withDomain);
		assertInvalidFailure(1L, itemId);
		assertThat(blockRepository.count()).isZero();
	}

	@Test
	void generate_forbiddenWordsAndCountsAndLengths_areRejected() {
		Long itemId = seedItem(1L);
		String ability = VALID.replace("손가락 끝이 아니라 면으로 눌러요", "실력이 금방 늘어요");
		String medical = VALID.replace("여섯 줄이 모두 또렷하게 울려요", "통증이 있으면 약을 복용해요");
		String tooFewSteps = "{\"steps\":[\"하나\",\"둘\"],\"checks\":[\"a\"],\"mistakes\":[],\"query\":\"q\"}";
		String longStep = VALID.replace("왼손 검지로 1번 줄을 눌러요", "가".repeat(41));
		String longQuery = VALID.replace("기타 F 코드 잡는 법", "가".repeat(21));
		String noChecks = VALID.replace("\"checks\":[\"여섯 줄이 모두 또렷하게 울려요\"]", "\"checks\":[]");

		for (String bad : new String[] {ability, medical, tooFewSteps, longStep, longQuery, noChecks}) {
			stub(bad, bad);
			assertInvalidFailure(1L, itemId);
		}
		assertThat(blockRepository.count()).isZero();
	}

	@Test
	void generate_mistakesMayBeMissing() {
		Long itemId = seedItem(1L);
		stub(VALID.replace("\"mistakes\":[\"손가락 끝이 아니라 면으로 눌러요\"],", ""));

		AiHowtoResponse response = service().generate(1L, itemId);

		assertThat(response.block().payload().path("mistakes")).isEmpty();
	}

	@Test
	void generate_sharesRoadmapDailyLimit_andLimitFailureIsReported() {
		Long itemId = seedItem(1L);
		stub(VALID);
		RoadmapHowtoService limited = service(1);
		limited.generate(1L, itemId);

		assertThatThrownBy(() -> limited.generate(1L, itemId))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_LIMIT_EXCEEDED));
	}

	@Test
	void generate_otherUsersItem_isNotFound_andProviderNotCalled() {
		Long itemId = seedItem(1L);

		assertThatThrownBy(() -> service().generate(2L, itemId))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
		verify(aiChatClient, times(0)).complete(anyString(), anyString(), anyInt());
	}

	@Test
	void generate_archivedRoadmap_isNotFound() {
		Long itemId = seedItem(1L);
		Roadmap roadmap = roadmapRepository.findAll().get(0);
		roadmap.archive();
		roadmapRepository.saveAndFlush(roadmap);

		assertThatThrownBy(() -> service().generate(1L, itemId))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void validate_acceptsExtraFields_butNeverStoresThem() {
		stub(VALID.replace("\"query\"", "\"url\":\"x\",\"query\""));
		Long itemId = seedItem(1L);

		AiHowtoResponse response = service().generate(1L, itemId);

		assertThat(response.block().payload().has("url")).isFalse();
	}
}
