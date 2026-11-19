package com.growit.record.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.ai.client.AiChatClient;
import com.growit.ai.client.AiChatResponse;
import com.growit.ai.config.AiProperties;
import com.growit.ai.entity.AiLogState;
import com.growit.ai.entity.AiResultType;
import com.growit.ai.repository.AiLogRepository;
import com.growit.ai.service.AiCallService;
import com.growit.ai.service.AiResponseValidator;
import com.growit.ai.service.AiUsageService;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.record.entity.ActivityRecord;
import com.growit.record.entity.AiStatus;
import com.growit.record.entity.Feeling;
import com.growit.record.repository.ActivityRecordRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;

@DataJpaTest
class RecordAnalysisServiceTest {

	@Autowired
	private ActivityRecordRepository activityRecordRepository;

	@Autowired
	private HobbyRepository hobbyRepository;

	@Autowired
	private AiLogRepository aiLogRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private final AiChatClient aiChatClient = mock(AiChatClient.class);
	private final ObjectMapper objectMapper = new ObjectMapper();

	private AiProperties enabledProperties() {
		AiProperties properties = new AiProperties();
		properties.setApiKey("key");
		properties.setModel("model");
		properties.setMaxConcurrency(2);
		return properties;
	}

	private RecordAnalysisService service(AiProperties properties) {
		AiUsageService usageService = new AiUsageService(aiLogRepository, properties);
		AiCallService callService = new AiCallService(properties, aiChatClient, aiLogRepository, usageService,
				objectMapper);
		AiResponseValidator validator = new AiResponseValidator(objectMapper);
		return new RecordAnalysisService(activityRecordRepository, hobbyRepository, callService, validator,
				aiLogRepository, objectMapper, transactionManager);
	}

	private Long createHobby(Long userId) {
		return hobbyRepository.save(new Hobby(userId, "기타", "코드 전환 매끄럽게")).getId();
	}

	private ActivityRecord createPendingRecord(Long userId, Long hobbyId, LocalDate date, String content) {
		ActivityRecord record = new ActivityRecord(userId, hobbyId, date, content, 20, "코드 전환이 느렸다", Feeling.PROUD,
				false);
		record.markAnalysisPending(LocalDateTime.now());
		return activityRecordRepository.save(record);
	}

	private void stubAiResponse(String json) {
		given(aiChatClient.complete(anyString(), anyString())).willReturn(new AiChatResponse(json, 10, "model"));
	}

	@Test
	void analyze_success_savesTitleTagsRelatedAndChange() {
		Long hobbyId = createHobby(1L);
		ActivityRecord previous = createPendingRecord(1L, hobbyId, LocalDate.of(2026, 10, 1), "이전 연습");
		previous.markAnalysisDone(null);
		activityRecordRepository.save(previous);
		ActivityRecord current = createPendingRecord(1L, hobbyId, LocalDate.of(2026, 10, 10), "코드 전환 연습");

		stubAiResponse(("{\"title\":\"코드 전환 연습\",\"tags\":[\"코드전환\"],\"relatedRecordIds\":[%d],"
				+ "\"change\":{\"text\":\"기록상 코드 전환이 매끄러워졌어요\",\"evidenceRecordId\":%d}}")
				.formatted(previous.getId(), previous.getId()));

		service(enabledProperties()).analyze(current.getId(), 1L);

		ActivityRecord result = activityRecordRepository.findById(current.getId()).orElseThrow();
		assertThat(result.getAiStatus()).isEqualTo(AiStatus.DONE);
		assertThat(result.getAiAnalysis()).contains("코드 전환 연습").contains("코드전환")
				.contains(String.valueOf(previous.getId()));
	}

