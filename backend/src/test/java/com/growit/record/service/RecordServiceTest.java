package com.growit.record.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.ai.client.AiChatClient;
import com.growit.ai.config.AiProperties;
import com.growit.ai.repository.AiLogRepository;
import com.growit.ai.service.AiCallService;
import com.growit.ai.service.AiResponseValidator;
import com.growit.ai.service.AiUsageService;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.record.dto.AnalysisFeedbackRequest;
import com.growit.record.dto.AnalysisTagsEditRequest;
import com.growit.record.dto.RecordCreateRequest;
import com.growit.record.dto.RecordListResponse;
import com.growit.record.dto.RecordResponse;
import com.growit.record.dto.RecordSummaryResponse;
import com.growit.record.dto.RecordUpdateRequest;
import com.growit.record.entity.AiStatus;
import com.growit.record.entity.ChangeFeedback;
import com.growit.record.entity.Feeling;
import com.growit.record.entity.TagsFeedbackChoice;
import com.growit.record.event.RecordSavedEvent;
import com.growit.record.repository.ActivityRecordRepository;
import com.growit.user.entity.User;
import com.growit.user.repository.UserRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

@DataJpaTest
class RecordServiceTest {

	@Autowired
	private ActivityRecordRepository activityRecordRepository;

	@Autowired
	private HobbyRepository hobbyRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AiLogRepository aiLogRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);

	/** AI를 끈 기본 설정(AiProperties())을 쓰면 사용자 조회 없이 바로 SKIPPED로 처리된다. */
	private RecordService recordService() {
		return new RecordService(activityRecordRepository, hobbyRepository, userRepository, new AiProperties(),
				eventPublisher, Mockito.mock(RecordAnalysisService.class));
	}

	/** 실제 RecordAnalysisService를 연결해 피드백·태그 수정의 JSON 변환 로직까지 검증한다(AI 호출은 일어나지 않음). */
	private RecordService recordServiceWithAiEnabled() {
		AiProperties properties = new AiProperties();
		properties.setApiKey("key");
		properties.setModel("model");
		AiChatClient aiChatClient = Mockito.mock(AiChatClient.class);
		AiUsageService usageService = new AiUsageService(aiLogRepository, properties);
		AiCallService callService = new AiCallService(properties, aiChatClient, aiLogRepository, usageService,
				new ObjectMapper());
		RecordAnalysisService analysisService = new RecordAnalysisService(activityRecordRepository, hobbyRepository,
				callService, new AiResponseValidator(new ObjectMapper()), new ObjectMapper(), transactionManager);
		return new RecordService(activityRecordRepository, hobbyRepository, userRepository, properties,
				eventPublisher, analysisService);
	}

	private Long createUser() {
		return userRepository.save(new User("aitest@example.com", "hashed", "사용자")).getId();
	}

	private Long createHobby(Long userId, String name) {
		Hobby hobby = new Hobby(userId, name, null);
		return hobbyRepository.save(hobby).getId();
	}

	@Test
	void create_savesRecord() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");

		RecordResponse response = service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.of(2026, 10, 28), "코드 전환 연습", 20, "속도가 안 나왔다",
						Feeling.PROUD, true));

		assertThat(response.id()).isNotNull();
		assertThat(response.hobbyId()).isEqualTo(hobbyId);
		assertThat(response.content()).isEqualTo("코드 전환 연습");
		assertThat(response.analysis()).isNull();
	}

	@Test
	void create_aiOff_setsSkippedStatus() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");

		RecordResponse response = service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null));

		assertThat(response.aiStatus()).isEqualTo(AiStatus.SKIPPED);
	}

	@Test
	void create_hobbyNotOwnedByUser_throwsNotFound() {
		RecordService service = recordService();
		Long hobbyId = createHobby(2L, "기타");

		assertThatThrownBy(() -> service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null)))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void get_otherUsersRecord_throwsNotFound() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		RecordResponse created = service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null));

		assertThatThrownBy(() -> service.get(2L, created.id()))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void update_blankContent_throwsValidationError() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		RecordResponse created = service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null));

		assertThatThrownBy(() -> service.update(1L, created.id(),
				new RecordUpdateRequest(null, null, JsonNullable.of("   "), JsonNullable.undefined(),
						JsonNullable.undefined(), JsonNullable.undefined(), null)))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void update_negativeMinutes_throwsValidationError() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		RecordResponse created = service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null));

		assertThatThrownBy(() -> service.update(1L, created.id(),
				new RecordUpdateRequest(null, null, JsonNullable.undefined(), JsonNullable.of(-5),
						JsonNullable.undefined(), JsonNullable.undefined(), null)))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void create_archivedHobby_throwsValidationError() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		Hobby hobby = hobbyRepository.findById(hobbyId).orElseThrow();
		hobby.archive(java.time.LocalDateTime.now());
		hobbyRepository.save(hobby);

		assertThatThrownBy(() -> service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null)))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void update_moveToArchivedHobby_throwsValidationError() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		RecordResponse created = service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null));
		Long archivedHobbyId = createHobby(1L, "러닝");
		Hobby archivedHobby = hobbyRepository.findById(archivedHobbyId).orElseThrow();
		archivedHobby.archive(java.time.LocalDateTime.now());
		hobbyRepository.save(archivedHobby);

		assertThatThrownBy(() -> service.update(1L, created.id(),
				new RecordUpdateRequest(archivedHobbyId, null, JsonNullable.undefined(), JsonNullable.undefined(),
						JsonNullable.undefined(), JsonNullable.undefined(), null)))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void list_filtersByUserAndHobby() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		Long otherHobbyId = createHobby(2L, "피아노");
		service.create(1L, new RecordCreateRequest(hobbyId, LocalDate.of(2026, 10, 1), "연습 1", null, null, null, null));
		service.create(1L, new RecordCreateRequest(hobbyId, LocalDate.of(2026, 10, 2), "연습 2", null, null, null, null));
		service.create(2L,
				new RecordCreateRequest(otherHobbyId, LocalDate.of(2026, 10, 2), "남의 기록", null, null, null, null));

		RecordListResponse response = service.list(1L, null, null, null, null, null);

		assertThat(response.totalCount()).isEqualTo(2);
		assertThat(response.items()).extracting(RecordSummaryResponse::recordDate)
				.containsExactly(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1));
	}

	@Test
	void update_fieldsOmitted_keepsExistingValues() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		RecordResponse created = service.create(1L, new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", 20,
				"속도가 안 나왔다", Feeling.PROUD, false));

		RecordResponse updated = service.update(1L, created.id(),
				new RecordUpdateRequest(null, null, JsonNullable.undefined(), JsonNullable.undefined(),
						JsonNullable.undefined(), JsonNullable.undefined(), null));

		assertThat(updated.minutes()).isEqualTo(20);
		assertThat(updated.difficulty()).isEqualTo("속도가 안 나왔다");
		assertThat(updated.feeling()).isEqualTo(Feeling.PROUD);
	}

	@Test
	void update_fieldsSetToNull_clearsValues() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		RecordResponse created = service.create(1L, new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", 20,
				"속도가 안 나왔다", Feeling.PROUD, false));

		RecordResponse updated = service.update(1L, created.id(),
				new RecordUpdateRequest(null, null, JsonNullable.undefined(), JsonNullable.of(null),
						JsonNullable.of(null), JsonNullable.of(null), null));

		assertThat(updated.minutes()).isNull();
		assertThat(updated.difficulty()).isNull();
		assertThat(updated.feeling()).isNull();
	}

	@Test
	void update_contentSetToNull_throwsValidationError() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		RecordResponse created = service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null));

		assertThatThrownBy(() -> service.update(1L, created.id(),
				new RecordUpdateRequest(null, null, JsonNullable.of(null), JsonNullable.undefined(),
						JsonNullable.undefined(), JsonNullable.undefined(), null)))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void update_hobbyId_movesRecordToOwnedActiveHobby() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		Long otherHobbyId = createHobby(1L, "러닝");
		RecordResponse created = service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null));

		RecordResponse updated = service.update(1L, created.id(),
				new RecordUpdateRequest(otherHobbyId, null, JsonNullable.undefined(), JsonNullable.undefined(),
						JsonNullable.undefined(), JsonNullable.undefined(), null));

		assertThat(updated.hobbyId()).isEqualTo(otherHobbyId);
	}

	@Test
	void create_futureDate_throwsValidationError() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");

		assertThatThrownBy(() -> service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.now().plusDays(1), "연습했다", null, null, null, null)))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void update_futureDate_throwsValidationError() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		RecordResponse created = service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null));

		assertThatThrownBy(() -> service.update(1L, created.id(),
				new RecordUpdateRequest(null, LocalDate.now().plusDays(1), JsonNullable.undefined(),
						JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined(), null)))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void get_returnsPreviousAndNextRecordWithinSameHobby() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		Long otherHobbyId = createHobby(1L, "러닝");
		RecordResponse first = service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.of(2026, 10, 1), "연습 1", null, null, null, null));
		RecordResponse middle = service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.of(2026, 10, 10), "연습 2", null, null, null, null));
		RecordResponse last = service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.of(2026, 10, 20), "연습 3", null, null, null, null));
		service.create(1L,
				new RecordCreateRequest(otherHobbyId, LocalDate.of(2026, 10, 15), "다른 분야 기록", null, null, null, null));

		RecordResponse response = service.get(1L, middle.id());

		assertThat(response.previousRecord().id()).isEqualTo(first.id());
		assertThat(response.nextRecord().id()).isEqualTo(last.id());

		RecordResponse firstResponse = service.get(1L, first.id());
		assertThat(firstResponse.previousRecord()).isNull();
		assertThat(firstResponse.nextRecord().id()).isEqualTo(middle.id());
	}

	@Test
	void list_returnsSummaryWithoutNeighbors() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		service.create(1L, new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null));

		RecordListResponse response = service.list(1L, null, null, null, null, null);

		assertThat(response.items()).hasSize(1);
		assertThat(response.items().get(0).content()).isEqualTo("연습했다");
	}

	@Test
	void create_aiEnabledUser_setsPendingAndPublishesEvent() {
		Long userId = createUser();
		RecordService service = recordServiceWithAiEnabled();
		Long hobbyId = createHobby(userId, "기타");

		RecordResponse response = service.create(userId,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null));

		assertThat(response.aiStatus()).isEqualTo(AiStatus.PENDING);
		verify(eventPublisher).publishEvent(new RecordSavedEvent(response.id(), userId));
	}

	@Test
	void update_contentChanged_triggersReanalysis() {
		Long userId = createUser();
		RecordService service = recordServiceWithAiEnabled();
		Long hobbyId = createHobby(userId, "기타");
		RecordResponse created = service.create(userId,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null));
		Mockito.clearInvocations(eventPublisher);

		RecordResponse updated = service.update(userId, created.id(),
				new RecordUpdateRequest(null, null, JsonNullable.of("새로 고친 글"), JsonNullable.undefined(),
						JsonNullable.undefined(), JsonNullable.undefined(), null));

		assertThat(updated.aiStatus()).isEqualTo(AiStatus.PENDING);
		verify(eventPublisher).publishEvent(new RecordSavedEvent(updated.id(), userId));
	}

	@Test
	void update_onlyMinutesChanged_doesNotTriggerReanalysis() {
		Long userId = createUser();
		RecordService service = recordServiceWithAiEnabled();
		Long hobbyId = createHobby(userId, "기타");
		RecordResponse created = service.create(userId,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null));
		activityRecordRepository.findById(created.id()).orElseThrow()
				.markAnalysisDone("{\"title\":\"기존\",\"tags\":[],\"relatedRecordIds\":[],\"change\":null,"
						+ "\"tagsFeedback\":null,\"changeFeedback\":null}");

		RecordResponse updated = service.update(userId, created.id(),
				new RecordUpdateRequest(null, null, JsonNullable.undefined(), JsonNullable.of(30),
						JsonNullable.undefined(), JsonNullable.undefined(), null));

		assertThat(updated.aiStatus()).isEqualTo(AiStatus.DONE);
	}

	@Test
	void reanalyze_pending_returnsAsIsWithoutPublishingAgain() {
		Long userId = createUser();
		RecordService service = recordServiceWithAiEnabled();
		Long hobbyId = createHobby(userId, "기타");
		RecordResponse created = service.create(userId,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null));
		Mockito.clearInvocations(eventPublisher);

		RecordResponse response = service.reanalyze(userId, created.id());

		assertThat(response.aiStatus()).isEqualTo(AiStatus.PENDING);
		verify(eventPublisher, Mockito.never()).publishEvent(any());
	}

	@Test
	void reanalyze_aiDisabledForUser_throwsAiDisabled() {
		Long userId = createUser();
		User user = userRepository.findById(userId).orElseThrow();
		user.setAiEnabled(false);
		userRepository.save(user);
		RecordService service = recordServiceWithAiEnabled();
		Long hobbyId = createHobby(userId, "기타");
		RecordResponse created = service.create(userId,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null));

		assertThatThrownBy(() -> service.reanalyze(userId, created.id()))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_DISABLED));
	}

	@Test
	void submitFeedback_notDone_throwsAnalysisNotReady() {
		Long userId = createUser();
		RecordService service = recordServiceWithAiEnabled();
		Long hobbyId = createHobby(userId, "기타");
		RecordResponse created = service.create(userId,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null));

		assertThatThrownBy(() -> service.submitFeedback(userId, created.id(),
				new AnalysisFeedbackRequest(ChangeFeedback.HELPFUL, null)))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ANALYSIS_NOT_READY));
	}

	@Test
	void submitFeedback_done_storesFeedback() {
		Long userId = createUser();
		RecordService service = recordServiceWithAiEnabled();
		Long hobbyId = createHobby(userId, "기타");
		RecordResponse created = service.create(userId,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null));
		activityRecordRepository.findById(created.id()).orElseThrow()
				.markAnalysisDone("{\"title\":\"제목\",\"tags\":[],\"relatedRecordIds\":[],\"change\":null,"
						+ "\"tagsFeedback\":null,\"changeFeedback\":null}");

		RecordResponse response = service.submitFeedback(userId, created.id(),
				new AnalysisFeedbackRequest(ChangeFeedback.HELPFUL, TagsFeedbackChoice.CONFIRMED));

		assertThat(response.analysis().changeFeedback()).isEqualTo("HELPFUL");
		assertThat(response.analysis().tagsFeedback()).isEqualTo("CONFIRMED");
	}

	@Test
	void editTags_done_normalizesAndMarksEdited() {
		Long userId = createUser();
		RecordService service = recordServiceWithAiEnabled();
		Long hobbyId = createHobby(userId, "기타");
		RecordResponse created = service.create(userId,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null));
		activityRecordRepository.findById(created.id()).orElseThrow()
				.markAnalysisDone("{\"title\":\"제목\",\"tags\":[\"기존\"],\"relatedRecordIds\":[],\"change\":null,"
						+ "\"tagsFeedback\":null,\"changeFeedback\":null}");

		RecordResponse response = service.editTags(userId, created.id(),
				new AnalysisTagsEditRequest(java.util.List.of(" 새 태그 ", "새 태그", "")));

		assertThat(response.analysis().tags()).containsExactly("새 태그");
		assertThat(response.analysis().tagsFeedback()).isEqualTo("EDITED");
	}

	@Test
	void editTags_tooManyTags_throwsValidationError() {
		Long userId = createUser();
		RecordService service = recordServiceWithAiEnabled();
		Long hobbyId = createHobby(userId, "기타");
		RecordResponse created = service.create(userId,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null));
		activityRecordRepository.findById(created.id()).orElseThrow()
				.markAnalysisDone("{\"title\":\"제목\",\"tags\":[],\"relatedRecordIds\":[],\"change\":null}");

		assertThatThrownBy(() -> service.editTags(userId, created.id(),
				new AnalysisTagsEditRequest(java.util.List.of("1", "2", "3", "4"))))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void get_stalePending_correctsToFailedAndPersists() {
		Long userId = createUser();
		RecordService service = recordServiceWithAiEnabled();
		Long hobbyId = createHobby(userId, "기타");
		RecordResponse created = service.create(userId,
				new RecordCreateRequest(hobbyId, LocalDate.now(), "연습했다", null, null, null, null));
		var record = activityRecordRepository.findById(created.id()).orElseThrow();
		record.markAnalysisPending(java.time.LocalDateTime.now().minusMinutes(5));
		activityRecordRepository.save(record);

		RecordResponse response = service.get(userId, created.id());

		assertThat(response.aiStatus()).isEqualTo(AiStatus.FAILED);
		assertThat(activityRecordRepository.findById(created.id()).orElseThrow().getAiStatus())
				.isEqualTo(AiStatus.FAILED);
	}
}
