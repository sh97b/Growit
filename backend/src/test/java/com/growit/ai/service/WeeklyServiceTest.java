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
import com.growit.ai.dto.AiViewResponse;
import com.growit.ai.dto.WeeklyRequest;
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
class WeeklyServiceTest {

	private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

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

	private WeeklyService service(AiProperties properties) {
		AiUsageService usageService = new AiUsageService(aiLogRepository, properties);
		AiCallService callService = new AiCallService(properties, aiChatClient, aiLogRepository, usageService,
				objectMapper);
		AiResponseValidator validator = new AiResponseValidator(objectMapper);
		return new WeeklyService(activityRecordRepository, hobbyRepository, aiResultRepository, callService,
				validator, objectMapper);
	}

	private Long createHobby(Long userId) {
		return hobbyRepository.save(new Hobby(userId, "기타", null)).getId();
	}

	private ActivityRecord createRecord(Long userId, Long hobbyId, LocalDate date, String content) {
		return activityRecordRepository.save(
				new ActivityRecord(userId, hobbyId, date, content, 20, null, Feeling.PROUD, false));
	}

	private void stubAiResponse(String json) {
		given(aiChatClient.complete(anyString(), anyString())).willReturn(new AiChatResponse(json, 10, "model"));
	}

	@Test
	void weekly_noRecordsInWeek_returnsEmptyWithoutCallingProvider() {
		createHobby(1L);

		AiViewResponse response = service(enabledProperties()).weekly(1L, new WeeklyRequest(MONDAY, null));

		assertThat(response.empty()).isTrue();
		verifyNoInteractions(aiChatClient);
		assertThat(aiResultRepository.findAll()).isEmpty();
	}

	@Test
	void weekly_success_storesTextAndEvidence() {
		Long hobbyId = createHobby(1L);
		ActivityRecord record = createRecord(1L, hobbyId, MONDAY, "연습했다");

		stubAiResponse("{\"text\":\"이번 주엔 연습을 했어요\",\"evidenceIds\":[" + record.getId() + "]}");

		AiViewResponse response = service(enabledProperties()).weekly(1L, new WeeklyRequest(MONDAY, hobbyId));

		assertThat(response.empty()).isFalse();
		assertThat(response.reused()).isFalse();
		assertThat(response.text()).isEqualTo("이번 주엔 연습을 했어요");
		assertThat(response.displayText()).isEqualTo("이번 주엔 연습을 했어요");
		assertThat(response.evidence()).hasSize(1);
		assertThat(aiResultRepository.findAll()).hasSize(1);
	}

	@Test
	void weekly_sameInputTwice_reusesStoredResult() {
		Long hobbyId = createHobby(1L);
		ActivityRecord record = createRecord(1L, hobbyId, MONDAY, "연습했다");
		stubAiResponse("{\"text\":\"이번 주엔 연습을 했어요\",\"evidenceIds\":[" + record.getId() + "]}");

		WeeklyService service = service(enabledProperties());
		WeeklyRequest request = new WeeklyRequest(MONDAY, hobbyId);
		AiViewResponse first = service.weekly(1L, request);
		AiViewResponse second = service.weekly(1L, request);

		assertThat(first.reused()).isFalse();
		assertThat(second.reused()).isTrue();
		assertThat(second.id()).isEqualTo(first.id());
		assertThat(aiResultRepository.findAll()).hasSize(1);
	}

	@Test
	void weekly_recordEditedAfterCache_changesCacheKeyAndCallsAgain() {
		Long hobbyId = createHobby(1L);
		ActivityRecord record = createRecord(1L, hobbyId, MONDAY, "연습했다");
		stubAiResponse("{\"text\":\"이번 주엔 연습을 했어요\",\"evidenceIds\":[" + record.getId() + "]}");

		WeeklyService service = service(enabledProperties());
		WeeklyRequest request = new WeeklyRequest(MONDAY, hobbyId);
		service.weekly(1L, request);

		record.setContent("연습을 더 했다");
		activityRecordRepository.saveAndFlush(record);
		stubAiResponse("{\"text\":\"이번 주엔 연습을 더 했어요\",\"evidenceIds\":[" + record.getId() + "]}");

		AiViewResponse second = service.weekly(1L, request);

		assertThat(second.reused()).isFalse();
		assertThat(second.text()).isEqualTo("이번 주엔 연습을 더 했어요");
		assertThat(aiResultRepository.findAll()).hasSize(2);
	}