	@Test
	void analyze_noHistory_leavesRelatedAndChangeEmpty() {
		Long hobbyId = createHobby(1L);
		ActivityRecord current = createPendingRecord(1L, hobbyId, LocalDate.of(2026, 10, 10), "첫 연습");

		stubAiResponse("{\"title\":\"첫 연습\",\"tags\":[\"시작\"],\"relatedRecordIds\":[999],"
				+ "\"change\":{\"text\":\"기록상 달라졌어요\",\"evidenceRecordId\":999}}");

		service(enabledProperties()).analyze(current.getId(), 1L);

		ActivityRecord result = activityRecordRepository.findById(current.getId()).orElseThrow();
		assertThat(result.getAiStatus()).isEqualTo(AiStatus.DONE);
		assertThat(result.getAiAnalysis()).contains("\"relatedRecordIds\":[]").contains("\"change\":null");
	}

	@Test
	void analyze_invalidJson_marksFailed() {
		Long hobbyId = createHobby(1L);
		ActivityRecord current = createPendingRecord(1L, hobbyId, LocalDate.now(), "연습");
		stubAiResponse("이것은 JSON이 아니다");

		service(enabledProperties()).analyze(current.getId(), 1L);

		ActivityRecord result = activityRecordRepository.findById(current.getId()).orElseThrow();
		assertThat(result.getAiStatus()).isEqualTo(AiStatus.FAILED);
	}

	@Test
	void analyze_promptFileMissing_marksFailedAndLogsFailWithoutCallingProvider() {
		Long hobbyId = createHobby(1L);
		ActivityRecord current = createPendingRecord(1L, hobbyId, LocalDate.now(), "연습");
		RecordAnalysisService service = service(enabledProperties());
		service.promptLoader = new SystemPromptLoader("does-not-exist.txt");

		service.analyze(current.getId(), 1L);

		ActivityRecord result = activityRecordRepository.findById(current.getId()).orElseThrow();
		assertThat(result.getAiStatus()).isEqualTo(AiStatus.FAILED);
		verifyNoInteractions(aiChatClient);
		assertThat(aiLogRepository.findAll()).anyMatch(
				log -> log.getResultType().equals(AiResultType.ANALYSIS.name())
						&& log.getState().equals(AiLogState.FAIL.name()));
	}

	@Test
	void analyze_evidenceIdNotInHistory_dropsChangeOnly() {
		Long hobbyId = createHobby(1L);
		ActivityRecord previous = createPendingRecord(1L, hobbyId, LocalDate.of(2026, 10, 1), "이전 연습");
		previous.markAnalysisDone(null);
		activityRecordRepository.save(previous);
		ActivityRecord current = createPendingRecord(1L, hobbyId, LocalDate.of(2026, 10, 10), "연습");

		stubAiResponse("{\"title\":\"연습 제목\",\"tags\":[\"연습\"],\"relatedRecordIds\":[],"
				+ "\"change\":{\"text\":\"기록상 달라졌어요\",\"evidenceRecordId\":99999}}");

		service(enabledProperties()).analyze(current.getId(), 1L);

		ActivityRecord result = activityRecordRepository.findById(current.getId()).orElseThrow();
		assertThat(result.getAiStatus()).isEqualTo(AiStatus.DONE);
		assertThat(result.getAiAnalysis()).contains("연습 제목").contains("\"change\":null");
	}

	@Test
	void analyze_forbiddenWordInTitle_dropsTitleOnly() {
		Long hobbyId = createHobby(1L);
		ActivityRecord current = createPendingRecord(1L, hobbyId, LocalDate.now(), "연습");
		stubAiResponse("{\"title\":\"실력이 늘었어요\",\"tags\":[\"연습\"],\"relatedRecordIds\":[],\"change\":null}");

		service(enabledProperties()).analyze(current.getId(), 1L);

		ActivityRecord result = activityRecordRepository.findById(current.getId()).orElseThrow();
		assertThat(result.getAiStatus()).isEqualTo(AiStatus.DONE);
		assertThat(result.getAiAnalysis()).contains("\"title\":null").contains("연습");
	}

