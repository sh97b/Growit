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
				&& m.contains("field=headline reason=LENGTH length=101 item=- text=") && m.contains(" matched=- raw={"));
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
		// 첫 응답 + 교정 재호출 + gap 보강 재호출(gap이 비어서). 총 3번이고 앞의 둘만 하루 한도에 세지 않는다.
		assertThat(system.getAllValues()).hasSize(3);
		assertThat(system.getAllValues().get(0)).doesNotContain("[교정 지시]");
		assertThat(system.getAllValues().get(1)).contains("[교정 지시]", "good 필드", "기록에 없는 숫자");
		assertThat(system.getAllValues().get(2)).contains("[보강 지시]", "gap 구역이 비어 있");
		assertThat(aiLogRepository.findAll()).extracting("state").containsExactlyInAnyOrder("OK", "RETRY", "RETRY");
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
						+ insight("마흔 번도 해 봤어요", "이유", r[1].getId()),
				"", ""));
		AiCompareResponse response = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "2W"));

		// 열 번=10, 3번=세 번은 통과하고, 입력에 없는 "마흔 번"(40)은 버려진다.
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
	void basis_returnsRecordsAndNumbersWithoutCallingProvider_andMatchesCompare() {
		Long hobbyId = createHobby(1L);
		ActivityRecord past = createRecord(1L, hobbyId, LocalDate.now().minusDays(30), "예전 연습");
		createRecord(1L, hobbyId, LocalDate.now().minusDays(5), "중간 연습");
		ActivityRecord current = createRecord(1L, hobbyId, LocalDate.now(), "최신 연습");
		CompareRequest request = new CompareRequest(hobbyId, "2W");

		com.growit.ai.dto.CompareBasisResponse basis = service(enabledProperties()).basis(1L, request);

		verifyNoInteractions(aiChatClient);
		assertThat(basis.locked()).isFalse();
		assertThat(basis.previous().id()).isEqualTo(past.getId());
		assertThat(basis.latest().id()).isEqualTo(current.getId());
		assertThat(basis.pastDate()).isEqualTo(past.getRecordDate());
		assertThat(basis.currentDate()).isEqualTo(current.getRecordDate());
		assertThat(basis.anchoredToLatest()).isFalse();
		assertThat(basis.numbers().recordedDays().current()).isEqualTo(3);

		// 같은 요청의 AI 해석 응답과 값이 일치한다.
		stubAiResponse(structuredJson("달라졌어요", insight("점", "이유", current.getId()), "", ""));
		AiCompareResponse compare = service(enabledProperties()).compare(1L, request);
		assertThat(compare.pastRecord().id()).isEqualTo(basis.previous().id());
		assertThat(compare.currentRecord().id()).isEqualTo(basis.latest().id());
		assertThat(compare.metrics()).isEqualTo(basis.numbers());
		assertThat(compare.baseDate()).isEqualTo(basis.baseDate());
		assertThat(compare.pastIsOldest()).isEqualTo(basis.pastIsOldest());
	}

	@Test
	void basis_locked_returnsReasonWithoutRecords() {
		Long hobbyId = createHobby(1L);
		createRecord(1L, hobbyId, LocalDate.now(), "연습1");

		com.growit.ai.dto.CompareBasisResponse basis = service(enabledProperties()).basis(1L, new CompareRequest(hobbyId, "1W"));

		assertThat(basis.locked()).isTrue();
		assertThat(basis.lockReason()).isEqualTo("NOT_ENOUGH_RECORDS");
		assertThat(basis.previous()).isNull();
		verifyNoInteractions(aiChatClient);
	}

	@Test
	void basis_otherUsersHobby_isNotFound() {
		Long hobbyId = createHobby(2L);

		assertThatThrownBy(() -> service(enabledProperties()).basis(1L, new CompareRequest(hobbyId, "1W")))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void basis_invalidCompareTo_isRejected() {
		Long hobbyId = createHobby(1L);

		assertThatThrownBy(() -> service(enabledProperties()).basis(1L, new CompareRequest(hobbyId, "9Y")))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
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

	// ---- CH5: 금지어 A·B 분리, 숫자 검사 범위, 구역 최소 1개 보장 ----

	private static final String OK_GOOD = "{\"point\":\"이어 쳤어요\",\"because\":\"이번엔 이어서 쳤어요\",\"evidenceRecordIds\":[%d]}";

	@Test
	void ch5_gap_allowsBWords_butRejectsAWords_andNextRejectsBWords() {
		Long hobbyId = createHobby(1L);
		ActivityRecord[] r = threeRecords(hobbyId, "예전 연습", "최신 연습");
		Long cur = r[1].getId();
		java.util.List<String> logs = captureCompareLogs();

		stubAiResponse(structuredJson("달라졌어요",
				insight("이어 쳤어요", "이번엔 이어서 쳤어요", cur) + "," + insight("완벽한 연주예요", "이유", cur),
				insight("아직 완벽하지는 않아요", "천천히 칠 때만 확인했어요", cur) + "," + insight("실력이 부족해요", "이유", cur),
				idea("속도를 한 단계 올려 보세요", cur) + "," + idea("최고 속도로 쳐 보세요", cur)));
		AiCompareResponse response = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "2W"));

		// good: B 단어(완벽)는 거절, gap: B 단어는 통과·A 단어(실력)는 거절, next: B 단어(최고)는 거절.
		assertThat(response.good()).extracting("point").containsExactly("이어 쳤어요");
		assertThat(response.gap()).extracting("point").containsExactly("아직 완벽하지는 않아요");
		assertThat(response.next()).extracting("idea").containsExactly("속도를 한 단계 올려 보세요");
		// 거절 로그에는 항목 번호·앞 80자·걸린 단어가 남는다.
		assertThat(logs).anyMatch(m -> m.startsWith("COMPARE_STRUCT_DROPPED") && m.contains("field=good reason=FORBIDDEN")
				&& m.contains("item=1 text=완벽한 연주예요 matched=완벽"));
		assertThat(logs).anyMatch(m -> m.contains("field=gap reason=FORBIDDEN") && m.contains("item=1 text=실력이 부족해요 matched=실력"));
		assertThat(logs).anyMatch(m -> m.contains("field=next reason=FORBIDDEN") && m.contains("item=1 text=최고 속도로 쳐 보세요 matched=최고"));
		org.mockito.Mockito.verify(aiChatClient, org.mockito.Mockito.times(1)).complete(anyString(), anyString());
	}

	@Test
	void ch5_numbers_goodRejectsFalse_gapSoftWarns_nextAllowsNew_diffAndSumAreAllowed() {
		Long hobbyId = createHobby(1L);
		ActivityRecord[] r = threeRecords(hobbyId, "코드 연습을 20분 했다", "코드 연습을 30분 했다");
		Long cur = r[1].getId();
		java.util.List<String> logs = captureCompareLogs();

		stubAiResponse(structuredJson("달라졌어요",
				insight("10분 더 길게 했어요", "20분에서 30분으로 늘어 합쳐 50분을 연습했어요", cur) + ","
						+ insight("77번이나 했어요", "이유", cur),
				insight("횟수는 아직 적어요", "91번에 비하면 적어요", cur),
				idea("속도를 60으로 맞춰 다섯 번 쳐 보세요", cur)));
		AiCompareResponse response = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "2W"));

		// good: 입력 숫자(20·30)의 차(10)·합(50)은 통과, 기록에 없는 77은 거절.
		assertThat(response.good()).extracting("point").containsExactly("10분 더 길게 했어요");
		// gap: 같은 규칙이지만 소프트라서 통과하고 경고만 남긴다.
		assertThat(response.gap()).extracting("point").containsExactly("횟수는 아직 적어요");
		assertThat(logs).anyMatch(m -> m.startsWith("COMPARE_NUMBER_SOFT where=gap.0 number=91"));
		// next: 숫자 검사를 하지 않아 새 숫자(60)도 통과한다.
		assertThat(response.next()).extracting("idea").containsExactly("속도를 60으로 맞춰 다섯 번 쳐 보세요");
		assertThat(logs).anyMatch(m -> m.contains("field=good reason=NUMBER") && m.contains("matched=77"));
		assertThat(logs).noneMatch(m -> m.contains("field=next reason=NUMBER"));
	}

	@Test
	void ch5_emptyGapAndNext_retriesOnceWithRejectionNote_notCountedTowardLimit() {
		Long hobbyId = createHobby(1L);
		ActivityRecord[] r = threeRecords(hobbyId, "예전 연습", "최신 연습");
		Long cur = r[1].getId();
		org.mockito.ArgumentCaptor<String> system = org.mockito.ArgumentCaptor.forClass(String.class);
		given(aiChatClient.complete(system.capture(), anyString()))
				.willReturn(new AiChatResponse(structuredJson("달라졌어요", String.format(OK_GOOD, cur),
						insight("실력이 부족해요", "이유", cur), ""), 10, "model"))
				.willReturn(new AiChatResponse(structuredJson("달라졌어요", String.format(OK_GOOD, cur),
						insight("속도는 아직 확인하지 못했어요", "천천히 칠 때만 적었어요", cur),
						idea("같은 전환을 속도만 올려 세 번 쳐 보세요", cur)), 10, "model"));

		AiCompareResponse response = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "2W"));

		assertThat(response.good()).extracting("point").containsExactly("이어 쳤어요");
		assertThat(response.gap()).extracting("point").containsExactly("속도는 아직 확인하지 못했어요");
		assertThat(response.next()).extracting("idea").containsExactly("같은 전환을 속도만 올려 세 번 쳐 보세요");
		assertThat(system.getAllValues()).hasSize(2);
		assertThat(system.getAllValues().get(1)).contains("[보강 지시]", "각각 1~2개 반드시 포함",
				"gap 1번째 항목이 쓰면 안 되는 표현이 있었기(실력)", "next 구역이 비어 있");
		assertThat(aiLogRepository.findAll()).extracting("state").containsExactlyInAnyOrder("OK", "RETRY");
	}

	@Test
	void ch5_stillEmptyAfterRetry_usesDeterministicFallback_difficultyAndNextAction() {
		Long hobbyId = createHobby(1L);
		ActivityRecord[] r = threeRecords(hobbyId, "예전 연습", "최신 연습");
		ActivityRecord current = r[1];
		current.markAnalysisDone("{\"tags\":[\"코드 전환\"],\"nextAction\":{\"text\":\"F에서 B♭으로 천천히 다섯 번 넘어가 보기\",\"minutes\":7}}");
		activityRecordRepository.save(current);
		java.util.List<String> logs = captureCompareLogs();
		stubAiResponse(structuredJson("달라졌어요", String.format(OK_GOOD, current.getId()), "", ""));

		AiCompareResponse response = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "2W"));

		assertThat(response.schemaVersion()).isEqualTo(2);
		// 막힌 점(어려움)이 있으면 그 말을, nextAction이 있으면 그 문장을 그대로 쓴다. 근거는 최근 기록.
		assertThat(response.gap()).extracting("point").containsExactly("기록에 적은 '어려웠다'는 아직 남은 숙제예요");
		assertThat(response.gap().get(0).evidence()).extracting("id").containsExactly(current.getId());
		assertThat(response.next()).extracting("idea").containsExactly("F에서 B♭으로 천천히 다섯 번 넘어가 보기");
		assertThat(response.next().get(0).evidence()).extracting("id").containsExactly(current.getId());
		assertThat(logs).anyMatch(m -> m.equals("COMPARE_SECTION_FALLBACK section=gap source=DIFFICULTY"));
		assertThat(logs).anyMatch(m -> m.equals("COMPARE_SECTION_FALLBACK section=next source=NEXT_ACTION"));
		// 첫 호출 + 보강 재호출 한 번뿐(3번 이하), 대체 문장에는 AI 호출이 없다.
		org.mockito.Mockito.verify(aiChatClient, org.mockito.Mockito.times(2)).complete(anyString(), anyString());
	}

	@Test
	void ch5_fallback_noDifficulty_usesFewRecordsSentence_andTagForNext_thenGeneric() {
		Long hobbyId = createHobby(1L);
		ActivityRecord past = createRecord(1L, hobbyId, LocalDate.now().minusDays(20), "예전 연습");
		createRecord(1L, hobbyId, LocalDate.now().minusDays(10), "중간 연습");
		ActivityRecord current = activityRecordRepository.save(
				new ActivityRecord(1L, hobbyId, LocalDate.now(), "최신 연습", 20, null, Feeling.PROUD, false));
		current.markAnalysisDone("{\"tags\":[\"코드 전환\",\"박자\"]}");
		activityRecordRepository.save(current);
		java.util.List<String> logs = captureCompareLogs();
		stubAiResponse(structuredJson("달라졌어요", String.format(OK_GOOD, current.getId()), "", ""));

		AiCompareResponse response = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "2W"));

		assertThat(response.gap()).extracting("point").containsExactly("비교할 기록이 아직 적어요");
		assertThat(response.gap()).extracting("because").containsExactly("기록이 더 쌓이면 변화가 더 선명해져요");
		assertThat(response.next()).extracting("idea").containsExactly("'코드 전환'를 한 가지 조건만 바꿔 한 번 더 해 보고 기록해 봐요");
		assertThat(logs).anyMatch(m -> m.equals("COMPARE_SECTION_FALLBACK section=gap source=FEW_RECORDS"));
		assertThat(logs).anyMatch(m -> m.equals("COMPARE_SECTION_FALLBACK section=next source=TAG"));
		assertThat(past.getId()).isNotNull();

		// 태그도 nextAction도 없으면 일반 문장으로 대신한다.
		ActivityRecord plain = activityRecordRepository.save(
				new ActivityRecord(1L, hobbyId, LocalDate.now().plusDays(1), "더 최신 연습", 20, null, Feeling.PROUD, false));
		stubAiResponse(structuredJson("달라졌어요", String.format(OK_GOOD, plain.getId()), "", ""));
		AiCompareResponse generic = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "2W"));
		assertThat(generic.next()).extracting("idea")
				.containsExactly("지금 하던 것을 한 가지 조건만 바꿔 한 번 더 해 보고 기록해 봐요");
		assertThat(logs).anyMatch(m -> m.equals("COMPARE_SECTION_FALLBACK section=next source=GENERIC"));
	}

	@Test
	void ch5_becauseTail_praiseOnlyLastSentenceIsCut_othersKept() {
		assertThat(CompareService.trimPraiseTail("이어서 쳤어요. 좋아요.")).isEqualTo("이어서 쳤어요.");
		assertThat(CompareService.trimPraiseTail("이어서 쳤어요. 멋져요!")).isEqualTo("이어서 쳤어요.");
		assertThat(CompareService.trimPraiseTail("이어서 쳤어요. 정말 대단해요")).isEqualTo("이어서 쳤어요.");
		// 평가 단어만이 아니거나 8자를 넘는 마지막 문장, 문장이 하나뿐인 경우는 건드리지 않는다.
		assertThat(CompareService.trimPraiseTail("이어서 쳤어요. 큰 변화예요.")).isEqualTo("이어서 쳤어요. 큰 변화예요.");
		assertThat(CompareService.trimPraiseTail("이어서 쳤어요. 박자도 좋아요.")).isEqualTo("이어서 쳤어요. 박자도 좋아요.");
		assertThat(CompareService.trimPraiseTail("좋아요.")).isEqualTo("좋아요.");
		assertThat(CompareService.trimPraiseTail("이어서 쳤어요")).isEqualTo("이어서 쳤어요");
	}

	@Test
	void ch5_becauseTail_isCutBeforeForbiddenCheck_soItemIsKept() {
		Long hobbyId = createHobby(1L);
		ActivityRecord[] r = threeRecords(hobbyId, "예전 연습", "최신 연습");
		Long cur = r[1].getId();

		// "대단해요"는 금지어(대단)지만, 평가 한 마디만 남은 꼬리라 먼저 잘려 항목이 살아남는다.
		stubAiResponse(structuredJson("달라졌어요",
				insight("이어 쳤어요", "이번엔 이어서 쳤어요. 대단해요.", cur),
				insight("속도는 아직 확인하지 못했어요", "천천히 칠 때만 적었어요", cur),
				idea("속도만 올려 세 번 쳐 보세요", cur)));
		AiCompareResponse response = service(enabledProperties()).compare(1L, new CompareRequest(hobbyId, "2W"));

		assertThat(response.good()).extracting("because").containsExactly("이번엔 이어서 쳤어요.");
	}
}
