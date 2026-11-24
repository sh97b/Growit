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

	{
		// 구조화(json_schema) 호출도 같은 스텁(complete)으로 답하게 한다.
		given(aiChatClient.completeStructured(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyMap()))
				.willAnswer(invocation -> aiChatClient.complete(invocation.getArgument(0), invocation.getArgument(1)));
	}
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

	private static final String LEGACY_JSON = "{\"interpretation\":\"지난번엔 끊겼는데 이번엔 이어졌어요\",\"pastRecordIds\":[],\"currentRecordIds\":[]}";

	private String structuredJson(String headline, String goodItems, String gapItems, String nextItems) {
		return "{\"headline\":\"" + headline + "\",\"good\":[" + goodItems + "],\"gap\":[" + gapItems + "],\"next\":[" + nextItems + "]}";
	}

	private String insight(String point, String because, Long... ids) {
		return "{\"point\":\"" + point + "\",\"because\":\"" + because + "\",\"evidenceRecordIds\":" + java.util.Arrays.toString(ids) + "}";
	}

	private String idea(String text, Long... ids) {
		return "{\"idea\":\"" + text + "\",\"evidenceRecordIds\":" + java.util.Arrays.toString(ids) + "}";
	}

	@Test
	void compareStructured_success_storesItemsAndFillsEvidenceFromRecords() {
		Long hobbyId = createHobby(1L);
		ActivityRecord past = createRecord(1L, hobbyId, LocalDate.now().minusDays(30), "예전 연습에서는 소리가 자꾸 끊겼다");
		createRecord(1L, hobbyId, LocalDate.now().minusDays(5), "중간 연습");
		ActivityRecord current = createRecord(1L, hobbyId, LocalDate.now(), "최신 연습에서는 천천히 이어 쳤다");

		stubAiResponse(structuredJson("소리가 덜 끊겼어요",
				insight("끊기는 일이 줄었어요", "지난번엔 끊겼는데 이번엔 이어 쳤다고 적었어요", past.getId(), current.getId()),
				insight("속도는 아직 남았어요", "천천히 칠 때만 이어졌어요", current.getId()),
				idea("천천히 쳤던 방식으로 한 번 더 해 보세요", current.getId())));

		AiCompareResponse response = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "2W"));

		assertThat(response.schemaVersion()).isEqualTo(2);
		assertThat(response.headline()).isEqualTo("소리가 덜 끊겼어요");
		assertThat(response.good()).hasSize(1);
		assertThat(response.good().get(0).evidence()).extracting("id").containsExactly(past.getId(), current.getId());
		assertThat(response.good().get(0).evidence().get(0).content()).isEqualTo("예전 연습에서는 소리가 자꾸 끊겼다");
		assertThat(response.good().get(0).evidence().get(0).recordDate()).isEqualTo(past.getRecordDate());
		assertThat(response.gap()).hasSize(1);
		assertThat(response.next()).hasSize(1);
		assertThat(response.pastRecord().id()).isEqualTo(past.getId());
		assertThat(response.currentRecord().id()).isEqualTo(current.getId());
		assertThat(aiResultRepository.findAll()).hasSize(1);
	}

	@Test
	void compareStructured_failedItemsAreDroppedOthersKept() {
		Long hobbyId = createHobby(1L);
		ActivityRecord past = createRecord(1L, hobbyId, LocalDate.now().minusDays(30), "예전 연습");
		createRecord(1L, hobbyId, LocalDate.now().minusDays(5), "중간 연습");
		ActivityRecord current = createRecord(1L, hobbyId, LocalDate.now(), "최신 연습");

		stubAiResponse(structuredJson("달라진 점이 보여요",
				insight("실력이 늘었어요", "금지어가 들어간 항목", current.getId()) + ","
						+ insight("이어 쳤어요", "이번엔 이어 쳤다고 적었어요", current.getId()),
				insight("근거 없는 항목", "근거 id가 비어 있어요") + ","
						+ insight("낯선 id 항목", "허용되지 않은 id예요", 99999L),
				idea("한 번 더 해 보세요", past.getId()) + "," + idea("긴".repeat(111), current.getId())));

		AiCompareResponse response = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "2W"));

		assertThat(response.schemaVersion()).isEqualTo(2);
		assertThat(response.good()).extracting("point").containsExactly("이어 쳤어요");
		// 근거가 비었거나 낯선 id인 항목은 버리지 않고 최근 기록 id로 대신한다. 글자 수 하드 한도(110자)를 넘은 제안만 버려진다.
		assertThat(response.gap()).extracting("point").containsExactly("근거 없는 항목", "낯선 id 항목");
		assertThat(response.next()).extracting("idea").containsExactly("한 번 더 해 보세요");
	}

	@Test
	void compareStructured_otherUsersRecordId_isReplacedWithCurrentRecord_notTrusted() {
		Long hobbyId = createHobby(1L);
		createRecord(1L, hobbyId, LocalDate.now().minusDays(30), "예전 연습");
		createRecord(1L, hobbyId, LocalDate.now().minusDays(5), "중간 연습");
		ActivityRecord current = createRecord(1L, hobbyId, LocalDate.now(), "최신 연습");
		Long otherHobby = createHobby(2L);
		ActivityRecord others = createRecord(2L, otherHobby, LocalDate.now(), "남의 기록");

		stubAiResponse(structuredJson("남의 기록을 끌어왔어요",
				insight("남의 기록", "허용되지 않은 근거예요", others.getId()), "", ""));

		AiCompareResponse response = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "2W"));

		assertThat(response.schemaVersion()).isEqualTo(2);
		assertThat(response.good().get(0).evidence()).extracting("id").containsExactly(current.getId());
		assertThat(response.good().get(0).evidence()).extracting("content").doesNotContain("남의 기록");
	}

	@Test
	void compareStructured_allFail_replacesWithLegacyOneParagraph() {
		Long hobbyId = createHobby(1L);
		ActivityRecord past = createRecord(1L, hobbyId, LocalDate.now().minusDays(30), "예전 연습");
		createRecord(1L, hobbyId, LocalDate.now().minusDays(5), "중간 연습");
		createRecord(1L, hobbyId, LocalDate.now(), "최신 연습");

		// 첫 응답: 기록에 없는 숫자(30번)가 들어 있어 good이 하나도 안 남는다. 두 번째 응답: 예전 모양.
		given(aiChatClient.complete(anyString(), anyString()))
				.willReturn(new AiChatResponse(structuredJson("달라졌어요",
						insight("많이 했어요", "30번을 했다고 적었어요", past.getId()), "", ""), 10, "model"))
				.willReturn(new AiChatResponse(LEGACY_JSON, 10, "model"));

		AiCompareResponse response = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "2W"));

		assertThat(response.schemaVersion()).isNull();
		assertThat(response.interpretation()).isEqualTo("지난번엔 끊겼는데 이번엔 이어졌어요");
		assertThat(aiResultRepository.findAll()).hasSize(1);
		org.mockito.Mockito.verify(aiChatClient, org.mockito.Mockito.times(3)).complete(anyString(), anyString());
	}

	@Test
	void compareStructured_legacyResultStillReadsAsOldShape() {
		Long hobbyId = createHobby(1L);
		ActivityRecord past = createRecord(1L, hobbyId, LocalDate.now().minusDays(30), "예전 연습");
		createRecord(1L, hobbyId, LocalDate.now().minusDays(5), "중간 연습");
		ActivityRecord current = createRecord(1L, hobbyId, LocalDate.now(), "최신 연습");
		String oldJson = "{\"interpretation\":\"예전에 저장된 한 문단이에요\",\"pastRecordId\":" + past.getId()
				+ ",\"currentRecordId\":" + current.getId() + ",\"metrics\":null,\"baseDate\":\"" + LocalDate.now()
				+ "\",\"anchoredToLatest\":false,\"pastIsOldest\":false}";
		AiResult saved = aiResultRepository.save(new AiResult(1L, hobbyId, "COMPARE", past.getRecordDate(),
				LocalDate.now(), oldJson, "[" + past.getId() + "," + current.getId() + "]", "old-key", 3, "model"));

		AiCompareResponse response = service(enabledProperties()).getById(1L, saved.getId());

		assertThat(response.interpretation()).isEqualTo("예전에 저장된 한 문단이에요");
		assertThat(response.schemaVersion()).isNull();
		assertThat(response.good()).isNull();
		assertThat(response.pastRecord().id()).isEqualTo(past.getId());
	}

	private java.util.List<String> captureCompareLogs() {
		ch.qos.logback.classic.Logger logger =
				(ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(CompareService.class);
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

	/** 과거·현재·중간 기록 세 개를 만든다(과거 기록 본문과 현재 기록 본문을 지정). */
	private ActivityRecord[] threeRecords(Long hobbyId, String pastContent, String currentContent) {
		ActivityRecord past = createRecord(1L, hobbyId, LocalDate.now().minusDays(20), pastContent);
		createRecord(1L, hobbyId, LocalDate.now().minusDays(10), "중간 연습");
		ActivityRecord current = createRecord(1L, hobbyId, LocalDate.now(), currentContent);
		return new ActivityRecord[] {past, current};
	}

	@Test
	void structured_lengthBoundaries_headline100AndItem110AreKept_oneMoreIsDropped() {
		Long hobbyId = createHobby(1L);
		ActivityRecord[] r = threeRecords(hobbyId, "예전 연습", "최신 연습");
		String headline100 = "가".repeat(100);
		String item110 = "나".repeat(110);

		stubAiResponse(structuredJson(headline100,
				insight(item110, "이유", r[1].getId()) + "," + insight("다".repeat(111), "이유", r[1].getId()),
				"", idea("라".repeat(110), r[1].getId()) + "," + idea("마".repeat(111), r[1].getId())));
		AiCompareResponse response = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "2W"));

		assertThat(response.schemaVersion()).isEqualTo(2);
		assertThat(response.headline()).hasSize(100);
		assertThat(response.good()).hasSize(1);
		assertThat(response.good().get(0).point()).hasSize(110);
		assertThat(response.next()).hasSize(1);
		assertThat(response.next().get(0).idea()).hasSize(110);
	}

	@Test
	void structured_headline101_failsWholeStructure_afterOneStructuredRetry_thenLegacy() {
		Long hobbyId = createHobby(1L);
		ActivityRecord[] r = threeRecords(hobbyId, "예전 연습", "최신 연습");
		java.util.List<String> logs = captureCompareLogs();
		given(aiChatClient.complete(anyString(), anyString()))
				.willReturn(new AiChatResponse(structuredJson("가".repeat(101), insight("점", "이유", r[1].getId()), "", ""), 10, "model"))
				.willReturn(new AiChatResponse(LEGACY_JSON, 10, "model"));

		AiCompareResponse response = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "2W"));

		assertThat(response.schemaVersion()).isNull();
		assertThat(response.interpretation()).isEqualTo("지난번엔 끊겼는데 이번엔 이어졌어요");
		// 구조화 1회 + 구조화 교정 재호출 1회 + 예전 방식 1회
		org.mockito.Mockito.verify(aiChatClient, org.mockito.Mockito.times(3)).complete(anyString(), anyString());
		org.mockito.Mockito.verify(aiChatClient, org.mockito.Mockito.times(2))
				.completeStructured(anyString(), anyString(), org.mockito.ArgumentMatchers.eq("compare_insights"),
						org.mockito.ArgumentMatchers.anyMap());
		assertThat(logs).anyMatch(m -> m.startsWith("COMPARE_STRUCT_DROPPED recordIds=[")
				&& m.contains("field=headline reason=LENGTH length=101 raw={"));
		assertThat(logs).anyMatch(m -> m.startsWith("COMPARE_FALLBACK_LEGACY reason=headline:"));
	}

	@Test
	void structured_retryGetsRejectionHint_andSecondAnswerIsUsed_withoutCountingTowardLimit() {
		Long hobbyId = createHobby(1L);
		ActivityRecord[] r = threeRecords(hobbyId, "예전 연습", "최신 연습");
		java.util.List<String> logs = captureCompareLogs();
		org.mockito.ArgumentCaptor<String> system = org.mockito.ArgumentCaptor.forClass(String.class);
		given(aiChatClient.complete(system.capture(), anyString()))
				.willReturn(new AiChatResponse(structuredJson("달라졌어요", insight("횟수가 많아졌어요", "99번이래요", r[1].getId()), "", ""), 10, "model"))
				.willReturn(new AiChatResponse(structuredJson("달라졌어요", insight("조금 편해졌어요", "이유", r[1].getId()), "",
						idea("한 번 더 해 보세요", r[1].getId())), 10, "model"));

		AiCompareResponse response = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "2W"));

		assertThat(response.schemaVersion()).isEqualTo(2);
		assertThat(response.good()).extracting("point").containsExactly("조금 편해졌어요");
		assertThat(system.getAllValues()).hasSize(2);
		assertThat(system.getAllValues().get(0)).doesNotContain("[교정 지시]");
		assertThat(system.getAllValues().get(1)).contains("[교정 지시]", "good 필드", "기록에 없는 숫자");
		assertThat(aiLogRepository.findAll()).extracting("state").containsExactlyInAnyOrder("OK", "RETRY");
		assertThat(logs).anyMatch(m -> m.contains("field=good reason=NUMBER"));
		assertThat(logs).anyMatch(m -> m.equals("COMPARE_STRUCT_OK good=1 gap=0 next=1"));
		assertThat(logs).noneMatch(m -> m.startsWith("COMPARE_FALLBACK_LEGACY"));
	}

	@Test
	void structured_koreanNumeralsAndArabicDigitsMatchEachOther() {
		Long hobbyId = createHobby(1L);
		ActivityRecord[] r = threeRecords(hobbyId, "코드를 3번 바꿨다", "열 번 이어서 쳤다");

		stubAiResponse(structuredJson("달라졌어요",
				insight("10번을 이어 쳤어요", "예전엔 세 번이었어요", r[1].getId()) + ","
						+ insight("여섯 번도 해 봤어요", "이유", r[1].getId()),
				"", ""));
		AiCompareResponse response = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "2W"));

		// 열 번=10, 3번=세 번은 통과하고, 입력에 없는 "여섯 번"(6)은 버려진다.
		assertThat(response.schemaVersion()).isEqualTo(2);
		assertThat(response.good()).extracting("point").containsExactly("10번을 이어 쳤어요");
	}

	@Test
	void numbersIn_readsDigitsAndNativeKoreanNumerals() {
		assertThat(CompareService.numbersIn("10번 쳤다")).containsExactly(10);
		assertThat(CompareService.numbersIn("열 번 쳤다")).containsExactly(10);
		assertThat(CompareService.numbersIn("여섯 개와 스물세 번")).containsExactlyInAnyOrder(6, 23);
		assertThat(CompareService.numbersIn("한국 세계 네이버")).isEmpty();
		assertThat(CompareService.numbersIn(null)).isEmpty();
	}

	@Test
	void structured_missingOrForeignEvidence_isReplacedWithCurrentRecordId() {
		Long hobbyId = createHobby(1L);
		ActivityRecord[] r = threeRecords(hobbyId, "예전 연습", "최신 연습");
		Long otherHobby = createHobby(2L);
		ActivityRecord others = createRecord(2L, otherHobby, LocalDate.now(), "남의 기록");

		stubAiResponse(structuredJson("달라졌어요",
				insight("근거 비어 있음", "이유") + "," + insight("남의 id", "이유", others.getId(), 99999L) + ","
						+ insight("섞인 id", "이유", others.getId(), r[0].getId()),
				"", idea("제안", others.getId())));
		AiCompareResponse response = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "2W"));

		assertThat(response.schemaVersion()).isEqualTo(2);
		assertThat(response.good()).hasSize(3);
		assertThat(response.good().get(0).evidence()).extracting("id").containsExactly(r[1].getId());
		assertThat(response.good().get(1).evidence()).extracting("id").containsExactly(r[1].getId());
		assertThat(response.good().get(2).evidence()).extracting("id").containsExactly(r[0].getId());
		assertThat(response.next().get(0).evidence()).extracting("id").containsExactly(r[1].getId());
	}

	@Test
	void structured_requestUsesStrictJsonSchema() {
		AiProperties unused = enabledProperties();
		java.util.Map<String, Object> schema = CompareService.structuredSchema();

		assertThat(schema.get("additionalProperties")).isEqualTo(false);
		assertThat(schema.get("required")).isEqualTo(java.util.List.of("headline", "good", "gap", "next"));
		@SuppressWarnings("unchecked")
		java.util.Map<String, Object> properties = (java.util.Map<String, Object>) schema.get("properties");
		@SuppressWarnings("unchecked")
		java.util.Map<String, Object> goodItem = (java.util.Map<String, Object>) ((java.util.Map<String, Object>) properties
				.get("good")).get("items");
		assertThat(goodItem.get("additionalProperties")).isEqualTo(false);
		assertThat(goodItem.get("required")).isEqualTo(java.util.List.of("point", "because", "evidenceRecordIds"));
		assertThat(unused).isNotNull();
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