	@Test
	void analyze_forbiddenWordInTag_dropsThatTagOnly() {
		Long hobbyId = createHobby(1L);
		ActivityRecord current = createPendingRecord(1L, hobbyId, LocalDate.now(), "연습");
		stubAiResponse("{\"title\":\"오늘의 연습\",\"tags\":[\"점수관리\",\"코드전환\"],\"relatedRecordIds\":[],\"change\":null}");

		service(enabledProperties()).analyze(current.getId(), 1L);

		ActivityRecord result = activityRecordRepository.findById(current.getId()).orElseThrow();
		assertThat(result.getAiAnalysis()).contains("코드전환").doesNotContain("점수관리");
	}

	@Test
	void analyze_analysisUserLimitExceeded_marksFailedAndLogsLimitWithoutCallingProvider() {
		Long hobbyId = createHobby(1L);
		ActivityRecord current = createPendingRecord(1L, hobbyId, LocalDate.now(), "연습");
		AiProperties properties = enabledProperties();
		properties.setAnalysisUserDailyLimit(0);

		service(properties).analyze(current.getId(), 1L);

		ActivityRecord result = activityRecordRepository.findById(current.getId()).orElseThrow();
		assertThat(result.getAiStatus()).isEqualTo(AiStatus.FAILED);
		verifyNoInteractions(aiChatClient);
		assertThat(aiLogRepository.findAll()).anyMatch(
				log -> log.getResultType().equals(AiResultType.ANALYSIS.name())
						&& log.getState().equals(AiLogState.LIMIT.name()));
	}

	@Test
	void analyze_recordAlreadyDone_skipsQuietlyWithoutCallingProvider() {
		Long hobbyId = createHobby(1L);
		ActivityRecord current = createPendingRecord(1L, hobbyId, LocalDate.now(), "연습");
		current.markAnalysisDone("{\"title\":\"기존 결과\"}");
		activityRecordRepository.save(current);

		service(enabledProperties()).analyze(current.getId(), 1L);

		ActivityRecord result = activityRecordRepository.findById(current.getId()).orElseThrow();
		assertThat(result.getAiAnalysis()).contains("기존 결과");
		verifyNoInteractions(aiChatClient);
	}

	@Test
	void analyze_recordDeleted_doesNotThrow() {
		service(enabledProperties()).analyze(99999L, 1L);
		verifyNoInteractions(aiChatClient);
	}

	@Test
	void analyze_separatesDataBetweenAccounts() {
		Long hobbyA = createHobby(1L);
		Long hobbyB = createHobby(2L);
		ActivityRecord recordA = createPendingRecord(1L, hobbyA, LocalDate.now(), "A의 연습");
		createPendingRecord(2L, hobbyB, LocalDate.now(), "B의 연습");

		// A의 recordId로 사용자 B를 지정하면 소유자가 달라 분석 대상이 아니다.
		service(enabledProperties()).analyze(recordA.getId(), 2L);

		ActivityRecord resultA = activityRecordRepository.findById(recordA.getId()).orElseThrow();
		assertThat(resultA.getAiStatus()).isEqualTo(AiStatus.PENDING);
		verifyNoInteractions(aiChatClient);
	}

	@Test
	void analyze_success_storesPromptVersionAndModel() {
		Long hobbyId = createHobby(1L);
		ActivityRecord current = createPendingRecord(1L, hobbyId, LocalDate.now(), "연습");
		stubAiResponse("{\"title\":\"연습\",\"tags\":[],\"relatedRecordIds\":[],\"change\":null}");

		service(enabledProperties()).analyze(current.getId(), 1L);

		ActivityRecord result = activityRecordRepository.findById(current.getId()).orElseThrow();
		assertThat(result.getAiAnalysis()).contains("\"promptVersion\":4").contains("\"model\":\"model\"");
	}

	@Test
	void readJson_missingPromptVersionAndModel_deserializesWithNulls() {
		RecordAnalysisService service = service(enabledProperties());
		StoredAnalysis stored = service.readJson("{\"title\":\"예전 결과\",\"tags\":[],\"relatedRecordIds\":[],"
				+ "\"change\":null,\"tagsFeedback\":null,\"changeFeedback\":null}");

		assertThat(stored.title()).isEqualTo("예전 결과");
		assertThat(stored.promptVersion()).isNull();
		assertThat(stored.model()).isNull();
	}

