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
		ActivityRecord record = new ActivityRecord(userId, hobbyId, date, content, 20, "코드 전환이 느렸다", Feeling.NEUTRAL,
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
		assertThat(result.getAiAnalysis()).contains("\"promptVersion\":13").contains("\"model\":\"model\"");
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
		return analyzeWithNextAction(nextActionJson, false);
	}

	private ActivityRecord analyzeWithNextAction(String nextActionJson, boolean strict) {
		Long hobbyId = createHobby(1L);
		ActivityRecord current = createPendingRecord(1L, hobbyId, LocalDate.of(2026, 10, 10), "연습");
		stubAiResponse(BASE + "\"nextAction\":" + nextActionJson.replace("%ID%", String.valueOf(current.getId())) + "}");
		RecordAnalysisService service = service(enabledProperties());
		service.strictSpecificity = strict;
		service.analyze(current.getId(), 1L);
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
		String longText = "코드 전환" + "가".repeat(80);
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

	/** 하드 검사(필수 필드·길이)에 걸리는 응답은 nextAction만 버린다. 근거 id 누락은 하드가 아니다. */
	@Test
	void analyze_nextActionWithoutReasonOrWithLongReason_dropsNextActionOnly() {
		String noReason = "{\"text\":\"코드 전환을 천천히 쳐 봐요\",\"minutes\":7,\"evidenceRecordId\":%ID%}";
		String longReason = actionJson("코드 전환을 천천히 쳐 봐요", "7", "코드 전환이 느렸어요" + "요".repeat(40), "%ID%");
		for (String json : new String[] {noReason, longReason}) {
			ActivityRecord result = analyzeWithNextAction(json);

			assertThat(result.getAiStatus()).as(json).isEqualTo(AiStatus.DONE);
			assertThat(result.getAiAnalysis()).as(json).contains("\"nextAction\":null");
		}
	}

	@Test
	void analyze_nextActionEvidenceMissing_isReplacedByThisRecord() {
		String noEvidence = "{\"text\":\"코드 전환을 천천히 쳐 봐요\",\"minutes\":7,\"reason\":\"코드 전환이 느렸어요\"}";

		ActivityRecord result = analyzeWithNextAction(noEvidence);

		assertThat(result.getAiStatus()).isEqualTo(AiStatus.DONE);
		assertThat(result.getAiAnalysis()).contains("\"evidenceRecordId\":" + result.getId());
	}

	@Test
	void analyze_nextActionEvidenceNotInInput_isReplacedByThisRecord() {
		ActivityRecord result = analyzeWithNextAction(actionJson("코드 전환을 천천히 쳐 봐요", "7", "코드 전환이 느렸어요", "99999"));

		assertThat(result.getAiStatus()).isEqualTo(AiStatus.DONE);
		assertThat(result.getAiAnalysis()).contains("\"evidenceRecordId\":" + result.getId());
	}

	@Test
	void analyze_nextActionEvidenceIsOtherUsersRecord_dropsNextActionOnly() {
		Long otherHobby = createHobby(2L);
		ActivityRecord others = createPendingRecord(2L, otherHobby, LocalDate.of(2026, 10, 9), "남의 기록");
		ActivityRecord result = analyzeWithNextAction(
				actionJson("코드 전환을 천천히 쳐 봐요", "7", "코드 전환이 느렸어요", String.valueOf(others.getId())));

		assertThat(result.getAiStatus()).isEqualTo(AiStatus.DONE);
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

	/** 구체성 검사(일반 표현·입력 낱말)는 기본이 완화라 통과하고 로그만 남긴다. */
	@Test
	void analyze_genericOrVagueNextAction_passesInRelaxedModeWithSoftLog() {
		java.util.List<String> logs = captureLogs();
		String generic = actionJson("코드 전환을 다시 연습해 봐요", "7", "코드 전환이 느렸어요", "%ID%");
		String vague = actionJson("조용한 곳에서 물을 마시며 쉬어 봐요", "5", "코드 전환이 느렸어요", "%ID%");

		assertThat(analyzeWithNextAction(generic).getAiAnalysis()).doesNotContain("\"nextAction\":null");
		assertThat(analyzeWithNextAction(vague).getAiAnalysis()).doesNotContain("\"nextAction\":null");

		assertThat(logs).anyMatch(line -> line.contains("NEXT_ACTION_SOFT") && line.contains("reason=GENERIC"));
		assertThat(logs).anyMatch(line -> line.contains("NEXT_ACTION_SOFT") && line.contains("reason=NO_INPUT_WORD"));
	}

	@Test
	void analyze_genericOrVagueNextAction_isDroppedInStrictMode() {
		java.util.List<String> logs = captureLogs();
		String[] generics = {"코드 전환을 다시 연습해 봐요", "자신 있는 곳을 한 번 더 쳐 봐요", "기타를 꾸준히 쳐 봐요", "코드 전환을 복습해 봐요"};
		for (String generic : generics) {
			ActivityRecord result = analyzeWithNextAction(actionJson(generic, "7", "코드 전환이 느렸어요", "%ID%"), true);

			assertThat(result.getAiAnalysis()).as(generic).contains("\"nextAction\":null");
		}
		ActivityRecord vague = analyzeWithNextAction(actionJson("조용한 곳에서 물을 마시며 쉬어 봐요", "5", "코드 전환이 느렸어요", "%ID%"), true);
		assertThat(vague.getAiAnalysis()).contains("\"nextAction\":null");
		ActivityRecord concrete = analyzeWithNextAction(
				actionJson("C에서 G로 넘어가는 코드 전환만 박자 없이 열 번 바꿔 봐요", "7", "코드 전환이 느렸다고 적었어요", "%ID%"), true);
		assertThat(concrete.getAiAnalysis()).doesNotContain("\"nextAction\":null");

		assertThat(logs).anyMatch(line -> line.contains("NEXT_ACTION_DROPPED") && line.contains("reason=GENERIC"));
		assertThat(logs).anyMatch(line -> line.contains("NEXT_ACTION_DROPPED") && line.contains("reason=NO_INPUT_WORD"));
	}

	@Test
	void analyze_nextActionDropLogs_carryReasonAndRecordId() {
		java.util.List<String> logs = captureLogs();

		ActivityRecord missing = analyzeWithNextAction("null");
		analyzeWithNextAction(actionJson("코드 전환을 쳐 봐요", "11", "코드 전환이 느렸어요", "%ID%"));
		analyzeWithNextAction("\"문자열\"");
		analyzeWithNextAction("{\"text\":\"코드 전환을 쳐 봐요\"}");
		analyzeWithNextAction(actionJson("꼭 코드 전환을 쳐 봐요", "7", "코드 전환이 느렸어요", "%ID%"));
		analyzeWithNextAction(actionJson(" ", "7", "코드 전환이 느렸어요", "%ID%"));

		assertThat(logs).anyMatch(line -> line.contains("NEXT_ACTION_DROPPED") && line.contains("reason=MISSING")
				&& line.contains("recordId=" + missing.getId()));
		for (String reason : new String[] {"RANGE", "PARSE", "FIELD", "FORBIDDEN", "LENGTH"}) {
			assertThat(logs).as(reason).anyMatch(line -> line.contains("NEXT_ACTION_DROPPED") && line.contains("reason=" + reason));
		}
	}

	@Test
	void analyze_nextActionAbsentFromResponse_logsMissingAndHasNoCard() {
		java.util.List<String> logs = captureLogs();
		Long hobbyId = createHobby(1L);
		ActivityRecord current = createPendingRecord(1L, hobbyId, LocalDate.of(2026, 10, 10), "연습");
		stubAiResponse("{\"title\":\"연습\",\"tags\":[],\"relatedRecordIds\":[],\"change\":null}");

		service(enabledProperties()).analyze(current.getId(), 1L);

		ActivityRecord result = activityRecordRepository.findById(current.getId()).orElseThrow();
		assertThat(result.getAiStatus()).isEqualTo(AiStatus.DONE);
		assertThat(result.getAiAnalysis()).contains("\"nextAction\":null");
		assertThat(logs).anyMatch(line -> line.contains("NEXT_ACTION_DROPPED") && line.contains("reason=MISSING"));
	}

	@Test
	void analyze_sendsThisRecordIdToAi() {
		Long hobbyId = createHobby(1L);
		ActivityRecord current = createPendingRecord(1L, hobbyId, LocalDate.of(2026, 10, 10), "연습");
		stubAiResponse(BASE + "\"nextAction\":null}");

		service(enabledProperties()).analyze(current.getId(), 1L);

		assertThat(capturedUserPrompt()).contains("\"id\":" + current.getId());
	}

	private java.util.List<String> captureLogs() {
		ch.qos.logback.classic.Logger logger =
				(ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(RecordAnalysisService.class);
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

	/** 영어 문장·따옴표·물음표·알파벳이 든 기록에서도 nextAction이 버려지지 않는다(낱말 비교는 대소문자를 가리지 않는다). */
	@Test
	void analyze_englishContentWithQuotesAndQuestionMarks_keepsNextActionEvenInStrictMode() {
		Long hobbyId = hobbyRepository.save(new Hobby(1L, "영어 회화", "여행 영어 말하기")).getId();
		ActivityRecord current = createPendingRecord(1L, hobbyId, LocalDate.of(2026, 10, 10),
				"여행 영어 문장 읽기: \"Where is the Station?\" / It's \"too far\", right?");
		stubAiResponse(BASE + "\"nextAction\":" + actionJson("station 문장을 천천히 소리 내어 두 번 읽어 봐요", "6",
				"Where is the Station 문장을 읽었다고 적었어요", String.valueOf(current.getId())) + "}");
		RecordAnalysisService service = service(enabledProperties());
		service.strictSpecificity = true;

		service.analyze(current.getId(), 1L);

		ActivityRecord result = activityRecordRepository.findById(current.getId()).orElseThrow();
		assertThat(result.getAiStatus()).isEqualTo(AiStatus.DONE);
		assertThat(result.getAiAnalysis()).doesNotContain("\"nextAction\":null").contains("station 문장을");
	}

	@Test
	void analyze_englishTextWithQuotesInNextAction_isParsedAndKept() {
		Long hobbyId = createHobby(1L);
		ActivityRecord current = createPendingRecord(1L, hobbyId, LocalDate.of(2026, 10, 10), "연습");
		stubAiResponse(BASE + "\"nextAction\":{\"text\":\"\\\"Where is the station?\\\" 문장을 코드 전환처럼 천천히 읽어 봐요\","
				+ "\"minutes\":5,\"reason\":\"코드 전환이 느렸다고 적었어요\",\"evidenceRecordId\":" + current.getId() + "}}");

		service(enabledProperties()).analyze(current.getId(), 1L);

		ActivityRecord result = activityRecordRepository.findById(current.getId()).orElseThrow();
		assertThat(result.getAiAnalysis()).doesNotContain("\"nextAction\":null");
	}

	private static String changeJson(String changeText, String support, long evidence) {
		String textPart = changeText == null ? "\"text\":null," : "\"text\":\"" + changeText + "\",";
		String supportPart = support == null ? "" : "\"support\":\"" + support + "\",";
		return "{\"title\":\"연습\",\"tags\":[],\"relatedRecordIds\":[],\"change\":{" + textPart + supportPart
				+ "\"evidenceRecordId\":" + evidence + "},\"nextAction\":null}";
	}

	private ActivityRecord analyzeWithChange(String changeText, String support, boolean withPrevious) {
		Long hobbyId = createHobby(1L);
		Long evidenceId = null;
		if (withPrevious) {
			ActivityRecord previous = createPendingRecord(1L, hobbyId, LocalDate.of(2026, 10, 1), "이전 연습");
			evidenceId = previous.getId();
		}
		ActivityRecord current = createPendingRecord(1L, hobbyId, LocalDate.of(2026, 10, 10), "코드 전환 연습");
		long evidence = evidenceId != null ? evidenceId : current.getId();
		stubAiResponse(changeJson(changeText, support, evidence));
		service(enabledProperties()).analyze(current.getId(), 1L);
		return activityRecordRepository.findById(current.getId()).orElseThrow();
	}

	private StoredChange storedChangeOf(ActivityRecord record) {
		return service(enabledProperties()).readJson(record.getAiAnalysis()).change();
	}

	@Test
	void change_changeAndSupport_bothAccepted_withoutRetry() {
		java.util.List<String> logs = captureLogs();

		ActivityRecord result = analyzeWithChange("지난번엔 끊겼는데, 오늘은 열 번을 이어 쳤네요.",
				"열 번을 이어 쳤다니 멋져요.", true);

		StoredChange change = storedChangeOf(result);
		assertThat(change.text()).isEqualTo("지난번엔 끊겼는데, 오늘은 열 번을 이어 쳤네요.");
		assertThat(change.support()).isEqualTo("열 번을 이어 쳤다니 멋져요.");
		assertThat(logs).noneMatch(l -> l.contains("SUPPORT_RETRY") || l.contains("SUPPORT_FALLBACK"));
	}

	private RoadmapContextSource sourceWith(RoadmapContextSource.RoadmapContext context) {
		return new RoadmapContextSource() {
			@Override
			public RoadmapContext currentContext(Long userId, Long hobbyId) {
				return context;
			}

			@Override
			public RoadmapItemRef findPendingItem(Long userId, Long itemId) {
				return null;
			}
		};
	}

	private ActivityRecord analyzeWithRoadmap(RoadmapContextSource.RoadmapContext context, String suggestionJson) {
		Long hobbyId = createHobby(1L);
		ActivityRecord current = createPendingRecord(1L, hobbyId, LocalDate.of(2026, 10, 10), "Am 코드를 짚어 봤다");
		stubAiResponse("{\"title\":\"연습\",\"tags\":[],\"relatedRecordIds\":[],\"change\":null,"
				+ "\"nextAction\":null" + (suggestionJson == null ? "" : ",\"roadmapSuggestion\":" + suggestionJson) + "}");
		RecordAnalysisService service = service(enabledProperties());
		service.setRoadmapContextSource(sourceWith(context));
		service.analyze(current.getId(), 1L);
		return activityRecordRepository.findById(current.getId()).orElseThrow();
	}

	private static RoadmapContextSource.RoadmapContext twoItems() {
		return new RoadmapContextSource.RoadmapContext("Am 익히기", java.util.List.of(
				new RoadmapContextSource.RoadmapItemRef(11L, "Am 코드를 짚고 소리 확인하기", "Am 익히기"),
				new RoadmapContextSource.RoadmapItemRef(12L, "C에서 Am으로 열 번 바꿔 보기", "Am 익히기")));
	}

	@Test
	void roadmap_present_sendsCurrentStepAndPendingItems_andKeepsValidSuggestion() {
		ActivityRecord result = analyzeWithRoadmap(twoItems(), "{\"itemId\":11}");

		assertThat(capturedUserPrompt()).contains("\"roadmap\":{\"currentStep\":\"Am 익히기\"")
				.contains("\"id\":11").contains("\"id\":12");
		StoredAnalysis stored = service(enabledProperties()).readJson(result.getAiAnalysis());
		assertThat(stored.roadmapSuggestion().itemId()).isEqualTo(11L);
		assertThat(stored.roadmapSuggestion().answer()).isNull();
		// 항목 문장은 저장하지 않는다(보여 줄 때 로드맵에서 읽는다).
		assertThat(result.getAiAnalysis()).doesNotContain("Am 코드를 짚고 소리 확인하기");
	}

	@Test
	void roadmap_suggestionWithUnknownOrCheckedOrOtherUsersId_isDropped() {
		java.util.List<String> logs = captureLogs();

		// 입력으로 주지 않은 id(남의 항목·이미 체크한 항목·지어낸 id)는 모두 입력 목록에 없다.
		for (String suggestion : new String[] {"{\"itemId\":999}", "{\"itemId\":13}", "{\"itemId\":\"11\"}", "{}"}) {
			ActivityRecord result = analyzeWithRoadmap(twoItems(), suggestion);
			assertThat(service(enabledProperties()).readJson(result.getAiAnalysis()).roadmapSuggestion())
					.as(suggestion).isNull();
		}
		assertThat(logs).anyMatch(l -> l.contains("ROADMAP_SUGGESTION_DROPPED"));
	}

	@Test
	void roadmap_absent_keepsOldBehavior_andIgnoresSuggestion() {
		ActivityRecord result = analyzeWithRoadmap(null, "{\"itemId\":11}");

		assertThat(capturedUserPrompt()).doesNotContain("\"roadmap\"");
		assertThat(service(enabledProperties()).readJson(result.getAiAnalysis()).roadmapSuggestion()).isNull();
		assertThat(result.getAiStatus()).isEqualTo(AiStatus.DONE);
	}

	@Test
	void roadmap_suggestionNull_isFine() {
		ActivityRecord result = analyzeWithRoadmap(twoItems(), "null");

		assertThat(service(enabledProperties()).readJson(result.getAiAnalysis()).roadmapSuggestion()).isNull();
		assertThat(result.getAiStatus()).isEqualTo(AiStatus.DONE);
	}

	@Test
	void change_everyWarmPhrase_isAccepted() {
		for (String warm : RecordAnalysisService.WARM_PHRASES) {
			String support = "이어 쳤네요. " + warm;
			assertThat(storedChangeOf(analyzeWithChange("전환을 이어 쳤어요.", support, true)).support()).as(warm)
					.isEqualTo(support);
		}
	}

	@Test
	void change_nullTextWithWarmSupport_isStoredAsSupportOnly() {
		ActivityRecord result = analyzeWithChange(null, "잠들기 전에 20쪽이나 읽다니 좋은 시작이에요.", false);

		StoredChange change = storedChangeOf(result);
		assertThat(change.text()).isNull();
		assertThat(change.support()).isEqualTo("잠들기 전에 20쪽이나 읽다니 좋은 시작이에요.");
	}

	@Test
	void analyze_dailyLimitReached_marksFailedWithLimitReason_andOldFailedRowsHaveNone() {
		Long hobbyId = createHobby(1L);
		ActivityRecord current = createPendingRecord(1L, hobbyId, LocalDate.of(2026, 10, 10), "첫 연습");
		AiProperties properties = enabledProperties();
		properties.setAnalysisUserDailyLimit(0);
		RecordAnalysisService service = service(properties);

		service.analyze(current.getId(), 1L);

		ActivityRecord result = activityRecordRepository.findById(current.getId()).orElseThrow();
		assertThat(result.getAiStatus()).isEqualTo(AiStatus.FAILED);
		assertThat(service.failReasonOf(result.getAiAnalysis())).isEqualTo("LIMIT");
		// MySQL JSON 컬럼은 공백을 넣어 돌려주므로 그 모양도 읽혀야 하고, 예전 FAILED 행(null)은 이유가 없다.
		assertThat(service.failReasonOf("{\"failReason\": \"LIMIT\"}")).isEqualTo("LIMIT");
		assertThat(service.failReasonOf(null)).isNull();
		assertThat(service.failReasonOf("not json")).isNull();
	}

	@Test
	void analyze_providerDown_marksFailedWithProviderDownReason() {
		Long hobbyId = createHobby(1L);
		ActivityRecord current = createPendingRecord(1L, hobbyId, LocalDate.of(2026, 10, 10), "첫 연습");
		given(aiChatClient.complete(anyString(), anyString())).willThrow(new com.growit.ai.client.AiClientException(
				com.growit.ai.client.AiClientException.Type.PROVIDER_DOWN, "x", "http_401"));
		RecordAnalysisService service = service(enabledProperties());

		service.analyze(current.getId(), 1L);

		ActivityRecord result = activityRecordRepository.findById(current.getId()).orElseThrow();
		assertThat(result.getAiStatus()).isEqualTo(AiStatus.FAILED);
		assertThat(service.failReasonOf(result.getAiAnalysis())).isEqualTo("PROVIDER_DOWN");
	}

	@Test
	void analyze_otherFailure_hasNoLimitReason() {
		Long hobbyId = createHobby(1L);
		ActivityRecord current = createPendingRecord(1L, hobbyId, LocalDate.of(2026, 10, 10), "첫 연습");
		stubAiResponse("not json");
		RecordAnalysisService service = service(enabledProperties());

		service.analyze(current.getId(), 1L);

		ActivityRecord result = activityRecordRepository.findById(current.getId()).orElseThrow();
		assertThat(result.getAiStatus()).isEqualTo(AiStatus.FAILED);
		assertThat(service.failReasonOf(result.getAiAnalysis())).isNull();
	}

	@Test
	void change_supportRetry_isNotCountedTowardTheDailyLimit() {
		Long hobbyId = createHobby(1L);
		ActivityRecord previous = createPendingRecord(1L, hobbyId, LocalDate.of(2026, 10, 1), "이전 연습");
		ActivityRecord current = createPendingRecord(1L, hobbyId, LocalDate.of(2026, 10, 10), "코드 전환 연습");
		given(aiChatClient.complete(anyString(), anyString())).willReturn(
				new AiChatResponse(changeJson("전환을 이어 쳤어요.", "열 번 이어 쳤어요.", previous.getId()), 10, "model"),
				new AiChatResponse(changeJson("전환을 이어 쳤어요.", "열 번 이어 쳐서 멋져요.", previous.getId()), 10, "model"));
		AiProperties properties = enabledProperties();
		properties.setAnalysisUserDailyLimit(1);

		service(properties).analyze(current.getId(), 1L);

		// 한도가 1인데도 재호출까지 끝나 DONE이고, 한도에 세는 OK 로그는 1개뿐이다.
		assertThat(activityRecordRepository.findById(current.getId()).orElseThrow().getAiStatus()).isEqualTo(AiStatus.DONE);
		assertThat(aiLogRepository.findAll()).extracting(l -> l.getState()).containsExactlyInAnyOrder("OK", "RETRY");
	}

	@Test
	void change_supportWithoutWarmPhrase_retriesOnceAndAcceptsCorrectedAnswer() {
		Long hobbyId = createHobby(1L);
		ActivityRecord previous = createPendingRecord(1L, hobbyId, LocalDate.of(2026, 10, 1), "이전 연습");
		ActivityRecord current = createPendingRecord(1L, hobbyId, LocalDate.of(2026, 10, 10), "코드 전환 연습");
		given(aiChatClient.complete(anyString(), anyString())).willReturn(
				new AiChatResponse(changeJson("전환을 이어 쳤어요.", "열 번 이어 쳤어요.", previous.getId()), 10, "model"),
				new AiChatResponse(changeJson("전환을 이어 쳤어요.", "열 번 이어 쳐서 멋져요.", previous.getId()), 10, "model"));
		java.util.List<String> logs = captureLogs();

		service(enabledProperties()).analyze(current.getId(), 1L);

		StoredChange change = storedChangeOf(activityRecordRepository.findById(current.getId()).orElseThrow());
		assertThat(change.support()).isEqualTo("열 번 이어 쳐서 멋져요.");
		assertThat(logs).anyMatch(l -> l.contains("SUPPORT_RETRY"));
		assertThat(logs).noneMatch(l -> l.contains("SUPPORT_FALLBACK"));
		org.mockito.ArgumentCaptor<String> system = org.mockito.ArgumentCaptor.forClass(String.class);
		org.mockito.Mockito.verify(aiChatClient, org.mockito.Mockito.times(2)).complete(system.capture(), anyString());
		assertThat(system.getAllValues().get(1)).contains("[교정 지시]").contains("따뜻한 직접 표현");
		assertThat(system.getAllValues().get(0)).doesNotContain("[교정 지시]");
	}

	@Test
	void change_retryAlsoFails_usesDirectionBasedFallback_andKeepsOtherFields() {
		java.util.List<String> logs = captureLogs();

		ActivityRecord result = analyzeWithChange("전환을 열 번 이어 쳤어요.", "정말 실력이 늘었네요", true);

		StoredChange change = storedChangeOf(result);
		assertThat(change.text()).isEqualTo("전환을 열 번 이어 쳤어요.");
		// 이 기록에는 어려움("코드 전환이 느렸다")이 적혀 있어 힘든 쪽 문장을 쓴다.
		assertThat(change.support()).isEqualTo(RecordAnalysisService.FALLBACK_SUPPORT_HARD);
		assertThat(result.getAiAnalysis()).contains("\"title\":\"연습\"");
		assertThat(logs).anyMatch(l -> l.contains("CHANGE_DROPPED") && l.contains("field=support")
				&& l.contains("reason=FORBIDDEN"));
		assertThat(logs).anyMatch(l -> l.contains("SUPPORT_RETRY"));
		assertThat(logs).anyMatch(l -> l.contains("SUPPORT_FALLBACK"));
	}

	@Test
	void change_fallbackSupport_followsFeelingDirection() {
		String[][] cases = {{"PROUD", RecordAnalysisService.FALLBACK_SUPPORT_GOOD},
				{"NEUTRAL", RecordAnalysisService.FALLBACK_SUPPORT_NORMAL},
				{"STUCK", RecordAnalysisService.FALLBACK_SUPPORT_HARD}};
		for (String[] c : cases) {
			Long hobbyId = createHobby(1L);
			ActivityRecord current = new ActivityRecord(1L, hobbyId, LocalDate.of(2026, 10, 10), "연습", 20, null,
					Feeling.valueOf(c[0]), false);
			current.markAnalysisPending(LocalDateTime.now());
			current = activityRecordRepository.save(current);
			stubAiResponse(changeJson(null, "그냥 했어요.", current.getId()));

			service(enabledProperties()).analyze(current.getId(), 1L);

			StoredChange change = storedChangeOf(activityRecordRepository.findById(current.getId()).orElseThrow());
			assertThat(change.support()).as(c[0]).isEqualTo(c[1]);
		}
	}

	@Test
	void change_reportStylePhrases_areRejectedInBothFields() {
		java.util.List<String> logs = captureLogs();

		StoredChange change = storedChangeOf(
				analyzeWithChange("열 번 이어 친 점을 알 수 있어요.", "이어 간 과정이 보여요. 멋져요", true));

		assertThat(change.text()).isNull();
		assertThat(change.support()).isEqualTo(RecordAnalysisService.FALLBACK_SUPPORT_HARD);
		assertThat(logs).anyMatch(l -> l.contains("field=change") && l.contains("reason=STYLE"));
		assertThat(logs).anyMatch(l -> l.contains("field=support") && l.contains("reason=STYLE"));
	}

	@Test
	void change_everyReportStylePhrase_isRejected() {
		for (String phrase : RecordAnalysisService.REPORT_STYLE_PHRASES) {
			StoredChange change = storedChangeOf(analyzeWithChange("전환을 이어 쳤어요. " + phrase, "열 번 쳐서 멋져요", true));
			assertThat(change.text()).as(phrase).isNull();
		}
	}

	@Test
	void change_bannedWords_neverReachScreen() {
		for (String word : new String[] {"실력", "늘었", "향상", "점수", "순위", "완벽", "최고", "천재", "틀림없", "될 거예요"}) {
			ActivityRecord result = analyzeWithChange("전환을 이어 쳤어요. " + word, "정말 " + word + " 해요 멋져요", true);

			assertThat(result.getAiStatus()).as(word).isEqualTo(AiStatus.DONE);
			StoredChange change = storedChangeOf(result);
			assertThat(change.text()).as(word).isNull();
			assertThat(change.support()).as(word).isEqualTo(RecordAnalysisService.FALLBACK_SUPPORT_HARD);
		}
	}

	@Test
	void change_lengthLimits_areNinetyAndEighty() {
		String ninety = "가".repeat(90);
		String eighty = "나".repeat(76) + " 멋져요";

		StoredChange ok = storedChangeOf(analyzeWithChange(ninety, eighty, true));
		assertThat(eighty).hasSize(80);
		assertThat(ok.text()).isEqualTo(ninety);
		assertThat(ok.support()).isEqualTo(eighty);

		java.util.List<String> logs = captureLogs();
		StoredChange over = storedChangeOf(analyzeWithChange("가".repeat(91), "나".repeat(77) + " 멋져요", true));
		assertThat(over.text()).isNull();
		assertThat(over.support()).isEqualTo(RecordAnalysisService.FALLBACK_SUPPORT_HARD);
		assertThat(logs).anyMatch(l -> l.contains("field=change") && l.contains("reason=LENGTH") && l.contains("length=91"));
		assertThat(logs).anyMatch(l -> l.contains("field=support") && l.contains("reason=LENGTH") && l.contains("length=81"));
	}

	@Test
	void change_supportMissing_retriesThenFallsBack() {
		java.util.List<String> logs = captureLogs();

		StoredChange change = storedChangeOf(analyzeWithChange("전환을 이어 쳤어요.", null, true));

		assertThat(change.text()).isEqualTo("전환을 이어 쳤어요.");
		assertThat(change.support()).isEqualTo(RecordAnalysisService.FALLBACK_SUPPORT_HARD);
		assertThat(logs).anyMatch(l -> l.contains("SUPPORT_MISSING"));
		assertThat(logs).anyMatch(l -> l.contains("SUPPORT_RETRY"));
	}

	@Test
	void change_withoutPreviousRecord_canUseThisRecordAsEvidence() {
		ActivityRecord result = analyzeWithChange("코드 전환을 처음 기록으로 남겼어요.", "시작한 것만으로 충분해요.", false);

		assertThat(result.getAiAnalysis()).contains("코드 전환을 처음 기록으로 남겼어요.")
				.contains("\"evidenceRecordId\":" + result.getId());
	}

	@Test
	void change_promptVersionIsThirteenAndOldSingleSentenceResultsStillRead() {
		ActivityRecord result = analyzeWithChange("전환을 이어 쳤어요.", "이어 간 게 멋져요.", true);
		assertThat(result.getAiAnalysis()).contains("\"promptVersion\":13");

		StoredAnalysis old = service(enabledProperties()).readJson("{\"title\":\"예전\",\"tags\":[],"
				+ "\"relatedRecordIds\":[],\"change\":{\"text\":\"지난번엔 끊겼는데 오늘은 이어 쳤어요\",\"evidenceRecordId\":1},"
				+ "\"promptVersion\":2,\"model\":\"m\"}");
		assertThat(old.change().text()).isEqualTo("지난번엔 끊겼는데 오늘은 이어 쳤어요");
		assertThat(old.change().support()).isNull();
	}

	@Test
	void analyze_sendsFeelingDirectionToAi() {
		Long hobbyId = createHobby(1L);
		ActivityRecord record = new ActivityRecord(1L, hobbyId, LocalDate.of(2026, 10, 10), "연습", 20, "손가락이 아팠다",
				Feeling.STUCK, false);
		record.markAnalysisPending(LocalDateTime.now());
		record = activityRecordRepository.save(record);
		stubAiResponse(BASE + "\"nextAction\":null}");

		service(enabledProperties()).analyze(record.getId(), 1L);

		assertThat(capturedUserPrompt()).contains("\"feelingDirection\":\"HARD\"");
	}

	@Test
	void nextActionText_longEnglishQuoteAroundSixtyChars_isNowAccepted() {
		String text = "\\\"Excuse me, where is the nearest subway station?\\\" 문장을 코드 전환처럼 천천히 읽어 봐요";
		assertThat(text.length()).isBetween(55, 80);

		ActivityRecord result = analyzeWithNextAction(actionJson(text, "6", "코드 전환이 느렸다고 적었어요", "%ID%"));

		assertThat(result.getAiAnalysis()).doesNotContain("\"nextAction\":null");
	}

	@Test
	void nextActionText_overEightyChars_isDroppedAsLengthWithFieldAndLengthInLog() {
		java.util.List<String> logs = captureLogs();
		String text = "코드 전환" + "가".repeat(76);

		ActivityRecord result = analyzeWithNextAction(actionJson(text, "6", "코드 전환이 느렸어요", "%ID%"));

		assertThat(result.getAiAnalysis()).contains("\"nextAction\":null");
		assertThat(logs).anyMatch(line -> line.contains("reason=LENGTH") && line.contains("detail=text=81/80"));
	}

	@Test
	void nextActionText_exactlyEightyChars_isAccepted_andLongReasonLogsReasonField() {
		java.util.List<String> logs = captureLogs();
		String eighty = "코드 전환" + "가".repeat(75);
		assertThat(eighty.length()).isEqualTo(80);

		assertThat(analyzeWithNextAction(actionJson(eighty, "6", "코드 전환이 느렸어요", "%ID%")).getAiAnalysis())
				.doesNotContain("\"nextAction\":null");
		analyzeWithNextAction(actionJson("코드 전환을 쳐 봐요", "6", "코드 전환이 느렸어요" + "요".repeat(40), "%ID%"));

		assertThat(logs).anyMatch(line -> line.contains("reason=LENGTH") && line.contains("detail=reason="));
	}

	// ---- T7: 칭찬 강도(강함/부드러움), 강조어 금지, 과한 칭찬 재호출 ----

	/** 어려움이 없고 감정이 개운한 평범한 기록(이전 기록 없음)을 만들고, 주어진 응답을 차례로 돌려주게 한 뒤 분석한다. */
	private ActivityRecord analyzeOrdinary(String... supports) {
		Long hobbyId = createHobby(1L);
		ActivityRecord current = new ActivityRecord(1L, hobbyId, LocalDate.of(2026, 10, 10),
				"아침에 20분 가볍게 뛰고 5분 걸었다. 숨이 편했고 어제보다 다리가 가벼웠다", 25, null, Feeling.RELIEVED, false);
		current.markAnalysisPending(LocalDateTime.now());
		current = activityRecordRepository.save(current);
		AiChatResponse[] responses = new AiChatResponse[supports.length];
		for (int i = 0; i < supports.length; i++) {
			responses[i] = new AiChatResponse(changeJson(null, supports[i], current.getId()), 10, "model");
		}
		org.mockito.BDDMockito.BDDMyOngoingStubbing<AiChatResponse> stub = given(aiChatClient.complete(anyString(), anyString()))
				.willReturn(responses[0]);
		for (int i = 1; i < responses.length; i++) {
			stub = stub.willReturn(responses[i]);
		}
		service(enabledProperties()).analyze(current.getId(), 1L);
		return activityRecordRepository.findById(current.getId()).orElseThrow();
	}

	@Test
	void t7_ordinaryRecordWithStrongPraise_retriesWithCorrection_andAcceptsSoftAnswer() {
		java.util.List<String> logs = captureLogs();

		ActivityRecord result = analyzeOrdinary("다리가 어제보다 가벼웠다니 멋져요.", "다리가 어제보다 가벼웠다니 다행이에요.");

		assertThat(storedChangeOf(result).support()).isEqualTo("다리가 어제보다 가벼웠다니 다행이에요.");
		assertThat(logs).anyMatch(l -> l.contains("SUPPORT_RETRY"));
		assertThat(logs).noneMatch(l -> l.contains("SUPPORT_OVERPRAISE") || l.contains("SUPPORT_FALLBACK"));
		org.mockito.ArgumentCaptor<String> system = org.mockito.ArgumentCaptor.forClass(String.class);
		org.mockito.Mockito.verify(aiChatClient, org.mockito.Mockito.times(2)).complete(system.capture(), anyString());
		assertThat(system.getAllValues().get(1)).contains("[교정 지시]", RecordAnalysisService.OVERPRAISE_CORRECTION);
	}

	@Test
	void t7_ordinaryRecordStillStrongAfterRetry_isKeptWithOverpraiseWarning_notFallback() {
		java.util.List<String> logs = captureLogs();

		ActivityRecord result = analyzeOrdinary("다리가 어제보다 가벼웠다니 멋져요.", "다리가 가벼워서 대단해요.");

		// 재호출도 강한 칭찬이면 문장은 규칙을 지켰으므로 그대로 두고, 경고 로그만 남긴다(기본 문장으로 바꾸지 않는다).
		assertThat(storedChangeOf(result).support()).isEqualTo("다리가 가벼워서 대단해요.");
		assertThat(logs).anyMatch(l -> l.startsWith("SUPPORT_OVERPRAISE recordId="));
		assertThat(logs).noneMatch(l -> l.contains("SUPPORT_FALLBACK"));
		assertThat(logs).noneMatch(l -> l.contains("다리가"));
		org.mockito.Mockito.verify(aiChatClient, org.mockito.Mockito.times(2)).complete(anyString(), anyString());
	}

	@Test
	void t7_recordWithDifficulty_strongPraiseIsAccepted_withoutRetry() {
		java.util.List<String> logs = captureLogs();

		// 기본 테스트 기록에는 어려움("코드 전환이 느렸다")이 적혀 있어 강한 칭찬이 어울린다.
		ActivityRecord result = analyzeWithChange(null, "느렸던 전환을 이어 쳤네요. 대단해요.", false);

		assertThat(storedChangeOf(result).support()).isEqualTo("느렸던 전환을 이어 쳤네요. 대단해요.");
		assertThat(logs).noneMatch(l -> l.contains("SUPPORT_RETRY") || l.contains("SUPPORT_OVERPRAISE"));
		org.mockito.Mockito.verify(aiChatClient, org.mockito.Mockito.times(1)).complete(anyString(), anyString());
	}

	@Test
	void t7_hardFeelingWithoutDifficultyText_alsoAllowsStrongPraise() {
		Long hobbyId = createHobby(1L);
		ActivityRecord current = new ActivityRecord(1L, hobbyId, LocalDate.of(2026, 10, 10), "스트레칭만 했다", 5, null,
				Feeling.STUCK, false);
		current.markAnalysisPending(LocalDateTime.now());
		current = activityRecordRepository.save(current);
		stubAiResponse(changeJson(null, "몸이 무거운 날에도 스트레칭을 했네요. 잘했어요.", current.getId()));

		service(enabledProperties()).analyze(current.getId(), 1L);

		assertThat(storedChangeOf(activityRecordRepository.findById(current.getId()).orElseThrow()).support())
				.isEqualTo("몸이 무거운 날에도 스트레칭을 했네요. 잘했어요.");
		org.mockito.Mockito.verify(aiChatClient, org.mockito.Mockito.times(1)).complete(anyString(), anyString());
	}

	@Test
	void t7_emphasisWordsInSupport_areRejected_thenFallback() {
		for (String word : RecordAnalysisService.EMPHASIS_WORDS) {
			java.util.List<String> logs = captureLogs();

			ActivityRecord result = analyzeWithChange(null, "열 번을 쳤네요. " + word + " 괜찮아요.", false);

			assertThat(storedChangeOf(result).support()).as(word).isEqualTo(RecordAnalysisService.FALLBACK_SUPPORT_HARD);
			assertThat(logs).as(word).anyMatch(l -> l.contains("field=support") && l.contains("reason=EMPHASIS"));
			assertThat(logs).as(word).anyMatch(l -> l.contains("SUPPORT_RETRY"));
		}
	}

	@Test
	void t7_softOnlySupport_satisfiesTheWarmRequirement() {
		for (String soft : RecordAnalysisService.WARM_SOFT) {
			String support = "오늘도 기록을 남겼네요. " + soft;
			ActivityRecord result = analyzeOrdinary(changeSupportCheck(support));
			assertThat(storedChangeOf(result).support()).as(soft).isEqualTo(support);
		}
		// "좋아 보여요" 안의 "보여요"는 관찰체로 거절하지 않는다.
		assertThat(storedChangeOf(analyzeOrdinary("다리가 가벼워 좋아 보여요.")).support()).isEqualTo("다리가 가벼워 좋아 보여요.");
	}

	private static String changeSupportCheck(String support) {
		return support;
	}

	@Test
	void t7_warmLists_areSplitAndUnioned() {
		assertThat(RecordAnalysisService.WARM_STRONG).containsExactly("멋져요", "대단해요", "잘하셨어요", "잘했어요", "해냈", "자랑스러워", "장해요");
		assertThat(RecordAnalysisService.WARM_SOFT).contains("수고했어요", "고생했어요", "애썼어요", "괜찮아요", "충분해요", "응원해요",
				"다행이에요", "좋았겠어요", "좋아 보여요", "편안했겠어요", "반가워요", "좋은 시작", "좋은 흐름", "기분 좋은", "잘 이어 가고 있어요");
		assertThat(RecordAnalysisService.WARM_PHRASES).containsAll(RecordAnalysisService.WARM_STRONG)
				.containsAll(RecordAnalysisService.WARM_SOFT).hasSize(RecordAnalysisService.WARM_STRONG.size()
						+ RecordAnalysisService.WARM_SOFT.size());
		assertThat(RecordAnalysisService.EMPHASIS_WORDS).containsExactly("정말", "너무", "진짜", "엄청", "굉장히");
	}

	@Test
	void t7_fallbackSentences_areGentle_andContainAWarmPhrase() {
		assertThat(RecordAnalysisService.FALLBACK_SUPPORT_GOOD).isEqualTo("오늘도 기록을 이어 갔네요. 좋은 흐름이에요.");
		assertThat(RecordAnalysisService.FALLBACK_SUPPORT_NORMAL).isEqualTo("오늘도 한 줄 남겼네요. 이어 가는 것만으로 충분해요.");
		assertThat(RecordAnalysisService.FALLBACK_SUPPORT_HARD).isEqualTo("힘든 날에도 기록을 남겼네요. 오늘은 이걸로 충분해요. 고생했어요.");
		for (String fallback : java.util.List.of(RecordAnalysisService.FALLBACK_SUPPORT_GOOD,
				RecordAnalysisService.FALLBACK_SUPPORT_NORMAL, RecordAnalysisService.FALLBACK_SUPPORT_HARD)) {
			assertThat(RecordAnalysisService.WARM_PHRASES).anyMatch(fallback::contains);
			assertThat(RecordAnalysisService.EMPHASIS_WORDS).noneMatch(fallback::contains);
			assertThat(RecordAnalysisService.WARM_STRONG).noneMatch(fallback::contains);
			assertThat(fallback.length()).isLessThanOrEqualTo(80);
		}
	}

	// ---- T8: 성취형 판정·직접 인정 필수·기록에 없는 말 금지 ----

	private ActivityRecord pendingRecord(String content, String difficulty, Feeling feeling) {
		Long hobbyId = createHobby(1L);
		ActivityRecord record = new ActivityRecord(1L, hobbyId, LocalDate.of(2026, 10, 10), content, 15, difficulty,
				feeling, false);
		record.markAnalysisPending(LocalDateTime.now());
		return activityRecordRepository.save(record);
	}

	/** 주어진 change JSON들을 차례로 돌려주게 하고 분석한다(마지막 응답은 반복). */
	private ActivityRecord analyzeRecord(ActivityRecord current, String... changeJsons) {
		AiChatResponse[] responses = new AiChatResponse[changeJsons.length];
		for (int i = 0; i < changeJsons.length; i++) {
			responses[i] = new AiChatResponse(changeJsons[i], 10, "model");
		}
		org.mockito.BDDMockito.BDDMyOngoingStubbing<AiChatResponse> stub = given(aiChatClient.complete(anyString(), anyString()))
				.willReturn(responses[0]);
		for (int i = 1; i < responses.length; i++) {
			stub = stub.willReturn(responses[i]);
		}
		service(enabledProperties()).analyze(current.getId(), 1L);
		return activityRecordRepository.findById(current.getId()).orElseThrow();
	}

	private ActivityRecord achievementRecord() {
		return pendingRecord("어제 막혔던 F 코드를 오늘은 10번 중 8번 깨끗하게 잡았다", "손가락이 아프다", Feeling.PROUD);
	}

	@Test
	void t8_achievementJudgement_difficultyWithGoodFeeling_orOvercomePhrase_butNeverWhenHard() {
		// (막힌 점이 있고 감정이 좋음)
		assertThat(RecordAnalysisService.isAchievement("F 코드를 쳤다", "손가락이 아프다", "GOOD")).isTrue();
		// 막힌 점만 있고 감정이 보통이면 성취형이 아니다.
		assertThat(RecordAnalysisService.isAchievement("F 코드를 쳤다", "손가락이 아프다", "NEUTRAL")).isFalse();
		assertThat(RecordAnalysisService.isAchievement("F 코드를 쳤다", null, "GOOD")).isFalse();
		assertThat(RecordAnalysisService.isAchievement("F 코드를 쳤다", "  ", "GOOD")).isFalse();
		// 본문의 극복 표현(감정 방향과 상관없이 힘듦만 아니면)
		for (String phrase : RecordAnalysisService.OVERCOME_PHRASES) {
			assertThat(RecordAnalysisService.isAchievement("오늘은 " + phrase + " F 코드를 잡았다", null, "NEUTRAL")).as(phrase).isTrue();
			// 감정 방향이 힘듦이면 극복 표현이 있어도, 막힌 점이 있어도 위로형을 유지한다.
			assertThat(RecordAnalysisService.isAchievement("오늘은 " + phrase + " F 코드를 잡았다", "손가락이 아프다", "HARD")).as(phrase)
					.isFalse();
		}
		assertThat(RecordAnalysisService.OVERCOME_PHRASES)
				.containsExactly("막혔던", "안 되던", "못 하던", "어렵던", "드디어", "처음으로", "마침내", "결국");
	}

	@Test
	void t8_achievementFlagIsSentToAi() {
		ActivityRecord record = achievementRecord();
		stubAiResponse(changeJson(null, "10번 중 8번 잡았네요. 해냈네요.", record.getId()));
		service(enabledProperties()).analyze(record.getId(), 1L);
		assertThat(capturedUserPrompt()).contains("\"achievement\":true");
org.mockito.Mockito.clearInvocations(aiChatClient);

		ActivityRecord plain = pendingRecord("아침에 가볍게 뛰었다", null, Feeling.RELIEVED);
		stubAiResponse(changeJson(null, "다리가 가벼웠다니 다행이에요.", plain.getId()));
		service(enabledProperties()).analyze(plain.getId(), 1L);
		assertThat(capturedUserPrompt()).contains("\"achievement\":false");
	}

	@Test
	void t8_achievementSupportWithoutRecognition_retriesWithCorrection_andAcceptsRecognizedAnswer() {
		java.util.List<String> logs = captureLogs();
		ActivityRecord record = achievementRecord();

		ActivityRecord result = analyzeRecord(record,
				changeJson(null, "손가락이 아팠는데도 15분 연습했네요. 뿌듯한 마음이 좋았겠어요.", record.getId()),
				changeJson(null, "막혔던 F 코드를 10번 중 8번 잡았네요. 해냈네요.", record.getId()));

		assertThat(storedChangeOf(result).support()).isEqualTo("막혔던 F 코드를 10번 중 8번 잡았네요. 해냈네요.");
		assertThat(logs).anyMatch(l -> l.contains("SUPPORT_RETRY") && l.contains("achievement=true"));
		assertThat(logs).anyMatch(l -> l.contains("field=support") && l.contains("reason=NO_RECOGNITION"));
		assertThat(logs).noneMatch(l -> l.contains("SUPPORT_FALLBACK"));
		org.mockito.ArgumentCaptor<String> system = org.mockito.ArgumentCaptor.forClass(String.class);
		org.mockito.Mockito.verify(aiChatClient, org.mockito.Mockito.times(2)).complete(system.capture(), anyString());
		assertThat(system.getAllValues().get(1)).contains("[교정 지시]", RecordAnalysisService.ACHIEVEMENT_CORRECTION);
	}

	@Test
	void t8_achievementRetryAlsoLacksRecognition_usesAchievementFallback() {
		java.util.List<String> logs = captureLogs();
		ActivityRecord record = achievementRecord();

		ActivityRecord result = analyzeRecord(record,
				changeJson(null, "손가락이 아팠는데도 15분 연습했네요. 뿌듯한 마음이 좋았겠어요.", record.getId()));

		assertThat(storedChangeOf(result).support()).isEqualTo(RecordAnalysisService.FALLBACK_SUPPORT_ACHIEVEMENT)
				.isEqualTo("어려웠던 걸 해냈네요. 대단해요.");
		assertThat(logs).anyMatch(l -> l.contains("SUPPORT_FALLBACK") && l.contains("achievement=true"));
	}

	@Test
	void t8_everyStrongWord_countsAsRecognition() {
		for (String strong : RecordAnalysisService.WARM_STRONG) {
			ActivityRecord record = achievementRecord();
			String support = "10번 중 8번 잡았네요. " + strong;
			assertThat(storedChangeOf(analyzeRecord(record, changeJson(null, support, record.getId()))).support()).as(strong)
					.isEqualTo(support);
		}
	}

	@Test
	void t8_ungroundedWord_isViolation_unlessTheRecordHasIt() {
		java.util.List<String> logs = captureLogs();
		ActivityRecord record = achievementRecord();

		// "집중해서"는 이번 기록 본문·막힌 점에 없다 → 같은 재호출 경로로 한 번 더 부르고, 고친 답을 쓴다.
		ActivityRecord result = analyzeRecord(record,
				changeJson(null, "15분 동안 집중해서 연습했네요. 대단해요.", record.getId()),
				changeJson(null, "10번 중 8번 잡았네요. 대단해요.", record.getId()));
		assertThat(storedChangeOf(result).support()).isEqualTo("10번 중 8번 잡았네요. 대단해요.");
		assertThat(logs).anyMatch(l -> l.contains("field=support") && l.contains("reason=UNGROUNDED"));
		assertThat(logs).anyMatch(l -> l.contains("SUPPORT_RETRY"));

		// 기록에 "집중"이 적혀 있으면 그대로 쓸 수 있다.
		ActivityRecord focused = pendingRecord("오늘은 집중해서 F 코드를 연습했다. 드디어 깨끗하게 잡았다", null, Feeling.NEUTRAL);
		org.mockito.Mockito.clearInvocations(aiChatClient);
		ActivityRecord ok = analyzeRecord(focused, changeJson(null, "집중해서 연습하더니 드디어 잡았네요. 해냈네요.", focused.getId()));
		assertThat(storedChangeOf(ok).support()).isEqualTo("집중해서 연습하더니 드디어 잡았네요. 해냈네요.");
		org.mockito.Mockito.verify(aiChatClient, org.mockito.Mockito.times(1)).complete(anyString(), anyString());
	}

	@Test
	void t8_ungroundedStems_areTheFiveWords_andPreviousRecordTextDoesNotCount() {
		assertThat(RecordAnalysisService.UNGROUNDED_STEMS).containsExactly("집중", "열심히", "꾸준", "최선", "끝까지");
		java.util.List<String> logs = captureLogs();
		Long hobbyId = createHobby(1L);
		// 이전 기록에는 "끝까지"가 있지만 이번 기록에는 없다 → 위반.
		ActivityRecord previous = new ActivityRecord(1L, hobbyId, LocalDate.of(2026, 10, 1), "끝까지 쳤다", 15, null,
				Feeling.NEUTRAL, false);
		previous.markAnalysisPending(LocalDateTime.now());
		previous = activityRecordRepository.save(previous);
		ActivityRecord current = new ActivityRecord(1L, hobbyId, LocalDate.of(2026, 10, 10), "코드를 쳤다", 15, null,
				Feeling.NEUTRAL, false);
		current.markAnalysisPending(LocalDateTime.now());
		current = activityRecordRepository.save(current);

		ActivityRecord result = analyzeRecord(current,
				changeJson("지난번엔 끝까지 쳤는데, 오늘도 이어 쳤네요.", "이어 쳤네요. 괜찮아요.", previous.getId()));

		// change 문장만 버리고(재호출 한 번 뒤에도 같으면) support는 그대로 둔다. 기본 문장으로 바꾸지 않는다.
		StoredChange change = storedChangeOf(result);
		assertThat(change.text()).isNull();
		assertThat(change.support()).isEqualTo("이어 쳤네요. 괜찮아요.");
		assertThat(logs).anyMatch(l -> l.contains("field=change") && l.contains("reason=UNGROUNDED"));
		assertThat(logs).anyMatch(l -> l.contains("SUPPORT_RETRY"));
		assertThat(logs).noneMatch(l -> l.contains("SUPPORT_FALLBACK"));
		org.mockito.Mockito.verify(aiChatClient, org.mockito.Mockito.times(2)).complete(anyString(), anyString());
	}

	@Test
	void t8_hardFeelingWithOvercomePhrase_staysComfortType_noRecognitionRequired() {
		ActivityRecord record = pendingRecord("드디어 마지막 곡까지 쳤지만 몸이 무거웠다", null, Feeling.STUCK);

		ActivityRecord result = analyzeRecord(record, changeJson(null, "몸이 무거운 날이었네요. 고생했어요.", record.getId()));

		assertThat(storedChangeOf(result).support()).isEqualTo("몸이 무거운 날이었네요. 고생했어요.");
		org.mockito.Mockito.verify(aiChatClient, org.mockito.Mockito.times(1)).complete(anyString(), anyString());
	}

	@Test
	void t8_overcomePhraseWithoutDifficulty_allowsStrongRecognition_notOverpraise() {
		java.util.List<String> logs = captureLogs();
		ActivityRecord record = pendingRecord("드디어 F 코드를 깨끗하게 잡았다", null, Feeling.NEUTRAL);

		ActivityRecord result = analyzeRecord(record, changeJson(null, "드디어 F 코드를 잡았네요. 해냈네요.", record.getId()));

		assertThat(storedChangeOf(result).support()).isEqualTo("드디어 F 코드를 잡았네요. 해냈네요.");
		assertThat(logs).noneMatch(l -> l.contains("SUPPORT_OVERPRAISE") || l.contains("SUPPORT_RETRY"));
	}

	@Test
	void t8_threeSameEndingsInARow_onlyLogsStyleSoft() {
		java.util.List<String> logs = captureLogs();
		ActivityRecord record = achievementRecord();

		ActivityRecord result = analyzeRecord(record,
				changeJson("어제는 막혔는데 오늘은 잡았네요.", "10번 중 8번 해냈네요. 손가락이 아파도 쳤네요.", record.getId()));

		// 글은 그대로 저장하고(버리지 않고) 로그만 남긴다.
		StoredChange change = storedChangeOf(result);
		assertThat(change.text()).isEqualTo("어제는 막혔는데 오늘은 잡았네요.");
		assertThat(change.support()).isEqualTo("10번 중 8번 해냈네요. 손가락이 아파도 쳤네요.");
		assertThat(logs).anyMatch(l -> l.startsWith("STYLE_SOFT recordId=") && l.contains("reason=SAME_ENDING")
				&& l.contains("ending=네요") && l.contains("run=3"));
		assertThat(logs).noneMatch(l -> l.contains("SUPPORT_RETRY"));
	}
}
