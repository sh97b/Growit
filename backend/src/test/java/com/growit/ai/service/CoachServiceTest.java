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
import com.growit.ai.dto.AiCoachAutoResponse;
import com.growit.ai.dto.AiCoachResponse;
import com.growit.ai.dto.CoachRequest;
import com.growit.ai.entity.AiResult;
import com.growit.ai.repository.AiLogRepository;
import com.growit.ai.repository.AiResultRepository;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.notification.config.WebPushProperties;
import com.growit.notification.repository.NotificationSettingsRepository;
import com.growit.notification.repository.PushSubscriptionRepository;
import com.growit.notification.service.NotificationSchedulerService;
import com.growit.notification.service.PushSenderService;
import com.growit.record.entity.ActivityRecord;
import com.growit.record.entity.Feeling;
import com.growit.record.repository.ActivityRecordRepository;
import com.growit.user.entity.User;
import com.growit.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
class CoachServiceTest {

	@Autowired
	private ActivityRecordRepository activityRecordRepository;

	@Autowired
	private HobbyRepository hobbyRepository;

	@Autowired
	private AiResultRepository aiResultRepository;

	@Autowired
	private AiLogRepository aiLogRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private NotificationSettingsRepository notificationSettingsRepository;

	@Autowired
	private PushSubscriptionRepository pushSubscriptionRepository;

	private final AiChatClient aiChatClient = mock(AiChatClient.class);
	private final ObjectMapper objectMapper = new ObjectMapper();

	private AiProperties enabledProperties() {
		AiProperties properties = new AiProperties();
		properties.setApiKey("key");
		properties.setModel("model");
		properties.setMaxConcurrency(2);
		return properties;
	}

	private CoachService service(AiProperties properties) {
		AiUsageService usageService = new AiUsageService(aiLogRepository, properties);
		AiCallService callService = new AiCallService(properties, aiChatClient, aiLogRepository, usageService,
				objectMapper);
		AiResponseValidator validator = new AiResponseValidator(objectMapper);
		PushSenderService disabledPushSenderService = new PushSenderService(new WebPushProperties());
		NotificationSchedulerService notificationSchedulerService = new NotificationSchedulerService(
				notificationSettingsRepository, pushSubscriptionRepository, activityRecordRepository,
				disabledPushSenderService);
		return new CoachService(activityRecordRepository, hobbyRepository, aiResultRepository, callService, validator,
				objectMapper, userRepository, notificationSchedulerService, properties);
	}

	private Long createUser(boolean aiEnabled) {
		User user = userRepository.save(new User("user" + System.nanoTime() + "@test.com", "x", "테스터"));
		user.setAiEnabled(aiEnabled);
		return userRepository.save(user).getId();
	}

	private Long createHobby(Long userId) {
		return hobbyRepository.save(new Hobby(userId, "기타", "코드 전환 매끄럽게")).getId();
	}

	private ActivityRecord createRecord(Long userId, Long hobbyId, LocalDate date, String content) {
		return activityRecordRepository.save(
				new ActivityRecord(userId, hobbyId, date, content, 20, "어려웠다", Feeling.PROUD, false));
	}

	private void createRecords(Long userId, Long hobbyId, int count, LocalDate start) {
		for (int i = 0; i < count; i++) {
			createRecord(userId, hobbyId, start.plusDays(i), "연습 " + i);
		}
	}

	private void stubAiResponse(String json) {
		given(aiChatClient.complete(anyString(), anyString())).willReturn(new AiChatResponse(json, 10, "model"));
	}

	@Test
	void coach_fewerThanFiveRecordsInPeriod_returnsLockedWithoutCallingProvider() {
		Long hobbyId = createHobby(1L);
		createRecords(1L, hobbyId, 3, LocalDate.now().minusDays(2));

		AiCoachResponse response = service(enabledProperties()).coach(1L, new CoachRequest(hobbyId, "1M"));

		assertThat(response.locked()).isTrue();
		assertThat(response.requiredCount()).isEqualTo(5);
		assertThat(response.currentCount()).isEqualTo(3);
		verifyNoInteractions(aiChatClient);
	}

	@Test
	void coach_recordIdNotInInput_dropsThatTextItem() {
		Long hobbyId = createHobby(1L);
		createRecords(1L, hobbyId, 5, LocalDate.now().minusDays(4));

		stubAiResponse("{\"periodSummary\":\"기록상 꾸준했어요\","
				+ "\"changes\":[{\"text\":\"근거 없는 변화\",\"recordIds\":[99999]}],"
				+ "\"repeatedDifficulties\":[],\"flow\":[],\"questions\":[],\"directions\":[]}");

		AiCoachResponse response = service(enabledProperties()).coach(1L, new CoachRequest(hobbyId, "1M"));

		assertThat(response.changes()).isEmpty();
	}

