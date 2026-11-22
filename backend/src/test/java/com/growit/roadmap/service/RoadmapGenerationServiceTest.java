package com.growit.roadmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import com.growit.roadmap.dto.response.RoadmapResponse;
import com.growit.roadmap.entity.Roadmap;
import com.growit.roadmap.repository.RoadmapItemRepository;
import com.growit.roadmap.repository.RoadmapRepository;
import com.growit.roadmap.repository.RoadmapStepRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;

@DataJpaTest
class RoadmapGenerationServiceTest {

	private static final String VALID = """
			{"steps":[
			 {"title":"Am 코드 손에 익히기","periodHint":"약 1~2주","items":["Am 코드를 짚고 소리 확인하기","C에서 Am으로 천천히 열 번 바꿔 보기"]},
			 {"title":"세 코드 이어 치기","periodHint":"약 2주","items":["C, Am, G를 한 번씩 이어 쳐 보기","박자 없이 세 코드 돌려 보기","느린 박자에 맞춰 한 바퀴 쳐 보기"]},
			 {"title":"짧은 곡 한 마디 치기","periodHint":null,"items":["쉬운 곡의 첫 마디만 쳐 보기","첫 두 마디를 이어 쳐 보기"]}
			]}""";

	@Autowired
	private HobbyRepository hobbyRepository;

	@Autowired
	private ActivityRecordRepository activityRecordRepository;

	@Autowired
	private RoadmapRepository roadmapRepository;

	@Autowired
	private RoadmapStepRepository stepRepository;

	@Autowired
	private RoadmapItemRepository itemRepository;

	@Autowired
	private AiLogRepository aiLogRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private final AiChatClient aiChatClient = mock(AiChatClient.class);
	private final ObjectMapper objectMapper = new ObjectMapper();

	private RoadmapGenerationService service() {
		AiProperties properties = new AiProperties();
		properties.setApiKey("key");
		properties.setModel("model");
		properties.setRoadmapUserDailyLimit(100);
		AiUsageService usage = new AiUsageService(aiLogRepository, properties);
		AiCallService call = new AiCallService(properties, aiChatClient, aiLogRepository, usage, objectMapper);
		RoadmapService roadmapService = new RoadmapService(roadmapRepository, stepRepository, itemRepository,
				hobbyRepository, Clock.systemUTC());
		return new RoadmapGenerationService(hobbyRepository, activityRecordRepository, roadmapRepository,
				stepRepository, itemRepository, call, new AiResponseValidator(objectMapper), roadmapService,
				Clock.systemUTC(), transactionManager);
	}

	private Hobby hobby(Long userId, String goal) {
		return hobbyRepository.save(new Hobby(userId, "기타", goal));
	}

	private void stub(String... responses) {
		AiChatResponse first = new AiChatResponse(responses[0], 10, "model");
		AiChatResponse[] rest = new AiChatResponse[responses.length - 1];
		for (int i = 1; i < responses.length; i++) {
			rest[i - 1] = new AiChatResponse(responses[i], 10, "model");
		}
		given(aiChatClient.complete(anyString(), anyString(), anyInt())).willReturn(first, rest);
	}

