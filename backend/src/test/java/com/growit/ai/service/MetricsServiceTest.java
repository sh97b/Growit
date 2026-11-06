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
import com.growit.ai.dto.MetricsRequest;
import com.growit.ai.repository.AiLogRepository;
import com.growit.ai.repository.AiResultRepository;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.record.entity.ActivityRecord;
import com.growit.record.entity.Feeling;
import com.growit.record.repository.ActivityRecordRepository;
import com.growit.stats.service.StatsService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class MetricsServiceTest {

	private static final LocalDate WEEK1 = LocalDate.of(2026, 11, 2);
	private static final LocalDate WEEK2 = LocalDate.of(2026, 11, 9);

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

	private MetricsService service(AiProperties properties) {
		AiUsageService usageService = new AiUsageService(aiLogRepository, properties);
		AiCallService callService = new AiCallService(properties, aiChatClient, aiLogRepository, usageService,
				objectMapper);
		AiResponseValidator validator = new AiResponseValidator(objectMapper);
		StatsService statsService = new StatsService(activityRecordRepository, hobbyRepository);
		return new MetricsService(activityRecordRepository, hobbyRepository, aiResultRepository, callService,
				validator, statsService, objectMapper);
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
	void metrics_fewerThanTwoRecordedWeeks_returnsLockedWithoutCallingProvider() {
		Long hobbyId = createHobby(1L);
		createRecord(1L, hobbyId, WEEK1, "연습1");

		AiViewResponse response = service(enabledProperties())
				.metrics(1L, new MetricsRequest(hobbyId, WEEK1, WEEK2.plusDays(6)));

		assertThat(response.locked()).isTrue();
		assertThat(response.requiredWeeks()).isEqualTo(2);
		assertThat(response.currentWeeks()).isEqualTo(1);
		verifyNoInteractions(aiChatClient);
	}

	@Test
	void metrics_success_storesTextAndEvidence() {
		Long hobbyId = createHobby(1L);
		ActivityRecord r1 = createRecord(1L, hobbyId, WEEK1, "연습1");
		ActivityRecord r2 = createRecord(1L, hobbyId, WEEK2, "연습2");

		stubAiResponse("{\"text\":\"두 주에 걸쳐 기록했어요\",\"evidenceIds\":[" + r1.getId() + "," + r2.getId() + "]}");

		AiViewResponse response = service(enabledProperties())
				.metrics(1L, new MetricsRequest(hobbyId, WEEK1, WEEK2.plusDays(6)));

		assertThat(response.locked()).isFalse();
		assertThat(response.reused()).isFalse();
		assertThat(response.text()).isEqualTo("두 주에 걸쳐 기록했어요");
		assertThat(response.evidence()).hasSize(2);
		assertThat(aiResultRepository.findAll()).hasSize(1);
	}

	@Test
	void metrics_sameInputTwice_reusesStoredResult() {
		Long hobbyId = createHobby(1L);
		ActivityRecord r1 = createRecord(1L, hobbyId, WEEK1, "연습1");
		ActivityRecord r2 = createRecord(1L, hobbyId, WEEK2, "연습2");
		stubAiResponse("{\"text\":\"두 주에 걸쳐 기록했어요\",\"evidenceIds\":[" + r1.getId() + "," + r2.getId() + "]}");

		MetricsService service = service(enabledProperties());
		MetricsRequest request = new MetricsRequest(hobbyId, WEEK1, WEEK2.plusDays(6));
		AiViewResponse first = service.metrics(1L, request);
		AiViewResponse second = service.metrics(1L, request);

		assertThat(first.reused()).isFalse();
		assertThat(second.reused()).isTrue();
		assertThat(second.id()).isEqualTo(first.id());
		assertThat(aiResultRepository.findAll()).hasSize(1);
	}

	@Test
	void metrics_recordEditedAfterCache_changesCacheKeyAndCallsAgain() {
		Long hobbyId = createHobby(1L);
		ActivityRecord r1 = createRecord(1L, hobbyId, WEEK1, "연습1");
		ActivityRecord r2 = createRecord(1L, hobbyId, WEEK2, "연습2");
		stubAiResponse("{\"text\":\"두 주에 걸쳐 기록했어요\",\"evidenceIds\":[" + r1.getId() + "," + r2.getId() + "]}");

		MetricsService service = service(enabledProperties());
		MetricsRequest request = new MetricsRequest(hobbyId, WEEK1, WEEK2.plusDays(6));
		service.metrics(1L, request);

		r1.setContent("연습1을 고쳤다");
		activityRecordRepository.saveAndFlush(r1);
		stubAiResponse("{\"text\":\"두 주에 걸쳐 더 기록했어요\",\"evidenceIds\":[" + r1.getId() + "]}");

		AiViewResponse second = service.metrics(1L, request);

		assertThat(second.reused()).isFalse();
		assertThat(aiResultRepository.findAll()).hasSize(2);
	}

	@Test
	void metrics_forbiddenWordInText_throwsAiFailed() {
		Long hobbyId = createHobby(1L);
		createRecord(1L, hobbyId, WEEK1, "연습1");
		createRecord(1L, hobbyId, WEEK2, "연습2");

		stubAiResponse("{\"text\":\"실력이 늘었어요\",\"evidenceIds\":[]}");

		assertThatThrownBy(() -> service(enabledProperties())
				.metrics(1L, new MetricsRequest(hobbyId, WEEK1, WEEK2.plusDays(6))))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_FAILED));
		assertThat(aiResultRepository.findAll()).isEmpty();
	}

	@Test
	void metrics_hobbyNotOwned_throwsNotFound() {
		Long hobbyId = createHobby(2L);

		assertThatThrownBy(() -> service(enabledProperties())
				.metrics(1L, new MetricsRequest(hobbyId, WEEK1, WEEK2.plusDays(6))))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
		verifyNoInteractions(aiChatClient);
	}

	@Test
	void metrics_fromAfterTo_throwsValidationError() {
		assertThatThrownBy(() -> service(enabledProperties())
				.metrics(1L, new MetricsRequest(null, WEEK2, WEEK1)))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void metrics_separatesDataByUser() {
		Long hobbyId1 = createHobby(1L);
		Long hobbyId2 = createHobby(2L);
		createRecord(2L, hobbyId2, WEEK1, "다른 사용자1");
		createRecord(2L, hobbyId2, WEEK2, "다른 사용자2");
		createRecord(1L, hobbyId1, WEEK1, "내 기록");

		AiViewResponse response = service(enabledProperties())
				.metrics(1L, new MetricsRequest(hobbyId1, WEEK1, WEEK2.plusDays(6)));

		assertThat(response.locked()).isTrue();
		assertThat(response.currentWeeks()).isEqualTo(1);
	}

	@Test
	void metrics_userLimitExceeded_throwsLimitExceeded() {
		Long hobbyId = createHobby(1L);
		createRecord(1L, hobbyId, WEEK1, "연습1");
		createRecord(1L, hobbyId, WEEK2, "연습2");
		AiProperties properties = enabledProperties();
		properties.setViewUserDailyLimit(0);

		assertThatThrownBy(() -> service(properties)
				.metrics(1L, new MetricsRequest(hobbyId, WEEK1, WEEK2.plusDays(6))))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_LIMIT_EXCEEDED));
	}

	@Test
	void editText_savesEditedTextAndFeedback() {
		Long hobbyId = createHobby(1L);
		ActivityRecord r1 = createRecord(1L, hobbyId, WEEK1, "연습1");
		ActivityRecord r2 = createRecord(1L, hobbyId, WEEK2, "연습2");
		stubAiResponse("{\"text\":\"두 주에 걸쳐 기록했어요\",\"evidenceIds\":[" + r1.getId() + "," + r2.getId() + "]}");
		AiViewResponse created = service(enabledProperties())
				.metrics(1L, new MetricsRequest(hobbyId, WEEK1, WEEK2.plusDays(6)));

		AiViewResponse edited = service(enabledProperties()).editText(1L, created.id(), "내가 고친 문장이에요");

		assertThat(edited.editedText()).isEqualTo("내가 고친 문장이에요");
		assertThat(edited.displayText()).isEqualTo("내가 고친 문장이에요");
		assertThat(edited.feedback()).isEqualTo("EDITED");
	}

	@Test
	void submitFeedback_notOwner_throwsNotFound() {
		assertThatThrownBy(() -> service(enabledProperties()).submitFeedback(1L, 999L, "DISPUTED"))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}
}