	@Test
	void coach_forbiddenWordInSummary_throwsAiFailed() {
		Long hobbyId = createHobby(1L);
		createRecords(1L, hobbyId, 5, LocalDate.now().minusDays(4));
		stubAiResponse("{\"periodSummary\":\"실력이 늘었어요\",\"changes\":[],\"repeatedDifficulties\":[],"
				+ "\"flow\":[],\"questions\":[],\"directions\":[]}");

		assertThatThrownBy(() -> service(enabledProperties()).coach(1L, new CoachRequest(hobbyId, "1M")))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_FAILED));
		assertThat(aiResultRepository.findAll()).isEmpty();
	}

	@Test
	void coach_sameInputTwice_reusesStoredResult() {
		Long hobbyId = createHobby(1L);
		createRecords(1L, hobbyId, 5, LocalDate.now().minusDays(4));
		stubAiResponse("{\"periodSummary\":\"기록상 꾸준했어요\",\"changes\":[],\"repeatedDifficulties\":[],"
				+ "\"flow\":[],\"questions\":[],\"directions\":[]}");

		CoachService service = service(enabledProperties());
		CoachRequest request = new CoachRequest(hobbyId, "1M");
		AiCoachResponse first = service.coach(1L, request);
		AiCoachResponse second = service.coach(1L, request);

		assertThat(first.reused()).isFalse();
		assertThat(second.reused()).isTrue();
		assertThat(second.id()).isEqualTo(first.id());
		assertThat(aiResultRepository.findAll()).hasSize(1);
	}

	@Test
	void coach_hobbyIdNull_usesAllHobbies() {
		Long hobbyA = createHobby(1L);
		Long hobbyB = hobbyRepository.save(new Hobby(1L, "피아노", null)).getId();
		createRecords(1L, hobbyA, 3, LocalDate.now().minusDays(4));
		createRecords(1L, hobbyB, 2, LocalDate.now().minusDays(1));

		stubAiResponse("{\"periodSummary\":\"기록상 두 분야 모두 이어갔어요\",\"changes\":[],\"repeatedDifficulties\":[],"
				+ "\"flow\":[],\"questions\":[],\"directions\":[]}");

		AiCoachResponse response = service(enabledProperties()).coach(1L, new CoachRequest(null, "1M"));

		assertThat(response.locked()).isFalse();
		assertThat(response.recordsRead()).isEqualTo(5);
	}

	@Test
	void coach_hobbyNotOwned_throwsNotFound() {
		Long hobbyId = createHobby(2L);

		assertThatThrownBy(() -> service(enabledProperties()).coach(1L, new CoachRequest(hobbyId, "1M")))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
		verifyNoInteractions(aiChatClient);
	}

	@Test
	void submitReaction_ownResult_savesValue() {
		Long hobbyId = createHobby(1L);
		AiResult saved = aiResultRepository.save(new AiResult(1L, hobbyId, "COACH", LocalDate.now(), LocalDate.now(),
				"{\"mode\":\"NORMAL\",\"periodSummary\":\"기록상 꾸준했어요\",\"changes\":[],\"repeatedDifficulties\":[],"
						+ "\"nextAction\":null,\"comebackAction\":null,\"recordsRead\":5}",
				"[]", "c".repeat(64)));

		AiCoachResponse response = service(enabledProperties()).submitReaction(1L, saved.getId(), "TRIED");

		assertThat(response.reaction()).isEqualTo("TRIED");
	}

	private AiResult saveCoachResult(Long userId, Long hobbyId, String periodSummary, String nextActionTitle,
			String salt) {
		String next = nextActionTitle == null ? "null"
				: "{\"title\":\"" + nextActionTitle + "\",\"minutes\":10,\"reason\":\"이유\",\"recordIds\":[]}";
		return aiResultRepository.save(new AiResult(userId, hobbyId, "COACH", LocalDate.now(), LocalDate.now(),
				"{\"mode\":\"NORMAL\",\"periodSummary\":\"" + periodSummary + "\",\"changes\":[],"
						+ "\"repeatedDifficulties\":[],\"nextAction\":" + next + ",\"comebackAction\":null,"
						+ "\"recordsRead\":5}",
				"[]", salt.repeat(64)));
	}

	@Test
	void search_ownResultsOnly_newestFirst() {
		Long hobbyId = createHobby(1L);
		AiResult older = saveCoachResult(1L, hobbyId, "첫 회고", null, "d");
		saveCoachResult(2L, hobbyId, "남의 회고", null, "e");

		var result = service(enabledProperties()).search(1L, null, null, null, null, 0, 20);

		assertThat(result.items()).hasSize(1);
		assertThat(result.totalCount()).isEqualTo(1);
		assertThat(result.items().get(0).id()).isEqualTo(older.getId());
	}

	@Test
	void search_byQuery_matchesPeriodSummaryCaseInsensitive() {
		Long hobbyId = createHobby(1L);
		AiResult matching = saveCoachResult(1L, hobbyId, "기록상 꾸준히 Piano 연습했어요", null, "f");
		saveCoachResult(1L, hobbyId, "기록상 그림을 그렸어요", null, "g");

		var result = service(enabledProperties()).search(1L, null, "piano", null, null, 0, 20);

		assertThat(result.items()).hasSize(1);
		assertThat(result.items().get(0).id()).isEqualTo(matching.getId());
	}

	@Test
	void search_byQuery_matchesNextActionTitle() {
		Long hobbyId = createHobby(1L);
		AiResult matching = saveCoachResult(1L, hobbyId, "요약", "10분 스트레칭하기", "h");
		saveCoachResult(1L, hobbyId, "요약2", "책 한 쪽 읽기", "i");

		var result = service(enabledProperties()).search(1L, null, "스트레칭", null, null, 0, 20);

		assertThat(result.items()).hasSize(1);
		assertThat(result.items().get(0).id()).isEqualTo(matching.getId());
	}

	@Test
	void search_byQuery_noMatch_returnsEmpty() {
		Long hobbyId = createHobby(1L);
		saveCoachResult(1L, hobbyId, "기록상 꾸준했어요", null, "j");

		var result = service(enabledProperties()).search(1L, null, "존재하지않는검색어", null, null, 0, 20);

		assertThat(result.items()).isEmpty();
		assertThat(result.totalCount()).isEqualTo(0);
	}

	@Test
	void search_byQuery_specialCharactersDoNotThrow() {
		Long hobbyId = createHobby(1L);
		saveCoachResult(1L, hobbyId, "기록상 꾸준했어요", null, "k");

		var result = service(enabledProperties()).search(1L, null, "100%_test", null, null, 0, 20);

		assertThat(result.items()).isEmpty();
	}

	@Test
	void search_byDateRange_boundaryInclusive() {
		Long hobbyId = createHobby(1L);
		AiResult result1 = saveCoachResult(1L, hobbyId, "요약1", null, "l");
		LocalDate createdDate = result1.getCreatedAt().toLocalDate();

		var inRange = service(enabledProperties()).search(1L, null, null, createdDate, createdDate, 0, 20);
		assertThat(inRange.items()).hasSize(1);

		var beforeRange = service(enabledProperties()).search(1L, null, null, createdDate.plusDays(1), null, 0, 20);
		assertThat(beforeRange.items()).isEmpty();

		var afterRange = service(enabledProperties()).search(1L, null, null, null, createdDate.minusDays(1), 0, 20);
		assertThat(afterRange.items()).isEmpty();
	}

	@Test
	void search_byHobbyId_filtersToThatHobbyOnly() {
		Long hobbyA = createHobby(1L);
		Long hobbyB = hobbyRepository.save(new Hobby(1L, "다른 분야", null)).getId();
		AiResult inA = saveCoachResult(1L, hobbyA, "A 분야 요약", null, "m");
		saveCoachResult(1L, hobbyB, "B 분야 요약", null, "n");

		var result = service(enabledProperties()).search(1L, hobbyA, null, null, null, 0, 20);

		assertThat(result.items()).hasSize(1);
		assertThat(result.items().get(0).id()).isEqualTo(inA.getId());
		assertThat(result.items().get(0).hobbyName()).isEqualTo("기타");
	}

	@Test
	void search_hobbyIdNull_hobbyNameIsAllHobbies() {
		saveCoachResult(1L, null, "전체 분야 요약", null, "o");

		var result = service(enabledProperties()).search(1L, null, null, null, null, 0, 20);

		assertThat(result.items()).hasSize(1);
		assertThat(result.items().get(0).hobbyId()).isNull();
		assertThat(result.items().get(0).hobbyName()).isEqualTo("전체");
	}

	@Test
	void search_hobbyDeletedAfterResult_hobbyNameIsDeletedPlaceholderNotRawLabel() {
		Long hobbyId = createHobby(1L);
		saveCoachResult(1L, hobbyId, "요약", null, "o");
		hobbyRepository.deleteById(hobbyId);

		var result = service(enabledProperties()).search(1L, null, null, null, null, 0, 20);

		assertThat(result.items()).hasSize(1);
		assertThat(result.items().get(0).hobbyName()).isEqualTo("삭제된 분야");
	}

	@Test
	void search_pagination_splitsAcrossPages() {
		Long hobbyId = createHobby(1L);
		for (int i = 0; i < 5; i++) {
			saveCoachResult(1L, hobbyId, "요약" + i, null, String.valueOf((char) ('o' + i)));
		}

		var firstPage = service(enabledProperties()).search(1L, null, null, null, null, 0, 2);
		var secondPage = service(enabledProperties()).search(1L, null, null, null, null, 1, 2);

		assertThat(firstPage.items()).hasSize(2);
		assertThat(secondPage.items()).hasSize(2);
		assertThat(firstPage.totalCount()).isEqualTo(5);
		assertThat(firstPage.items().get(0).id()).isNotEqualTo(secondPage.items().get(0).id());
	}

	@Test
	void search_noResults_returnsEmptyWithZeroTotal() {
		var result = service(enabledProperties()).search(1L, null, null, null, null, 0, 20);

		assertThat(result.items()).isEmpty();
		assertThat(result.totalCount()).isEqualTo(0);
	}

	private AiResult saveCoachResultWithNextAction(Long userId, Long hobbyId, List<Long> evidenceIds) {
		String idsJson = evidenceIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
		return aiResultRepository.save(new AiResult(userId, hobbyId, "COACH", LocalDate.now(), LocalDate.now(),
				"{\"mode\":\"NORMAL\",\"periodSummary\":\"기록상 꾸준했어요\",\"changes\":[],\"repeatedDifficulties\":[],"
						+ "\"nextAction\":{\"title\":\"10분 연습하기\",\"minutes\":10,\"reason\":\"꾸준히 이어가려고\","
						+ "\"recordIds\":[" + idsJson + "]},\"comebackAction\":null,\"recordsRead\":5}",
				"[]", "s".repeat(64)));
	}

	@Test
	void simplify_success_storesSimplifiedActionAsCurrentAndResetsReaction() {
		Long hobbyId = createHobby(1L);
		ActivityRecord record = createRecord(1L, hobbyId, LocalDate.now(), "연습");
		AiResult saved = saveCoachResultWithNextAction(1L, hobbyId, List.of(record.getId()));
		saved.setReaction("TOO_HARD", java.time.LocalDateTime.now());
		aiResultRepository.save(saved);

		stubAiResponse(("{\"title\":\"1분만 해보기\",\"minutes\":1,\"reason\":\"더 쉽게 시작하려고\",\"recordIds\":[%d]}")
				.formatted(record.getId()));

		AiCoachResponse response = service(enabledProperties()).simplify(1L, saved.getId());

		assertThat(response.nextAction().title()).isEqualTo("1분만 해보기");
		assertThat(response.nextAction().minutes()).isEqualTo(1);
		assertThat(response.reaction()).isNull();
	}

	@Test
	void simplify_secondCallOnSameResult_throwsAlreadySimplified() {
		Long hobbyId = createHobby(1L);
		ActivityRecord record = createRecord(1L, hobbyId, LocalDate.now(), "연습");
		AiResult saved = saveCoachResultWithNextAction(1L, hobbyId, List.of(record.getId()));

		stubAiResponse(("{\"title\":\"1분만 해보기\",\"minutes\":1,\"reason\":\"더 쉽게 시작하려고\",\"recordIds\":[%d]}")
				.formatted(record.getId()));
		CoachService service = service(enabledProperties());
		service.simplify(1L, saved.getId());

		assertThatThrownBy(() -> service.simplify(1L, saved.getId())).isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_ALREADY_SIMPLIFIED));
	}

	@Test
	void simplify_anotherUsersResult_throwsNotFound() {
		Long hobbyId = createHobby(2L);
		ActivityRecord record = createRecord(2L, hobbyId, LocalDate.now(), "연습");
		AiResult saved = saveCoachResultWithNextAction(2L, hobbyId, List.of(record.getId()));

		assertThatThrownBy(() -> service(enabledProperties()).simplify(1L, saved.getId()))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
		verifyNoInteractions(aiChatClient);
	}

	@Test
	void simplify_dailyLimitExceeded_throwsLimitExceeded() {
		Long hobbyId = createHobby(1L);
		ActivityRecord record = createRecord(1L, hobbyId, LocalDate.now(), "연습");
		AiResult saved = saveCoachResultWithNextAction(1L, hobbyId, List.of(record.getId()));

		AiProperties properties = enabledProperties();
		properties.setSimplifyUserDailyLimit(0);

		assertThatThrownBy(() -> service(properties).simplify(1L, saved.getId())).isInstanceOfSatisfying(
				ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_LIMIT_EXCEEDED));
		verifyNoInteractions(aiChatClient);
	}

	private void backdateCreatedAt(AiResult result, java.time.LocalDateTime createdAt) {
		ReflectionTestUtils.setField(result, "createdAt", createdAt);
		aiResultRepository.save(result);
	}

	@Test
	void cardState_freshResultNoReaction_isActive() {
		Long hobbyId = createHobby(1L);
		AiResult saved = saveCoachResultWithNextAction(1L, hobbyId, List.of());

		AiCoachResponse response = service(enabledProperties()).getById(1L, saved.getId());

		assertThat(response.cardState()).isEqualTo("ACTIVE");
	}

	@Test
	void cardState_triedToday_isCelebrate() {
		Long hobbyId = createHobby(1L);
		AiResult saved = saveCoachResultWithNextAction(1L, hobbyId, List.of());

		AiCoachResponse response = service(enabledProperties()).submitReaction(1L, saved.getId(), "TRIED");

		assertThat(response.cardState()).isEqualTo("CELEBRATE");
	}

	@Test
	void cardState_triedYesterday_isExpired() {
		Long hobbyId = createHobby(1L);
		AiResult saved = saveCoachResultWithNextAction(1L, hobbyId, List.of());
		saved.setReaction("TRIED", java.time.LocalDateTime.now().minusDays(1));
		aiResultRepository.save(saved);

		AiCoachResponse response = service(enabledProperties()).getById(1L, saved.getId());

		assertThat(response.cardState()).isEqualTo("EXPIRED");
	}

	@Test
	void cardState_restTodayToday_isResting() {
		Long hobbyId = createHobby(1L);
		AiResult saved = saveCoachResultWithNextAction(1L, hobbyId, List.of());

		AiCoachResponse response = service(enabledProperties()).submitReaction(1L, saved.getId(), "REST_TODAY");

		assertThat(response.cardState()).isEqualTo("RESTING");
	}

	@Test
	void cardState_restTodayYesterday_isActiveAgain() {
		Long hobbyId = createHobby(1L);
		AiResult saved = saveCoachResultWithNextAction(1L, hobbyId, List.of());
		saved.setReaction("REST_TODAY", java.time.LocalDateTime.now().minusDays(1));
		aiResultRepository.save(saved);

		AiCoachResponse response = service(enabledProperties()).getById(1L, saved.getId());

		assertThat(response.cardState()).isEqualTo("ACTIVE");
	}

	@Test
	void cardState_createdOverSevenDaysAgo_isExpired() {
		Long hobbyId = createHobby(1L);
		AiResult saved = saveCoachResultWithNextAction(1L, hobbyId, List.of());
		backdateCreatedAt(saved, java.time.LocalDateTime.now().minusDays(8));

		AiCoachResponse response = service(enabledProperties()).getById(1L, saved.getId());

		assertThat(response.cardState()).isEqualTo("EXPIRED");
	}

	@Test
	void cardState_createdExactlySevenDaysAgo_isStillActive() {
		Long hobbyId = createHobby(1L);
		AiResult saved = saveCoachResultWithNextAction(1L, hobbyId, List.of());
		backdateCreatedAt(saved, java.time.LocalDateTime.now().minusDays(7));

		AiCoachResponse response = service(enabledProperties()).getById(1L, saved.getId());

		assertThat(response.cardState()).isEqualTo("ACTIVE");
	}

	@Test
	void cardState_tooHardAfterSimplify_isResting() {
		Long hobbyId = createHobby(1L);
		ActivityRecord record = createRecord(1L, hobbyId, LocalDate.now(), "연습");
		AiResult saved = saveCoachResultWithNextAction(1L, hobbyId, List.of(record.getId()));
		stubAiResponse(("{\"title\":\"1분만 해보기\",\"minutes\":1,\"reason\":\"더 쉽게 시작하려고\",\"recordIds\":[%d]}")
				.formatted(record.getId()));
		CoachService service = service(enabledProperties());
		service.simplify(1L, saved.getId());

		AiCoachResponse response = service.submitReaction(1L, saved.getId(), "TOO_HARD");

		assertThat(response.cardState()).isEqualTo("RESTING");
	}

	@Test
	void simplify_bannedWordInOutput_throwsAiFailed() {
		Long hobbyId = createHobby(1L);
		ActivityRecord record = createRecord(1L, hobbyId, LocalDate.now(), "연습");
		AiResult saved = saveCoachResultWithNextAction(1L, hobbyId, List.of(record.getId()));

		stubAiResponse(("{\"title\":\"실력이 늘었으니 1분만\",\"minutes\":1,\"reason\":\"더 쉽게\",\"recordIds\":[%d]}")
				.formatted(record.getId()));

		assertThatThrownBy(() -> service(enabledProperties()).simplify(1L, saved.getId()))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_FAILED));
	}

	private static final String SAMPLE_COACH_JSON = "{\"periodSummary\":\"기록상 꾸준히 이어갔어요\",\"changes\":[],"
			+ "\"repeatedDifficulties\":[],\"flow\":[],\"questions\":[],\"directions\":[]}";

	@Test
	void generateAuto_aiDisabledForUser_returnsDisabledWithoutCallingProvider() {
		Long userId = createUser(false);
		Long hobbyId = createHobby(userId);
		createRecords(userId, hobbyId, 5, LocalDate.now().minusDays(4));

		AiCoachAutoResponse response = service(enabledProperties()).generateAuto(userId);

		assertThat(response.status()).isEqualTo(AiCoachAutoResponse.Status.DISABLED);
		verifyNoInteractions(aiChatClient);
		assertThat(aiResultRepository.findAll()).isEmpty();
	}

	@Test
	void generateAuto_aiDisabledServerWide_returnsDisabled() {
		Long userId = createUser(true);
		Long hobbyId = createHobby(userId);
		createRecords(userId, hobbyId, 5, LocalDate.now().minusDays(4));

		AiCoachAutoResponse response = service(new AiProperties()).generateAuto(userId);

		assertThat(response.status()).isEqualTo(AiCoachAutoResponse.Status.DISABLED);
		verifyNoInteractions(aiChatClient);
	}

	@Test
	void generateAuto_firstCoach_fewerThan5TotalRecords_returnsNotEligible() {
		Long userId = createUser(true);
		Long hobbyId = createHobby(userId);
		createRecords(userId, hobbyId, 4, LocalDate.now().minusDays(3));

		AiCoachAutoResponse response = service(enabledProperties()).generateAuto(userId);

		assertThat(response.status()).isEqualTo(AiCoachAutoResponse.Status.NOT_ELIGIBLE);
		assertThat(response.newRecordsSinceLast()).isEqualTo(4);
		assertThat(response.hasAnyCoach()).isFalse();
		verifyNoInteractions(aiChatClient);
	}

	@Test
	void generateAuto_firstCoach_5TotalRecords_generates() {
		Long userId = createUser(true);
		Long hobbyId = createHobby(userId);
		createRecords(userId, hobbyId, 5, LocalDate.now().minusDays(4));
		stubAiResponse(SAMPLE_COACH_JSON);

		AiCoachAutoResponse response = service(enabledProperties()).generateAuto(userId);

		assertThat(response.status()).isEqualTo(AiCoachAutoResponse.Status.GENERATED);
		assertThat(response.result().isNew()).isTrue();
		assertThat(aiResultRepository.findAll()).hasSize(1);
		assertThat(aiResultRepository.findAll().get(0).isAuto()).isTrue();
	}

	@Test
	void generateAuto_afterManualCoach_fewerThan5NewRecords_returnsNotEligible() throws InterruptedException {
		Long userId = createUser(true);
		Long hobbyId = createHobby(userId);
		createRecords(userId, hobbyId, 5, LocalDate.now().minusDays(10));
		stubAiResponse(SAMPLE_COACH_JSON);
		service(enabledProperties()).coach(userId, new CoachRequest(null, "1M"));
		Thread.sleep(10);
		createRecords(userId, hobbyId, 4, LocalDate.now().minusDays(3));

		AiCoachAutoResponse response = service(enabledProperties()).generateAuto(userId);

		assertThat(response.status()).isEqualTo(AiCoachAutoResponse.Status.NOT_ELIGIBLE);
		assertThat(response.newRecordsSinceLast()).isEqualTo(4);
		assertThat(response.hasAnyCoach()).isTrue();
		assertThat(aiResultRepository.findAll()).hasSize(1); // 수동 회고 하나만, 자동은 안 만들어짐
	}

	@Test
	void generateAuto_afterManualCoach_5NewRecords_generates() throws InterruptedException {
		Long userId = createUser(true);
		Long hobbyId = createHobby(userId);
		createRecords(userId, hobbyId, 5, LocalDate.now().minusDays(10));
		stubAiResponse(SAMPLE_COACH_JSON);
		AiCoachResponse manual = service(enabledProperties()).coach(userId,
				new CoachRequest(null, "1M"));
		Thread.sleep(10);
		createRecords(userId, hobbyId, 5, LocalDate.now().minusDays(4));

		AiCoachAutoResponse response = service(enabledProperties()).generateAuto(userId);

		assertThat(response.status()).isEqualTo(AiCoachAutoResponse.Status.GENERATED);
		assertThat(response.result().id()).isNotEqualTo(manual.id());
		assertThat(aiResultRepository.findAll()).hasSize(2);
	}

	@Test
	void generateAuto_calledAgainImmediately_returnsExistsNotGeneratedTwice() {
		Long userId = createUser(true);
		Long hobbyId = createHobby(userId);
		createRecords(userId, hobbyId, 5, LocalDate.now().minusDays(4));
		stubAiResponse(SAMPLE_COACH_JSON);
		CoachService service = service(enabledProperties());
		AiCoachAutoResponse first = service.generateAuto(userId);

		AiCoachAutoResponse second = service.generateAuto(userId);

		assertThat(first.status()).isEqualTo(AiCoachAutoResponse.Status.GENERATED);
		assertThat(second.status()).isIn(AiCoachAutoResponse.Status.EXISTS, AiCoachAutoResponse.Status.NOT_ELIGIBLE);
		assertThat(aiResultRepository.findAll()).hasSize(1);
	}

	@Test
	void generateAuto_autoLimitExceeded_returnsLimitQuietly() {
		Long userId = createUser(true);
		Long hobbyId = createHobby(userId);
		createRecords(userId, hobbyId, 5, LocalDate.now().minusDays(4));
		AiProperties properties = enabledProperties();
		properties.setAutoUserDailyLimit(0);

		AiCoachAutoResponse response = service(properties).generateAuto(userId);

		assertThat(response.status()).isEqualTo(AiCoachAutoResponse.Status.LIMIT);
		assertThat(aiResultRepository.findAll()).isEmpty();
	}

	@Test
	void generateAuto_otherUsersRecordsNotCounted() {
		Long userId = createUser(true);
		Long hobbyId = createHobby(userId);
		createRecords(userId, hobbyId, 2, LocalDate.now().minusDays(1));
		Long otherUserId = createUser(true);
		Long otherHobbyId = createHobby(otherUserId);
		createRecords(otherUserId, otherHobbyId, 5, LocalDate.now().minusDays(4));

		AiCoachAutoResponse response = service(enabledProperties()).generateAuto(userId);

		assertThat(response.status()).isEqualTo(AiCoachAutoResponse.Status.NOT_ELIGIBLE);
		assertThat(response.newRecordsSinceLast()).isEqualTo(2);
	}

	@Test
	void markViewed_setsViewedAtOnceAndIsNewBecomesFalse() {
		Long userId = createUser(true);
		Long hobbyId = createHobby(userId);
		createRecords(userId, hobbyId, 5, LocalDate.now().minusDays(4));
		stubAiResponse("{\"periodSummary\":\"기록상 꾸준히 이어갔어요\",\"changes\":[],\"repeatedDifficulties\":[],"
				+ "\"flow\":[],\"questions\":[],\"directions\":[]}");
		CoachService service = service(enabledProperties());
		AiCoachResponse created = service.generateAuto(userId).result();
		assertThat(created.isNew()).isTrue();

		AiCoachResponse viewed = service.markViewed(userId, created.id());

		assertThat(viewed.isNew()).isFalse();
	}

	private List<Long> createRecordIds(Long userId, Long hobbyId, int count, LocalDate start) {
		List<Long> ids = new java.util.ArrayList<>();
		for (int i = 0; i < count; i++) {
			ids.add(createRecord(userId, hobbyId, start.plusDays(i), "연습 " + i).getId());
		}
		return ids;
	}

	private String reflectionJson(List<Long> ids) {
		long a = ids.get(0);
		long b = ids.get(1);
		return ("{\"periodSummary\":\"기록상 꾸준히 이어갔어요\","
				+ "\"flow\":[{\"text\":\"처음엔 코드 전환이 느렸어요\",\"recordIds\":[%d]},"
				+ "{\"text\":\"뒤로 갈수록 이어서 쳤어요\",\"recordIds\":[%d]}],"
				+ "\"changes\":[{\"text\":\"새 곡을 쳐 봤어요\",\"recordIds\":[%d]}],"
				+ "\"repeatedDifficulties\":[{\"text\":\"코드 전환이 자주 걸렸어요\",\"recordIds\":[%d,%d]}],"
				+ "\"resolvedDifficulties\":[{\"text\":\"박자는 뒤에 따로 적지 않았어요\",\"recordIds\":[%d]}],"
				+ "\"questions\":[{\"text\":\"코드 전환이 걸릴 땐 어떤 느낌이었나요?\",\"recordIds\":[%d]},"
				+ "{\"text\":\"요즘 가장 손이 가는 곡은 무엇인가요?\",\"recordIds\":[%d]}],"
				+ "\"directions\":[{\"direction\":\"어려웠던 부분을 천천히 다시 보는 쪽\",\"recordIds\":[%d],"
				+ "\"steps\":[\"먼저 어려웠다고 적은 부분만 천천히 해 봐요\"]},"
				+ "{\"direction\":\"새 곡을 조금씩 넓히는 쪽\",\"recordIds\":[%d],"
				+ "\"steps\":[\"먼저 어려웠다고 적은 곡 하나만 골라 봐요\"]}]}")
				.formatted(a, b, b, a, b, b, a, b, a, b);
	}

	@Test
	void coach_newLayout_hasSectionsStatsAndNoAction() {
		Long hobbyId = createHobby(1L);
		List<Long> ids = createRecordIds(1L, hobbyId, 5, LocalDate.now().minusDays(4));
		stubAiResponse(reflectionJson(ids));

		AiCoachResponse response = service(enabledProperties()).coach(1L, new CoachRequest(hobbyId, "1M"));

		assertThat(response.schemaVersion()).isEqualTo(2);
		assertThat(response.mode()).isEqualTo("NORMAL");
		assertThat(response.nextAction()).isNull();
		assertThat(response.comebackAction()).isNull();
		assertThat(response.flow()).hasSize(2);
		assertThat(response.flow().get(0).evidence()).hasSize(1);
		assertThat(response.questions()).hasSize(2);
		assertThat(response.directions()).hasSize(2);
		assertThat(response.resolvedDifficulties()).hasSize(1);
		// 숫자는 AI가 아니라 서버가 센다: 5개 기록, 5일, 20분씩 100분, 느낌은 모두 뿌듯함
		assertThat(response.stats().recordCount()).isEqualTo(5);
		assertThat(response.stats().recordedDays()).isEqualTo(5);
		assertThat(response.stats().totalMinutes()).isEqualTo(100);
		assertThat(response.stats().feelings()).hasSize(1);
		assertThat(response.stats().feelings().get(0).label()).isEqualTo("뿌듯함");
		assertThat(response.stats().feelings().get(0).count()).isEqualTo(5);
	}

	@Test
	void coach_newLayout_dropsItemsWithoutValidEvidenceOrWithForbiddenOrUrgentWords() {
		Long hobbyId = createHobby(1L);
		List<Long> ids = createRecordIds(1L, hobbyId, 5, LocalDate.now().minusDays(4));
		long a = ids.get(0);
		stubAiResponse(("{\"periodSummary\":\"기록상 꾸준했어요\","
				+ "\"flow\":[{\"text\":\"근거 없는 흐름\",\"recordIds\":[99999]},"
				+ "{\"text\":\"실력이 늘었어요\",\"recordIds\":[%d]},{\"text\":\"괜찮은 흐름\",\"recordIds\":[%d]}],"
				+ "\"changes\":[],\"repeatedDifficulties\":[],"
				+ "\"questions\":[{\"text\":\"꼭 답해 보세요\",\"recordIds\":[%d]},"
				+ "{\"text\":\"빨리 정해야 하나요?\",\"recordIds\":[%d]},{\"text\":\"무엇이 가장 즐거웠나요?\",\"recordIds\":[%d]}],"
				+ "\"directions\":[{\"direction\":\"반드시 이쪽\",\"recordIds\":[%d],"
				+ "\"steps\":[\"먼저 어려웠다고 적은 부분을 봐요\"]}]}").formatted(a, a, a, a, a, a));

		AiCoachResponse response = service(enabledProperties()).coach(1L, new CoachRequest(hobbyId, "1M"));

		assertThat(response.flow()).extracting(item -> item.text()).containsExactly("괜찮은 흐름");
		assertThat(response.questions()).extracting(item -> item.text()).containsExactly("무엇이 가장 즐거웠나요?");
		assertThat(response.directions()).isEmpty();
	}

	@Test
	void coach_newLayout_capsSectionSizes() {
		Long hobbyId = createHobby(1L);
		List<Long> ids = createRecordIds(1L, hobbyId, 5, LocalDate.now().minusDays(4));
		long a = ids.get(0);
		String item = "{\"text\":\"문장 %s\",\"recordIds\":[" + a + "]}";
		StringBuilder many = new StringBuilder();
		for (int i = 0; i < 8; i++) {
			many.append(i == 0 ? "" : ",").append(item.formatted(i));
		}
		String directionItem = "{\"direction\":\"방향 %s\",\"recordIds\":[" + a
				+ "],\"steps\":[\"먼저 어려웠다고 적은 부분을 봐요\"]}";
		StringBuilder manyDirections = new StringBuilder();
		for (int i = 0; i < 8; i++) {
			manyDirections.append(i == 0 ? "" : ",").append(directionItem.formatted(i));
		}
		stubAiResponse("{\"periodSummary\":\"기록상 꾸준했어요\",\"flow\":[" + many + "],\"changes\":[],"
				+ "\"repeatedDifficulties\":[],\"questions\":[" + many + "],\"directions\":[" + manyDirections + "]}");

		AiCoachResponse response = service(enabledProperties()).coach(1L, new CoachRequest(hobbyId, "1M"));

		assertThat(response.flow()).hasSize(5);
		assertThat(response.questions()).hasSize(2);
		assertThat(response.directions()).hasSize(3);
	}

	@Test
	void coach_comebackMode_keepsModeWithoutAction() {
		Long hobbyId = createHobby(1L);
		List<Long> ids = createRecordIds(1L, hobbyId, 5, LocalDate.now().minusDays(20));
		stubAiResponse(reflectionJson(ids));

		AiCoachResponse response = service(enabledProperties()).coach(1L, new CoachRequest(hobbyId, "1M"));

		assertThat(response.mode()).isEqualTo("COMEBACK");
		assertThat(response.comebackAction()).isNull();
		assertThat(response.nextAction()).isNull();
		assertThat(response.flow()).isNotEmpty();
	}

	@Test
	void coach_newLayout_otherUsersRecordIdsAreDropped() {
		Long hobbyId = createHobby(1L);
		createRecordIds(1L, hobbyId, 5, LocalDate.now().minusDays(4));
		Long otherHobby = createHobby(2L);
		long othersId = createRecord(2L, otherHobby, LocalDate.now(), "남의 기록").getId();
		stubAiResponse(("{\"periodSummary\":\"기록상 꾸준했어요\",\"flow\":[{\"text\":\"남의 기록 흐름\",\"recordIds\":[%d]}],"
				+ "\"changes\":[],\"repeatedDifficulties\":[],\"questions\":[],\"directions\":[]}").formatted(othersId));

		AiCoachResponse response = service(enabledProperties()).coach(1L, new CoachRequest(hobbyId, "1M"));

		assertThat(response.flow()).isEmpty();
	}

	@Test
	void coach_missingNewSections_throwsAiFailed() {
		Long hobbyId = createHobby(1L);
		createRecords(1L, hobbyId, 5, LocalDate.now().minusDays(4));
		stubAiResponse("{\"periodSummary\":\"기록상 꾸준했어요\",\"changes\":[],\"repeatedDifficulties\":[],"
				+ "\"action\":{\"title\":\"행동\",\"minutes\":5,\"reason\":\"이유\",\"recordIds\":[]}}");

		assertThatThrownBy(() -> service(enabledProperties()).coach(1L, new CoachRequest(hobbyId, "1M")))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_FAILED));
		assertThat(aiResultRepository.findAll()).isEmpty();
	}

	@Test
	void legacyResult_stillReadsWithoutNewSections() {
		Long hobbyId = createHobby(1L);
		AiResult saved = saveCoachResultWithNextAction(1L, hobbyId, List.of());

		AiCoachResponse response = service(enabledProperties()).getById(1L, saved.getId());

		assertThat(response.schemaVersion()).isNull();
		assertThat(response.flow()).isNull();
		assertThat(response.stats()).isNull();
		assertThat(response.nextAction()).isNotNull();
	}

	@Test
	void search_byQuery_matchesNewSectionText() {
		Long hobbyId = createHobby(1L);
		List<Long> ids = createRecordIds(1L, hobbyId, 5, LocalDate.now().minusDays(4));
		stubAiResponse(reflectionJson(ids));
		CoachService service = service(enabledProperties());
		service.coach(1L, new CoachRequest(hobbyId, "1M"));

		assertThat(service.search(1L, null, "손이 가는 곡", null, null, 0, 20).items()).hasSize(1);
		assertThat(service.search(2L, null, "손이 가는 곡", null, null, 0, 20).items()).isEmpty();
	}

	private static String directionJson(String direction, String ids, String steps) {
		return "{\"direction\":\"" + direction + "\",\"recordIds\":[" + ids + "],\"steps\":[" + steps + "]}";
	}

	private AiCoachResponse coachWithDirections(Long hobbyId, String directionsJson) {
		stubAiResponse("{\"periodSummary\":\"기록상 꾸준했어요\",\"flow\":[],\"changes\":[],\"repeatedDifficulties\":[],"
				+ "\"questions\":[],\"directions\":" + directionsJson + "}");
		return service(enabledProperties()).coach(1L, new CoachRequest(hobbyId, "1M"));
	}

	@Test
	void coach_directionPlans_keepEvidenceUpToTwoAndStepsInOrder() {
		Long hobbyId = createHobby(1L);
		List<Long> ids = createRecordIds(1L, hobbyId, 5, LocalDate.now().minusDays(4));
		String idList = ids.get(0) + "," + ids.get(1) + "," + ids.get(2);

		AiCoachResponse response = coachWithDirections(hobbyId, "[" + directionJson("어려웠던 부분을 천천히 다시 보는 쪽", idList,
				"\"먼저 어려웠다고 적은 부분만 천천히 해 봐요\",\"그다음 어려웠다고 적은 두 부분을 이어 봐요\"") + "]");

		assertThat(response.directionPlans()).hasSize(1);
		assertThat(response.directionPlans().get(0).evidence()).hasSize(2);
		assertThat(response.directionPlans().get(0).steps()).containsExactly("먼저 어려웠다고 적은 부분만 천천히 해 봐요",
				"그다음 어려웠다고 적은 두 부분을 이어 봐요");
		assertThat(response.directions()).extracting(item -> item.text()).containsExactly("어려웠던 부분을 천천히 다시 보는 쪽");
	}

	@Test
	void coach_directionPlans_dropOnlyBadOnesAndKeepTheRest() {
		Long hobbyId = createHobby(1L);
		List<Long> ids = createRecordIds(1L, hobbyId, 5, LocalDate.now().minusDays(4));
		String id = String.valueOf(ids.get(0));
		String goodStep = "\"먼저 어려웠다고 적은 부분만 해 봐요\"";

		AiCoachResponse response = coachWithDirections(hobbyId, "["
				+ directionJson("근거 없는 방향", "99999", goodStep) + ","
				+ directionJson("시작 제안이 없는 방향", id, "") + ","
				+ directionJson("기한이 든 방향", id, "\"먼저 어려웠다고 적은 부분을 이번 주까지 해 봐요\"") + ","
				+ directionJson("입력 낱말이 없는 방향", id, "\"먼저 물을 마시고 쉬어 봐요\"") + ","
				+ directionJson("반드시 이쪽", id, goodStep) + ","
				+ directionJson("남는 방향", id, goodStep) + "]");

		assertThat(response.directionPlans()).extracting(plan -> plan.direction()).containsExactly("남는 방향");
	}

	@Test
	void coach_directionPlans_otherUsersRecordIdIsDropped() {
		Long hobbyId = createHobby(1L);
		createRecordIds(1L, hobbyId, 5, LocalDate.now().minusDays(4));
		Long otherHobby = createHobby(2L);
		long othersId = createRecord(2L, otherHobby, LocalDate.now(), "남의 기록").getId();

		AiCoachResponse response = coachWithDirections(hobbyId,
				"[" + directionJson("남의 근거 방향", String.valueOf(othersId), "\"먼저 어려웠다고 적은 부분을 봐요\"") + "]");

		assertThat(response.directionPlans()).isEmpty();
	}

	@Test
	void coach_allDirectionsDropped_resultStillSavedWithEmptyDirectionPlans() {
		Long hobbyId = createHobby(1L);
		createRecordIds(1L, hobbyId, 5, LocalDate.now().minusDays(4));

		AiCoachResponse response = coachWithDirections(hobbyId, "[]");

		assertThat(response.schemaVersion()).isEqualTo(2);
		assertThat(response.directionPlans()).isEmpty();
		assertThat(response.directions()).isEmpty();
	}

	@Test
	void coachResultWithoutDirectionPlans_stillShowsDirectionSentences() {
		Long hobbyId = createHobby(1L);
		AiResult saved = aiResultRepository.save(new AiResult(1L, hobbyId, "COACH", LocalDate.now(), LocalDate.now(),
				"{\"mode\":\"NORMAL\",\"periodSummary\":\"기록상 꾸준했어요\",\"changes\":[],\"repeatedDifficulties\":[],"
						+ "\"nextAction\":null,\"comebackAction\":null,\"recordsRead\":5,\"flow\":[],"
						+ "\"resolvedDifficulties\":[],\"questions\":[],"
						+ "\"directions\":[{\"text\":\"천천히 다시 보는 쪽\",\"recordIds\":[]}],\"stats\":null}",
				"[]", "z".repeat(64)));

		AiCoachResponse response = service(enabledProperties()).getById(1L, saved.getId());

		assertThat(response.schemaVersion()).isEqualTo(2);
		assertThat(response.directionPlans()).isNull();
		assertThat(response.directions()).hasSize(1);
	}
}
