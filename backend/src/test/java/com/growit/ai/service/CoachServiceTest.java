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
import com.growit.ai.dto.AiCoachResponse;
import com.growit.ai.dto.CoachRequest;
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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

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
		return new CoachService(activityRecordRepository, hobbyRepository, aiResultRepository, callService, validator,
				objectMapper);
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
	void coach_normalMode_producesNextActionWithinRange() {
		Long hobbyId = createHobby(1L);
		createRecords(1L, hobbyId, 5, LocalDate.now().minusDays(4));

		stubAiResponse("{\"periodSummary\":\"기록상 꾸준히 이어갔어요\",\"changes\":[],\"repeatedDifficulties\":[],"
				+ "\"action\":{\"title\":\"코드 전환 3번 해보기\",\"minutes\":5,\"reason\":\"짧게 이어가기 좋아요\",\"recordIds\":[]}}");

		AiCoachResponse response = service(enabledProperties()).coach(1L, new CoachRequest(hobbyId, "1M"));

		assertThat(response.locked()).isFalse();
		assertThat(response.mode()).isEqualTo("NORMAL");
		assertThat(response.nextAction()).isNotNull();
		assertThat(response.nextAction().minutes()).isBetween(5, 10);
		assertThat(response.comebackAction()).isNull();
		assertThat(response.recordsRead()).isEqualTo(5);
	}

	@Test
	void coach_lastRecordSevenDaysAgo_comebackModeClampsMinutesToComebackRange() {
		Long hobbyId = createHobby(1L);
		createRecords(1L, hobbyId, 5, LocalDate.now().minusDays(20));

		stubAiResponse("{\"periodSummary\":\"기록상 잠시 쉬었어요\",\"changes\":[],\"repeatedDifficulties\":[],"
				+ "\"action\":{\"title\":\"한 줄만 적어보기\",\"minutes\":20,\"reason\":\"가볍게 시작하기 좋아요\",\"recordIds\":[]}}");

		AiCoachResponse response = service(enabledProperties()).coach(1L, new CoachRequest(hobbyId, "1M"));

		assertThat(response.mode()).isEqualTo("COMEBACK");
		assertThat(response.comebackAction()).isNotNull();
		assertThat(response.comebackAction().minutes()).isBetween(1, 5);
		assertThat(response.nextAction()).isNull();
	}

	@Test
	void coach_recordIdNotInInput_dropsThatTextItem() {
		Long hobbyId = createHobby(1L);
		createRecords(1L, hobbyId, 5, LocalDate.now().minusDays(4));

		stubAiResponse("{\"periodSummary\":\"기록상 꾸준했어요\","
				+ "\"changes\":[{\"text\":\"근거 없는 변화\",\"recordIds\":[99999]}],"
				+ "\"repeatedDifficulties\":[],\"action\":{\"title\":\"행동\",\"minutes\":5,\"reason\":\"이유\",\"recordIds\":[]}}");

		AiCoachResponse response = service(enabledProperties()).coach(1L, new CoachRequest(hobbyId, "1M"));

		assertThat(response.changes()).isEmpty();
	}

	@Test
	void coach_forbiddenWordInSummary_throwsAiFailed() {
		Long hobbyId = createHobby(1L);
		createRecords(1L, hobbyId, 5, LocalDate.now().minusDays(4));
		stubAiResponse("{\"periodSummary\":\"실력이 늘었어요\",\"changes\":[],\"repeatedDifficulties\":[],"
				+ "\"action\":{\"title\":\"행동\",\"minutes\":5,\"reason\":\"이유\",\"recordIds\":[]}}");

		assertThatThrownBy(() -> service(enabledProperties()).coach(1L, new CoachRequest(hobbyId, "1M")))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_FAILED));
		assertThat(aiResultRepository.findAll()).isEmpty();
	}

	@Test
	void coach_sameInputTwice_reusesStoredResult() {
		Long hobbyId = createHobby(1L);
		createRecords(1L, hobbyId, 5, LocalDate.now().minusDays(4));
		stubAiResponse("{\"periodSummary\":\"기록상 꾸준했어요\",\"changes\":[],\"repeatedDifficulties\":[],"
				+ "\"action\":{\"title\":\"행동\",\"minutes\":5,\"reason\":\"이유\",\"recordIds\":[]}}");

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
				+ "\"action\":{\"title\":\"행동\",\"minutes\":5,\"reason\":\"이유\",\"recordIds\":[]}}");

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

	@Test
	void history_ownResultsOnly_newestFirst() {
		Long hobbyId = createHobby(1L);
		AiResult older = aiResultRepository.save(new AiResult(1L, hobbyId, "COACH", LocalDate.now(), LocalDate.now(),
				"{\"mode\":\"NORMAL\",\"periodSummary\":\"첫 회고\",\"changes\":[],\"repeatedDifficulties\":[],"
						+ "\"nextAction\":null,\"comebackAction\":null,\"recordsRead\":5}",
				"[]", "d".repeat(64)));
		aiResultRepository.save(new AiResult(2L, hobbyId, "COACH", LocalDate.now(), LocalDate.now(),
				"{\"mode\":\"NORMAL\",\"periodSummary\":\"남의 회고\",\"changes\":[],\"repeatedDifficulties\":[],"
						+ "\"nextAction\":null,\"comebackAction\":null,\"recordsRead\":5}",
				"[]", "e".repeat(64)));

		var history = service(enabledProperties()).history(1L, null, 0, 20);

		assertThat(history).hasSize(1);
		assertThat(history.get(0).id()).isEqualTo(older.getId());
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
}