	@Test
	void weekly_evidenceIdNotInWeek_dropsThatIdOnly() {
		Long hobbyId = createHobby(1L);
		ActivityRecord record = createRecord(1L, hobbyId, MONDAY, "연습했다");

		stubAiResponse("{\"text\":\"이번 주엔 연습을 했어요\",\"evidenceIds\":[99999]}");

		AiViewResponse response = service(enabledProperties()).weekly(1L, new WeeklyRequest(MONDAY, hobbyId));

		assertThat(response.text()).isEqualTo("이번 주엔 연습을 했어요");
		assertThat(response.evidence()).isEmpty();
	}

	@Test
	void weekly_forbiddenWordInText_throwsAiFailed() {
		Long hobbyId = createHobby(1L);
		createRecord(1L, hobbyId, MONDAY, "연습했다");

		stubAiResponse("{\"text\":\"실력이 늘었어요\",\"evidenceIds\":[]}");

		assertThatThrownBy(() -> service(enabledProperties()).weekly(1L, new WeeklyRequest(MONDAY, hobbyId)))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_FAILED));
		assertThat(aiResultRepository.findAll()).isEmpty();
	}

	@Test
	void weekly_textOver80Chars_throwsAiFailed() {
		Long hobbyId = createHobby(1L);
		createRecord(1L, hobbyId, MONDAY, "연습했다");
		String longText = "가".repeat(81);

		stubAiResponse("{\"text\":\"" + longText + "\",\"evidenceIds\":[]}");

		assertThatThrownBy(() -> service(enabledProperties()).weekly(1L, new WeeklyRequest(MONDAY, hobbyId)))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_FAILED));
	}

	@Test
	void weekly_notMonday_throwsValidationError() {
		assertThatThrownBy(
				() -> service(enabledProperties()).weekly(1L, new WeeklyRequest(MONDAY.plusDays(1), null)))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void weekly_hobbyNotOwned_throwsNotFound() {
		Long hobbyId = createHobby(2L);

		assertThatThrownBy(() -> service(enabledProperties()).weekly(1L, new WeeklyRequest(MONDAY, hobbyId)))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
		verifyNoInteractions(aiChatClient);
	}

	@Test
	void weekly_separatesDataByUser() {
		Long hobbyId1 = createHobby(1L);
		Long hobbyId2 = createHobby(2L);
		createRecord(2L, hobbyId2, MONDAY, "다른 사용자 기록");

		AiViewResponse response = service(enabledProperties()).weekly(1L, new WeeklyRequest(MONDAY, hobbyId1));

		assertThat(response.empty()).isTrue();
		verifyNoInteractions(aiChatClient);
	}

	@Test
	void weekly_userLimitExceeded_throwsLimitExceeded() {
		Long hobbyId = createHobby(1L);
		createRecord(1L, hobbyId, MONDAY, "연습했다");
		AiProperties properties = enabledProperties();
		properties.setViewUserDailyLimit(0);

		assertThatThrownBy(() -> service(properties).weekly(1L, new WeeklyRequest(MONDAY, hobbyId)))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_LIMIT_EXCEEDED));
	}

	@Test
	void editText_savesEditedTextAndFeedback() {
		Long hobbyId = createHobby(1L);
		ActivityRecord record = createRecord(1L, hobbyId, MONDAY, "연습했다");
		stubAiResponse("{\"text\":\"이번 주엔 연습을 했어요\",\"evidenceIds\":[" + record.getId() + "]}");
		AiViewResponse created = service(enabledProperties()).weekly(1L, new WeeklyRequest(MONDAY, hobbyId));

		AiViewResponse edited = service(enabledProperties()).editText(1L, created.id(), "내가 고친 문장이에요");

		assertThat(edited.editedText()).isEqualTo("내가 고친 문장이에요");
		assertThat(edited.displayText()).isEqualTo("내가 고친 문장이에요");
		assertThat(edited.text()).isEqualTo("이번 주엔 연습을 했어요");
		assertThat(edited.feedback()).isEqualTo("EDITED");
	}

	@Test
	void submitFeedback_notOwner_throwsNotFound() {
		assertThatThrownBy(() -> service(enabledProperties()).submitFeedback(1L, 999L, "CONFIRMED"))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}
}