	private java.util.List<String> captureLogs() {
		ch.qos.logback.classic.Logger logger =
				(ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(RoadmapGenerationService.class);
		ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
				new ch.qos.logback.core.read.ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		return new java.util.AbstractList<>() {
			@Override
			public String get(int index) {
				return appender.list.get(index).getFormattedMessage();
			}

			@Override
			public int size() {
				return appender.list.size();
			}
		};
	}

	private static ErrorCode codeOf(Throwable e) {
		return ((ApiException) e).getCode();
	}

	@Test
	void create_success_savesStepsAndItems_andSendsRecentRecordsAsData() {
		Hobby hobby = hobby(1L, "코드 전환 익숙해지기");
		activityRecordRepository.save(new ActivityRecord(1L, hobby.getId(), LocalDate.of(2026, 11, 1),
				"C와 G를 쳤다", 20, "Am이 어려웠다", Feeling.NEUTRAL, false));
		stub(VALID);
		org.mockito.ArgumentCaptor<String> user = org.mockito.ArgumentCaptor.forClass(String.class);

		RoadmapResponse response = service().create(1L, hobby.getId(), LocalDate.now().plusDays(40));

		assertThat(response.steps()).hasSize(3);
		assertThat(response.steps().get(0).periodHint()).isEqualTo("약 1~2주");
		assertThat(response.steps().get(2).periodHint()).isNull();
		assertThat(response.steps().get(1).items()).hasSize(3);
		assertThat(response.steps().get(0).items().get(0).source()).isEqualTo("AI");
		assertThat(response.goalText()).isEqualTo("코드 전환 익숙해지기");
		assertThat(response.targetDate()).isEqualTo(LocalDate.now().plusDays(40));
		assertThat(roadmapRepository.count()).isEqualTo(1);
		verify(aiChatClient).complete(anyString(), user.capture(), anyInt());
		assertThat(user.getValue()).contains("Am이 어려웠다").contains("\"totalRecordCount\":1")
				.contains("코드 전환 익숙해지기");
	}

	@Test
	void create_withoutGoal_isRejectedBeforeAnyAiCall() {
		Hobby hobby = hobby(1L, "  ");

		assertThatThrownBy(() -> service().create(1L, hobby.getId(), null)).isInstanceOf(ApiException.class)
				.hasMessage("목표를 먼저 적어 주세요").satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.VALIDATION_ERROR));
		verify(aiChatClient, never()).complete(anyString(), anyString(), anyInt());
	}

	@Test
	void create_whenActiveExists_isConflict() {
		Hobby hobby = hobby(1L, "목표");
		roadmapRepository.save(new Roadmap(1L, hobby.getId(), "목표", null, 1));

		assertThatThrownBy(() -> service().create(1L, hobby.getId(), null))
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.ROADMAP_ACTIVE));
		verify(aiChatClient, never()).complete(anyString(), anyString(), anyInt());
	}

	@Test
	void create_afterThreeToday_isLimited() {
		Hobby hobby = hobby(1L, "목표");
		for (int i = 0; i < 3; i++) {
			Roadmap old = roadmapRepository.save(new Roadmap(1L, hobby.getId(), "목표", null, 1));
			old.archive();
		}

		assertThatThrownBy(() -> service().create(1L, hobby.getId(), null))
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.AI_LIMIT_EXCEEDED));
		verify(aiChatClient, never()).complete(anyString(), anyString(), anyInt());
	}

	@Test
	void create_otherUsersHobby_isNotFound() {
		Hobby hobby = hobby(1L, "목표");

		assertThatThrownBy(() -> service().create(2L, hobby.getId(), null))
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void create_pastTargetDate_isRejected() {
		Hobby hobby = hobby(1L, "목표");

		assertThatThrownBy(() -> service().create(1L, hobby.getId(), LocalDate.now().minusDays(2)))
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void create_invalidResponses_retryOnceThenFail_andLogReason() {
		List<String> invalid = List.of(
				"not json",
				"{\"steps\":[]}",
				VALID.replace("\"세 코드 이어 치기\"", "\"둘째\"").replaceFirst("\\{\"title\":\"짧은 곡.*?\\]\\}", "{\"title\":\"x\",\"periodHint\":null,\"items\":[\"하나뿐\"]}"),
				VALID.replace("Am 코드를 짚고 소리 확인하기", "매일 Am 코드를 짚고 소리 확인하기"),
				VALID.replace("Am 코드를 짚고 소리 확인하기", "11월 25일까지 Am 코드 짚기"),
				VALID.replace("약 1~2주", "2주"),
				VALID.replace("Am 코드를 짚고 소리 확인하기", "C에서 Am으로 천천히 열 번 바꿔 보기"),
				VALID.replace("Am 코드를 짚고 소리 확인하기", "가".repeat(61)));
		for (String body : invalid) {
			Hobby hobby = hobby(1L, "목표");
			stub(body, body);
			java.util.List<String> logs = captureLogs();

			assertThatThrownBy(() -> service().create(1L, hobby.getId(), null)).isInstanceOf(ApiException.class)
					.hasMessage(RoadmapGenerationService.FAIL_MESSAGE)
					.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.VALIDATION_ERROR));

			assertThat(roadmapRepository.findAll()).noneMatch(r -> r.getHobbyId().equals(hobby.getId()));
			assertThat(logs).as(body).filteredOn(l -> l.contains("ROADMAP_DROPPED")).hasSize(2);
		}
		verify(aiChatClient, times(invalid.size() * 2)).complete(anyString(), anyString(), anyInt());
	}

	@Test
	void createManual_makesEmptyActiveRoadmap_withoutAiCallOrLogOrLimit() {
		Hobby hobby = hobby(1L, "코드 전환 익숙해지기");
		for (int i = 0; i < 3; i++) {
			roadmapRepository.save(new Roadmap(1L, hobby.getId(), "목표", null, 1)).archive();
		}

		RoadmapResponse response = service().createManual(1L, hobby.getId(), LocalDate.now().plusDays(10));

		assertThat(response.steps()).isEmpty();
		assertThat(response.goalText()).isEqualTo("코드 전환 익숙해지기");
		assertThat(roadmapRepository.findFirstByHobbyIdAndUserIdAndState(hobby.getId(), 1L,
				com.growit.roadmap.entity.RoadmapState.ACTIVE)).isPresent();
		verify(aiChatClient, never()).complete(anyString(), anyString(), anyInt());
		assertThat(aiLogRepository.findAll()).isEmpty();
		// 빈 로드맵은 기록 분석 입력에 넣지 않는다.
		assertThat(new RoadmapContextProvider(roadmapRepository, stepRepository, itemRepository)
				.currentContext(1L, hobby.getId())).isNull();
	}

	@Test
	void createManual_rejectsMissingGoal_activeExisting_andOtherUsersHobby() {
		Hobby noGoal = hobby(1L, " ");
		assertThatThrownBy(() -> service().createManual(1L, noGoal.getId(), null))
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.VALIDATION_ERROR));

		Hobby hobby = hobby(1L, "목표");
		service().createManual(1L, hobby.getId(), null);
		assertThatThrownBy(() -> service().createManual(1L, hobby.getId(), null))
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.ROADMAP_ACTIVE));
		assertThatThrownBy(() -> service().createManual(2L, hobby.getId(), null))
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void create_retryIsNotCountedTowardTheLimit_andUsesAtLeast1500Tokens() {
		Hobby hobby = hobby(1L, "목표");
		stub("{\"steps\":[]}", VALID);
		org.mockito.ArgumentCaptor<Integer> tokens = org.mockito.ArgumentCaptor.forClass(Integer.class);

		service().create(1L, hobby.getId(), null);

		verify(aiChatClient, times(2)).complete(anyString(), anyString(), tokens.capture());
		assertThat(tokens.getAllValues()).containsOnly(1500);
		assertThat(aiLogRepository.findAll()).filteredOn(l -> "ROADMAP".equals(l.getResultType()))
				.extracting(l -> l.getState()).containsExactlyInAnyOrder("OK", "RETRY");
	}

	@Test
	void create_whenUserLimitReached_saysLimitMessageAndLogsRoadmapFailed() {
		Hobby hobby = hobby(1L, "목표");
		AiProperties properties = new AiProperties();
		properties.setApiKey("key");
		properties.setModel("model");
		properties.setRoadmapUserDailyLimit(0);
		AiUsageService usage = new AiUsageService(aiLogRepository, properties);
		AiCallService call = new AiCallService(properties, aiChatClient, aiLogRepository, usage, objectMapper);
		RoadmapGenerationService limited = new RoadmapGenerationService(hobbyRepository, activityRecordRepository,
				roadmapRepository, stepRepository, itemRepository, call, new AiResponseValidator(objectMapper),
				new RoadmapService(roadmapRepository, stepRepository, itemRepository, hobbyRepository, Clock.systemUTC()),
				Clock.systemUTC(), transactionManager);
		java.util.List<String> logs = captureLogs();

		assertThatThrownBy(() -> limited.create(1L, hobby.getId(), null)).isInstanceOf(ApiException.class)
				.hasMessage(RoadmapGenerationService.LIMIT_MESSAGE)
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.AI_LIMIT_EXCEEDED));
		assertThat(logs).anyMatch(l -> l.contains("ROADMAP_FAILED reason=LIMIT limit=AI_ROADMAP_USER_DAILY_LIMIT"));
		verify(aiChatClient, never()).complete(anyString(), anyString(), anyInt());
	}

	@Test
	void create_whenProviderBusy_keepsBusyMessage() {
		Hobby hobby = hobby(1L, "목표");
		given(aiChatClient.complete(anyString(), anyString(), anyInt())).willThrow(
				new com.growit.ai.client.AiClientException(com.growit.ai.client.AiClientException.Type.RATE_LIMITED, "429"));
		java.util.List<String> logs = captureLogs();

		assertThatThrownBy(() -> service().create(1L, hobby.getId(), null))
				.hasMessageContaining("지금은 AI 요청이 많아요")
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.AI_BUSY));
		assertThat(logs).anyMatch(l -> l.contains("ROADMAP_FAILED reason=BUSY"));
	}

	@Test
	void create_firstInvalidThenValid_succeedsWithCorrectionPrompt() {
		Hobby hobby = hobby(1L, "목표");
		stub("{\"steps\":[]}", VALID);
		org.mockito.ArgumentCaptor<String> system = org.mockito.ArgumentCaptor.forClass(String.class);

		RoadmapResponse response = service().create(1L, hobby.getId(), null);

		assertThat(response.steps()).hasSize(3);
		verify(aiChatClient, times(2)).complete(system.capture(), anyString(), anyInt());
		assertThat(system.getAllValues().get(1)).contains("[교정 지시]");
		assertThat(system.getAllValues().get(0)).doesNotContain("[교정 지시]");
	}

	@Test
	void create_promptFileForbidsDatesAndMedicalAdvice() throws Exception {
		String prompt = new String(getClass().getResourceAsStream("/roadmap-system.txt").readAllBytes(),
				java.nio.charset.StandardCharsets.UTF_8);

		assertThat(prompt).contains("3~5개 단계").contains("의학·식이·부상 치료").contains("날짜별 일정은 만들지 않는다");
	}
}