	@Test
	void markQueueRejected_pendingRecord_marksFailed() {
		Long hobbyId = createHobby(1L);
		ActivityRecord current = createPendingRecord(1L, hobbyId, LocalDate.now(), "연습");

		service(enabledProperties()).markQueueRejected(current.getId(), 1L);

		ActivityRecord result = activityRecordRepository.findById(current.getId()).orElseThrow();
		assertThat(result.getAiStatus()).isEqualTo(AiStatus.FAILED);
	}

	private static final String BASE = "{\"title\":\"연습\",\"tags\":[],\"relatedRecordIds\":[],\"change\":null,";

	/** %ID%는 분석할 기록의 id로 바뀐다(근거 id가 입력으로 준 기록이어야 해서). */
	private static String actionJson(String text, String minutes, String reason, String evidenceId) {
		return "{\"text\":\"" + text + "\",\"minutes\":" + minutes + ",\"reason\":\"" + reason
				+ "\",\"evidenceRecordId\":" + evidenceId + "}";
	}

	private static String goodAction() {
		return actionJson("코드 전환을 두 박자씩 나눠 천천히 쳐 봐요", "7", "코드 전환이 느렸다고 적었어요", "%ID%");
	}

	private ActivityRecord analyzeWithNextAction(String nextActionJson) {
		Long hobbyId = createHobby(1L);
		ActivityRecord current = createPendingRecord(1L, hobbyId, LocalDate.of(2026, 10, 10), "연습");
		stubAiResponse(BASE + "\"nextAction\":" + nextActionJson.replace("%ID%", String.valueOf(current.getId())) + "}");
		service(enabledProperties()).analyze(current.getId(), 1L);
		return activityRecordRepository.findById(current.getId()).orElseThrow();
	}

	@Test
	void analyze_nextAction_savedWithReasonEvidenceAndWithoutState() {
		ActivityRecord result = analyzeWithNextAction(goodAction());

		assertThat(result.getAiStatus()).isEqualTo(AiStatus.DONE);
		assertThat(result.getAiAnalysis()).contains("코드 전환을 두 박자씩 나눠 천천히 쳐 봐요").contains("\"minutes\":7")
				.contains("\"state\":null").contains("코드 전환이 느렸다고 적었어요")
				.contains("\"evidenceRecordId\":" + result.getId());
	}

	@Test
	void analyze_nextActionNull_keepsRestOfResult() {
		ActivityRecord result = analyzeWithNextAction("null");

		assertThat(result.getAiStatus()).isEqualTo(AiStatus.DONE);
		assertThat(result.getAiAnalysis()).contains("\"nextAction\":null").contains("\"title\":\"연습\"");
	}

	@Test
	void analyze_urgentOrForbiddenWordsInNextAction_dropsNextActionOnly() {
		for (String text : new String[] {"꼭 코드 전환을 쳐 봐요", "반드시 코드 전환을 쳐 봐요", "코드 전환을 지금 해야 해요",
				"코드 전환을 빨리 쳐 봐요", "코드 전환 실력이 늘었는지 봐요"}) {
			ActivityRecord result = analyzeWithNextAction(actionJson(text, "5", "코드 전환이 느렸어요", "%ID%"));

			assertThat(result.getAiStatus()).as(text).isEqualTo(AiStatus.DONE);
			assertThat(result.getAiAnalysis()).as(text).contains("\"nextAction\":null").contains("\"title\":\"연습\"");
		}
	}

