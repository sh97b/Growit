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
		assertThat(result.getAiAnalysis()).contains("\"promptVersion\":5").contains("\"model\":\"model\"");
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
}
