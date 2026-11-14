package com.growit.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.ai.client.AiChatClient;
import com.growit.ai.client.AiChatResponse;
import com.growit.ai.config.AiProperties;
import com.growit.ai.dto.AiCompareResponse;
import com.growit.ai.dto.CompareRequest;
import com.growit.ai.entity.AiResult;
import com.growit.ai.repository.AiLogRepository;
import com.growit.ai.repository.AiResultRepository;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.record.entity.ActivityRecord;
import com.growit.record.entity.Feeling;
import com.growit.record.repository.ActivityRecordRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class CompareServiceTest {

	@Autowired
	private ActivityRecordRepository activityRecordRepository;

	@Autowired
	private HobbyRepository hobbyRepository;

	@Autowired
	private AiResultRepository aiResultRepository;

	@Autowired
	private AiLogRepository aiLogRepository;

	private final AiChatClient aiChatClient = mock(AiChatClient.class);
	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

	private AiProperties enabledProperties() {
		AiProperties properties = new AiProperties();
		properties.setApiKey("key");
		properties.setModel("model");
		properties.setMaxConcurrency(2);
		return properties;
	}

	private CompareService service(AiProperties properties) {
		AiUsageService usageService = new AiUsageService(aiLogRepository, properties);
		AiCallService callService = new AiCallService(properties, aiChatClient, aiLogRepository, usageService,
				objectMapper);
		AiResponseValidator validator = new AiResponseValidator(objectMapper);
		return new CompareService(activityRecordRepository, hobbyRepository, aiResultRepository, callService,
				validator, objectMapper);
	}

	private Long createHobby(Long userId) {
		return hobbyRepository.save(new Hobby(userId, "기타", "코드 전환 매끄럽게")).getId();
	}

	private ActivityRecord createRecord(Long userId, Long hobbyId, LocalDate date, String content) {
		return activityRecordRepository.save(
				new ActivityRecord(userId, hobbyId, date, content, 20, "어려웠다", Feeling.PROUD, false));
	}

	private void stubAiResponse(String json) {
		given(aiChatClient.complete(anyString(), anyString())).willReturn(new AiChatResponse(json, 10, "model"));
	}

	@Test
	void compare_fewerThanThreeRecords_returnsLockedWithoutCallingProvider() {
		Long hobbyId = createHobby(1L);
		createRecord(1L, hobbyId, LocalDate.now().minusDays(20), "연습1");
		createRecord(1L, hobbyId, LocalDate.now(), "연습2");

		AiCompareResponse response = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "2W"));

		assertThat(response.locked()).isTrue();
		assertThat(response.lockReason()).isEqualTo("NOT_ENOUGH_RECORDS");
		assertThat(response.requiredCount()).isEqualTo(3);
		assertThat(response.currentCount()).isEqualTo(2);
		verifyNoInteractions(aiChatClient);
	}

	@Test
	void compare_noPastRecordAndGapTooSmall_returnsLockedWithNoPastRecordReason() {
		Long hobbyId = createHobby(1L);
		// 이틀 간격 3개: 가장 오래된 기록과 현재 기록의 간격이 3일 미만이라 대신 쓸 수 없다.
		LocalDate oldest = LocalDate.now().minusDays(2);
		createRecord(1L, hobbyId, oldest, "연습1");
		createRecord(1L, hobbyId, LocalDate.now().minusDays(1), "연습2");
		createRecord(1L, hobbyId, LocalDate.now(), "연습3");

		AiCompareResponse response = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "3M"));

		assertThat(response.locked()).isTrue();
		assertThat(response.lockReason()).isEqualTo("NO_PAST_RECORD");
		assertThat(response.requiredCount()).isEqualTo(3);
		assertThat(response.currentCount()).isEqualTo(3);
		assertThat(response.oldestRecordDate()).isEqualTo(oldest);
		assertThat(response.latestRecordDate()).isEqualTo(LocalDate.now());
		verifyNoInteractions(aiChatClient);
	}

	@Test
	void compare_allSameDate_returnsLockedWithNoPastRecordReason() {
		Long hobbyId = createHobby(1L);
		LocalDate sameDate = LocalDate.now();
		createRecord(1L, hobbyId, sameDate, "연습1");
		createRecord(1L, hobbyId, sameDate, "연습2");
		createRecord(1L, hobbyId, sameDate, "연습3");

		AiCompareResponse response = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "1W"));

		assertThat(response.locked()).isTrue();
		assertThat(response.lockReason()).isEqualTo("NO_PAST_RECORD");
		verifyNoInteractions(aiChatClient);
	}

	@Test
	void compare_sixRecordsOverThreeWeeks_1W_findsRecordJustBeforeTarget() {
		Long hobbyId = createHobby(1L);
		LocalDate today = LocalDate.now();
		createRecord(1L, hobbyId, today.minusDays(18), "연습1"); // 10/27 상당
		ActivityRecord second = createRecord(1L, hobbyId, today.minusDays(16), "연습2"); // 10/29 상당
		createRecord(1L, hobbyId, today.minusDays(14), "연습3"); // 10/31 상당
		createRecord(1L, hobbyId, today.minusDays(11), "연습4"); // 11/3 상당
		createRecord(1L, hobbyId, today.minusDays(10), "연습5"); // 11/4 상당
		ActivityRecord current = createRecord(1L, hobbyId, today.minusDays(8), "연습6"); // 11/6 상당(기준일)

		stubAiResponse(("{\"interpretation\":\"기록상 달라졌어요\",\"pastRecordIds\":[%d],\"currentRecordIds\":[%d]}")
				.formatted(second.getId(), current.getId()));

		AiCompareResponse response = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "1W"));

		assertThat(response.locked()).isFalse();
		assertThat(response.baseDate()).isEqualTo(current.getRecordDate());
		assertThat(response.pastRecord().id()).isEqualTo(second.getId());
		assertThat(response.currentRecord().id()).isEqualTo(current.getId());
		assertThat(response.pastIsOldest()).isFalse();
	}

	@Test
	void compare_sixRecordsOverThreeWeeks_2W_fallsBackToOldestRecord() {
		Long hobbyId = createHobby(1L);
		LocalDate today = LocalDate.now();
		ActivityRecord oldest = createRecord(1L, hobbyId, today.minusDays(18), "연습1"); // 10/27 상당
		createRecord(1L, hobbyId, today.minusDays(16), "연습2");
		createRecord(1L, hobbyId, today.minusDays(14), "연습3");
		createRecord(1L, hobbyId, today.minusDays(11), "연습4");
		createRecord(1L, hobbyId, today.minusDays(10), "연습5");
		ActivityRecord current = createRecord(1L, hobbyId, today.minusDays(8), "연습6");

		stubAiResponse(("{\"interpretation\":\"기록상 달라졌어요\",\"pastRecordIds\":[%d],\"currentRecordIds\":[%d]}")
				.formatted(oldest.getId(), current.getId()));

		AiCompareResponse response = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "2W"));

		assertThat(response.locked()).isFalse();
		assertThat(response.pastIsOldest()).isTrue();
		assertThat(response.pastRecord().id()).isEqualTo(oldest.getId());
	}

	@Test
	void compare_sixRecordsOverThreeWeeks_1MAnd3M_fallBackToOldestRecord() {
		Long hobbyId = createHobby(1L);
		LocalDate today = LocalDate.now();
		ActivityRecord oldest = createRecord(1L, hobbyId, today.minusDays(18), "연습1");
		createRecord(1L, hobbyId, today.minusDays(16), "연습2");
		createRecord(1L, hobbyId, today.minusDays(14), "연습3");
		createRecord(1L, hobbyId, today.minusDays(11), "연습4");
		createRecord(1L, hobbyId, today.minusDays(10), "연습5");
		ActivityRecord current = createRecord(1L, hobbyId, today.minusDays(8), "연습6");

		stubAiResponse(("{\"interpretation\":\"기록상 달라졌어요\",\"pastRecordIds\":[%d],\"currentRecordIds\":[%d]}")
				.formatted(oldest.getId(), current.getId()));

		AiCompareResponse oneMonth = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "1M"));
		assertThat(oneMonth.locked()).isFalse();
		assertThat(oneMonth.pastIsOldest()).isTrue();
		assertThat(oneMonth.pastRecord().id()).isEqualTo(oldest.getId());

		AiCompareResponse threeMonths = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "3M"));
		assertThat(threeMonths.locked()).isFalse();
		assertThat(threeMonths.pastIsOldest()).isTrue();
		assertThat(threeMonths.pastRecord().id()).isEqualTo(oldest.getId());
	}

	@Test
	void compare_currentRecordDatedToday_anchoredToLatestIsFalse() {
		Long hobbyId = createHobby(1L);
		ActivityRecord past = createRecord(1L, hobbyId, LocalDate.now().minusDays(20), "예전 연습");
		createRecord(1L, hobbyId, LocalDate.now().minusDays(10), "중간 연습");
		ActivityRecord current = createRecord(1L, hobbyId, LocalDate.now(), "최신 연습");

		stubAiResponse(("{\"interpretation\":\"기록상 달라졌어요\",\"pastRecordIds\":[%d],\"currentRecordIds\":[%d]}")
				.formatted(past.getId(), current.getId()));

		AiCompareResponse response = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "2W"));

		assertThat(response.anchoredToLatest()).isFalse();
	}

	@Test
	void compare_currentRecordNotDatedToday_anchoredToLatestIsTrue() {
		Long hobbyId = createHobby(1L);
		ActivityRecord past = createRecord(1L, hobbyId, LocalDate.now().minusDays(30), "예전 연습");
		createRecord(1L, hobbyId, LocalDate.now().minusDays(20), "중간 연습");
		ActivityRecord current = createRecord(1L, hobbyId, LocalDate.now().minusDays(8), "최신 연습");

		stubAiResponse(("{\"interpretation\":\"기록상 달라졌어요\",\"pastRecordIds\":[%d],\"currentRecordIds\":[%d]}")
				.formatted(past.getId(), current.getId()));

		AiCompareResponse response = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "2W"));

		assertThat(response.anchoredToLatest()).isTrue();
		assertThat(response.baseDate()).isEqualTo(current.getRecordDate());
	}

	@Test
	void compare_success_storesInterpretationAndMetrics() {
		Long hobbyId = createHobby(1L);
		ActivityRecord past = createRecord(1L, hobbyId, LocalDate.now().minusDays(30), "예전 연습");
		createRecord(1L, hobbyId, LocalDate.now().minusDays(5), "중간 연습");
		ActivityRecord current = createRecord(1L, hobbyId, LocalDate.now(), "최신 연습");

		stubAiResponse(("{\"interpretation\":\"기록상 연습 내용이 달라졌어요\",\"pastRecordIds\":[%d],"
				+ "\"currentRecordIds\":[%d]}").formatted(past.getId(), current.getId()));

		AiCompareResponse response = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "2W"));

		assertThat(response.locked()).isFalse();
		assertThat(response.reused()).isFalse();
		assertThat(response.interpretation()).isEqualTo("기록상 연습 내용이 달라졌어요");
		assertThat(response.pastRecord().id()).isEqualTo(past.getId());
		assertThat(response.currentRecord().id()).isEqualTo(current.getId());
		assertThat(response.metrics().recordedDays().current()).isEqualTo(3);

		assertThat(aiResultRepository.findAll()).hasSize(1);
	}

	@Test
	void compare_minutesZeroOnBothSides_metricsStillComputed() {
		Long hobbyId = createHobby(1L);
		ActivityRecord past = activityRecordRepository.save(
				new ActivityRecord(1L, hobbyId, LocalDate.now().minusDays(30), "예전 연습", null, null, null, false));
		activityRecordRepository.save(
				new ActivityRecord(1L, hobbyId, LocalDate.now().minusDays(5), "중간 연습", null, null, null, false));
		ActivityRecord current = activityRecordRepository.save(
				new ActivityRecord(1L, hobbyId, LocalDate.now(), "최신 연습", null, null, null, false));

		stubAiResponse(("{\"interpretation\":\"기록상 연습 내용이 달라졌어요\",\"pastRecordIds\":[%d],"
				+ "\"currentRecordIds\":[%d]}").formatted(past.getId(), current.getId()));

		AiCompareResponse response = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "2W"));

		assertThat(response.metrics().totalMinutes().past()).isEqualTo(0);
		assertThat(response.metrics().totalMinutes().current()).isEqualTo(0);
	}

	@Test
	void compare_doneRecordsWithTags_returnsTopTagsByFrequency() {
		Long hobbyId = createHobby(1L);
		ActivityRecord past = createRecord(1L, hobbyId, LocalDate.now().minusDays(30), "예전 연습");
		ActivityRecord mid1 = createRecord(1L, hobbyId, LocalDate.now().minusDays(10), "중간1");
		mid1.markAnalysisDone("{\"title\":\"t\",\"tags\":[\"박자\",\"호흡\"]}");
		activityRecordRepository.save(mid1);
		ActivityRecord mid2 = createRecord(1L, hobbyId, LocalDate.now().minusDays(5), "중간2");
		mid2.markAnalysisDone("{\"title\":\"t\",\"tags\":[\"박자\"]}");
		activityRecordRepository.save(mid2);
		ActivityRecord current = createRecord(1L, hobbyId, LocalDate.now(), "최신 연습");

		stubAiResponse(("{\"interpretation\":\"기록상 연습 내용이 달라졌어요\",\"pastRecordIds\":[%d],"
				+ "\"currentRecordIds\":[%d]}").formatted(past.getId(), current.getId()));

		AiCompareResponse response = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "2W"));

		assertThat(response.metrics().topTags()).hasSize(2);
		assertThat(response.metrics().topTags().get(0).tag()).isEqualTo("박자");
		assertThat(response.metrics().topTags().get(0).count()).isEqualTo(2);
	}

	@Test
	void compare_sameInputTwice_reusesStoredResultWithoutCallingProviderAgain() {
		Long hobbyId = createHobby(1L);
		ActivityRecord past = createRecord(1L, hobbyId, LocalDate.now().minusDays(20), "예전 연습");
		ActivityRecord current = createRecord(1L, hobbyId, LocalDate.now(), "최신 연습");
		createRecord(1L, hobbyId, LocalDate.now().minusDays(10), "중간 연습");

		stubAiResponse(("{\"interpretation\":\"기록상 달라졌어요\",\"pastRecordIds\":[%d],\"currentRecordIds\":[%d]}")
				.formatted(past.getId(), current.getId()));

		CompareService service = service(enabledProperties());
		CompareRequest request = new CompareRequest(hobbyId, "2W");
		AiCompareResponse first = service.compare(1L, request);
		AiCompareResponse second = service.compare(1L, request);

		assertThat(first.reused()).isFalse();
		assertThat(second.reused()).isTrue();
		assertThat(second.id()).isEqualTo(first.id());
		assertThat(aiResultRepository.findAll()).hasSize(1);
	}

	@Test
	void compare_evidenceIdNotAllowed_dropsThatIdOnly() {
		Long hobbyId = createHobby(1L);
		ActivityRecord past = createRecord(1L, hobbyId, LocalDate.now().minusDays(20), "예전 연습");
		ActivityRecord current = createRecord(1L, hobbyId, LocalDate.now(), "최신 연습");
		createRecord(1L, hobbyId, LocalDate.now().minusDays(10), "중간 연습");

		stubAiResponse("{\"interpretation\":\"기록상 달라졌어요\",\"pastRecordIds\":[99999],\"currentRecordIds\":["
				+ current.getId() + "]}");

		AiCompareResponse response = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "2W"));

		assertThat(response.interpretation()).isEqualTo("기록상 달라졌어요");
		assertThat(aiResultRepository.findAll()).hasSize(1);
	}

	@Test
	void compare_forbiddenWordInInterpretation_throwsAiFailed() {
		Long hobbyId = createHobby(1L);
		createRecord(1L, hobbyId, LocalDate.now().minusDays(20), "예전 연습");
		createRecord(1L, hobbyId, LocalDate.now(), "최신 연습");
		createRecord(1L, hobbyId, LocalDate.now().minusDays(10), "중간 연습");

		stubAiResponse("{\"interpretation\":\"실력이 늘었어요\",\"pastRecordIds\":[],\"currentRecordIds\":[]}");

		assertThatThrownBy(() -> service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "2W")))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_FAILED));
		assertThat(aiResultRepository.findAll()).isEmpty();
	}

	@Test
	void compare_hobbyNotOwned_throwsNotFound() {
		Long hobbyId = createHobby(2L);

		assertThatThrownBy(() -> service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "2W")))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
		verifyNoInteractions(aiChatClient);
	}

	@Test
	void compare_analysisUserLimitShared_exceeded_throwsLimitExceeded() {
		Long hobbyId = createHobby(1L);
		createRecord(1L, hobbyId, LocalDate.now().minusDays(20), "예전 연습");
		createRecord(1L, hobbyId, LocalDate.now(), "최신 연습");
		createRecord(1L, hobbyId, LocalDate.now().minusDays(10), "중간 연습");
		AiProperties properties = enabledProperties();
		properties.setUserDailyLimit(0);

		assertThatThrownBy(() -> service(properties).compare(1L, new CompareRequest(hobbyId, "2W")))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_LIMIT_EXCEEDED));
	}

	@Test
	void submitFeedback_ownResult_savesValue() {
		Long hobbyId = createHobby(1L);
		AiResult saved = aiResultRepository.save(new AiResult(1L, hobbyId, "COMPARE", LocalDate.now(), LocalDate.now(),
				"{\"interpretation\":\"기록상 달라졌어요\",\"pastRecordId\":null,\"currentRecordId\":null,\"metrics\":null}",
				"[]", "a".repeat(64)));

		AiCompareResponse response = service(enabledProperties()).submitFeedback(1L, saved.getId(), "HELPFUL");

		assertThat(response.feedback()).isEqualTo("HELPFUL");
	}

	@Test
	void submitFeedback_notOwner_throwsNotFound() {
		Long hobbyId = createHobby(2L);
		AiResult saved = aiResultRepository.save(new AiResult(2L, hobbyId, "COMPARE", LocalDate.now(), LocalDate.now(),
				"{\"interpretation\":\"기록상 달라졌어요\",\"pastRecordId\":null,\"currentRecordId\":null,\"metrics\":null}",
				"[]", "b".repeat(64)));

		assertThatThrownBy(() -> service(enabledProperties()).submitFeedback(1L, saved.getId(), "HELPFUL"))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}
}