	@Test
	void analyze_nextActionMinutesOutOfRangeOrTooLong_dropsNextActionOnly() {
		String longText = "코드 전환" + "가".repeat(50);
		for (String json : new String[] {actionJson("코드 전환을 쳐 봐요", "4", "코드 전환이 느렸어요", "%ID%"),
				actionJson("코드 전환을 쳐 봐요", "11", "코드 전환이 느렸어요", "%ID%"),
				actionJson("코드 전환을 쳐 봐요", "\"7\"", "코드 전환이 느렸어요", "%ID%"),
				actionJson("코드 전환을 쳐 봐요", "7.5", "코드 전환이 느렸어요", "%ID%"),
				actionJson(longText, "7", "코드 전환이 느렸어요", "%ID%"),
				actionJson(" ", "7", "코드 전환이 느렸어요", "%ID%")}) {
			ActivityRecord result = analyzeWithNextAction(json);

			assertThat(result.getAiStatus()).as(json).isEqualTo(AiStatus.DONE);
			assertThat(result.getAiAnalysis()).as(json).contains("\"nextAction\":null");
		}
	}

	@Test
	void analyze_nextActionWithoutReasonOrEvidenceOrWithLongReason_dropsNextActionOnly() {
		String noReason = "{\"text\":\"코드 전환을 천천히 쳐 봐요\",\"minutes\":7,\"evidenceRecordId\":%ID%}";
		String noEvidence = "{\"text\":\"코드 전환을 천천히 쳐 봐요\",\"minutes\":7,\"reason\":\"코드 전환이 느렸어요\"}";
		String longReason = actionJson("코드 전환을 천천히 쳐 봐요", "7", "코드 전환이 느렸어요" + "요".repeat(40), "%ID%");
		for (String json : new String[] {noReason, noEvidence, longReason}) {
			ActivityRecord result = analyzeWithNextAction(json);

			assertThat(result.getAiStatus()).as(json).isEqualTo(AiStatus.DONE);
			assertThat(result.getAiAnalysis()).as(json).contains("\"nextAction\":null");
		}
	}

	@Test
	void analyze_nextActionEvidenceNotInInput_dropsNextActionOnly() {
		ActivityRecord result = analyzeWithNextAction(actionJson("코드 전환을 천천히 쳐 봐요", "7", "코드 전환이 느렸어요", "99999"));

		assertThat(result.getAiStatus()).isEqualTo(AiStatus.DONE);
		assertThat(result.getAiAnalysis()).contains("\"nextAction\":null");
	}

	@Test
	void analyze_nextActionEvidenceIsOtherUsersRecord_dropsNextActionOnly() {
		Long otherHobby = createHobby(2L);
		ActivityRecord others = createPendingRecord(2L, otherHobby, LocalDate.of(2026, 10, 9), "남의 기록");
		ActivityRecord result = analyzeWithNextAction(
				actionJson("코드 전환을 천천히 쳐 봐요", "7", "코드 전환이 느렸어요", String.valueOf(others.getId())));

		assertThat(result.getAiAnalysis()).contains("\"nextAction\":null");
	}

	@Test
	void analyze_nextActionEvidenceFromHistory_isAccepted() {
		Long hobbyId = createHobby(1L);
		ActivityRecord previous = createPendingRecord(1L, hobbyId, LocalDate.of(2026, 10, 1), "이전 연습");
		ActivityRecord current = createPendingRecord(1L, hobbyId, LocalDate.of(2026, 10, 10), "연습");
		stubAiResponse(BASE + "\"nextAction\":"
				+ actionJson("코드 전환을 천천히 쳐 봐요", "7", "지난번에도 코드 전환이 느렸어요", String.valueOf(previous.getId())) + "}");

		service(enabledProperties()).analyze(current.getId(), 1L);

		ActivityRecord result = activityRecordRepository.findById(current.getId()).orElseThrow();
		assertThat(result.getAiAnalysis()).contains("\"evidenceRecordId\":" + previous.getId());
	}

