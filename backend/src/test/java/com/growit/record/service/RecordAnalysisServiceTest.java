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
				objectMapper, transactionManager);
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
	void markQueueRejected_pendingRecord_marksFailed() {
		Long hobbyId = createHobby(1L);
		ActivityRecord current = createPendingRecord(1L, hobbyId, LocalDate.now(), "연습");

		service(enabledProperties()).markQueueRejected(current.getId(), 1L);

		ActivityRecord result = activityRecordRepository.findById(current.getId()).orElseThrow();
		assertThat(result.getAiStatus()).isEqualTo(AiStatus.FAILED);
	}
}