	@Test
	void analyze_genericNextAction_isDroppedButConcreteOneIsKept() {
		for (String generic : new String[] {"코드 전환을 다시 연습해 봐요", "자신 있는 곳을 한 번 더 쳐 봐요", "기타를 꾸준히 쳐 봐요",
				"코드 전환을 복습해 봐요"}) {
			ActivityRecord result = analyzeWithNextAction(actionJson(generic, "7", "코드 전환이 느렸어요", "%ID%"));

			assertThat(result.getAiAnalysis()).as(generic).contains("\"nextAction\":null");
		}
		ActivityRecord concrete = analyzeWithNextAction(
				actionJson("C에서 G로 넘어가는 코드 전환만 박자 없이 열 번 바꿔 봐요", "7", "코드 전환이 느렸다고 적었어요", "%ID%"));
		assertThat(concrete.getAiAnalysis()).doesNotContain("\"nextAction\":null");
	}

	@Test
	void analyze_nextActionWithoutAnyInputWord_isDropped_andCanBeSwitchedOff() {
		String vague = actionJson("조용한 곳에서 물을 마시며 쉬어 봐요", "5", "코드 전환이 느렸어요", "%ID%");
		ActivityRecord dropped = analyzeWithNextAction(vague);
		assertThat(dropped.getAiAnalysis()).contains("\"nextAction\":null");

		Long hobbyId = createHobby(1L);
		ActivityRecord current = createPendingRecord(1L, hobbyId, LocalDate.of(2026, 10, 11), "연습");
		stubAiResponse(BASE + "\"nextAction\":" + vague.replace("%ID%", String.valueOf(current.getId())) + "}");
		RecordAnalysisService relaxed = service(enabledProperties());
		relaxed.requireInputWord = false;
		relaxed.analyze(current.getId(), 1L);
		assertThat(activityRecordRepository.findById(current.getId()).orElseThrow().getAiAnalysis())
				.doesNotContain("\"nextAction\":null");
	}

	private String capturedUserPrompt() {
		org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
		org.mockito.Mockito.verify(aiChatClient).complete(anyString(), captor.capture());
		return captor.getValue();
	}

	@Test
	void analyze_firstRecord_sendsFirstRecordContextWithoutElapsedDays() {
		Long hobbyId = createHobby(1L);
		ActivityRecord current = createPendingRecord(1L, hobbyId, LocalDate.of(2026, 10, 10), "첫 연습");
		stubAiResponse(BASE + "\"nextAction\":null}");

		service(enabledProperties()).analyze(current.getId(), 1L);

		assertThat(capturedUserPrompt()).contains("\"isFirstRecord\":true").contains("\"daysSincePreviousRecord\":null");
	}

	@Test
	void analyze_afterLongBreak_sendsElapsedDaysAcrossHobbies() {
		Long hobbyA = createHobby(1L);
		Long hobbyB = createHobby(1L);
		createPendingRecord(1L, hobbyA, LocalDate.of(2026, 10, 1), "이전 연습");
		ActivityRecord current = createPendingRecord(1L, hobbyB, LocalDate.of(2026, 10, 10), "다른 분야 연습");
		stubAiResponse(BASE + "\"nextAction\":null}");

		service(enabledProperties()).analyze(current.getId(), 1L);

		assertThat(capturedUserPrompt()).contains("\"isFirstRecord\":false").contains("\"daysSincePreviousRecord\":9");
	}

	@Test
	void analyze_elapsedDays_ignoresOtherUsersRecords() {
		Long hobbyId = createHobby(1L);
		Long otherHobby = createHobby(2L);
		createPendingRecord(2L, otherHobby, LocalDate.of(2026, 10, 9), "남의 기록");
		ActivityRecord current = createPendingRecord(1L, hobbyId, LocalDate.of(2026, 10, 10), "내 첫 기록");
		stubAiResponse(BASE + "\"nextAction\":null}");

		service(enabledProperties()).analyze(current.getId(), 1L);

		assertThat(capturedUserPrompt()).contains("\"isFirstRecord\":true");
	}

	@Test
	void readJson_oldResultWithoutNextAction_deserializesWithNull() {
		StoredAnalysis stored = service(enabledProperties()).readJson("{\"title\":\"예전\",\"tags\":[],"
				+ "\"relatedRecordIds\":[],\"change\":null,\"promptVersion\":2,\"model\":\"m\"}");

		assertThat(stored.nextAction()).isNull();
	}
}
