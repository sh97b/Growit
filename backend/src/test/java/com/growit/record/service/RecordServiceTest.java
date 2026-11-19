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
import com.growit.dot.service.DotGoalService;
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
import com.growit.record.repository.CustomFeelingRepository;
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
	private CustomFeelingRepository customFeelingRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);

	/** AI를 끈 기본 설정(AiProperties())을 쓰면 사용자 조회 없이 바로 SKIPPED로 처리된다. */
	private RecordService recordService() {
		return new RecordService(activityRecordRepository, hobbyRepository, userRepository, new AiProperties(),
				eventPublisher, Mockito.mock(RecordAnalysisService.class), Mockito.mock(DotGoalService.class),
				new CustomFeelingService(customFeelingRepository));
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
				callService, new AiResponseValidator(new ObjectMapper()), aiLogRepository, new ObjectMapper(),
				transactionManager);
		return new RecordService(activityRecordRepository, hobbyRepository, userRepository, properties,
				eventPublisher, analysisService, Mockito.mock(DotGoalService.class),
				new CustomFeelingService(customFeelingRepository));
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

		RecordListResponse response = service.list(1L, null, null, null, null, null, null);

		assertThat(response.totalCount()).isEqualTo(2);
		assertThat(response.items()).extracting(RecordSummaryResponse::recordDate)
				.containsExactly(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1));
	}

	@Test
	void list_searchQuery_matchesContentAndDifficultyCaseInsensitive() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.of(2026, 10, 1), "코드 진행 연습", null, "박자가 불안정했다", null, null));
		service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.of(2026, 10, 2), "스케일 연습", null, null, null, null));

		RecordListResponse byContent = service.list(1L, null, null, null, "코드", null, null);
		assertThat(byContent.totalCount()).isEqualTo(1);
		assertThat(byContent.items().get(0).content()).isEqualTo("코드 진행 연습");

		RecordListResponse byDifficulty = service.list(1L, null, null, null, "박자", null, null);
		assertThat(byDifficulty.totalCount()).isEqualTo(1);

		RecordListResponse caseInsensitive = service.list(1L, null, null, null, "SCALE".toLowerCase(), null, null);
		assertThat(caseInsensitive.totalCount()).isEqualTo(0); // 한글이라 영문 대소문자 케이스는 의미 없지만 메서드 자체는 lower 비교

		RecordListResponse noMatch = service.list(1L, null, null, null, "없는말", null, null);
		assertThat(noMatch.totalCount()).isEqualTo(0);
	}

	@Test
	void list_searchQuery_wildcardCharsTreatedLiterally() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		service.create(1L, new RecordCreateRequest(hobbyId, LocalDate.of(2026, 10, 1), "100% 완성", null, null, null, null));
		service.create(1L, new RecordCreateRequest(hobbyId, LocalDate.of(2026, 10, 2), "그냥 완성", null, null, null, null));

		RecordListResponse literalPercent = service.list(1L, null, null, null, "100%", null, null);
		assertThat(literalPercent.totalCount()).isEqualTo(1);
		assertThat(literalPercent.items().get(0).content()).isEqualTo("100% 완성");

		RecordListResponse underscoreAsWildcardWouldMatchAll = service.list(1L, null, null, null, "_", null, null);
		assertThat(underscoreAsWildcardWouldMatchAll.totalCount()).isEqualTo(0); // _ 가 패턴이 아니라 글자 그대로 취급됨
	}

	@Test
	void list_searchQuery_combinesWithHobbyAndDateAndPagination() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		Long otherHobbyId = createHobby(1L, "피아노");
		service.create(1L, new RecordCreateRequest(hobbyId, LocalDate.of(2026, 10, 1), "연습 일지 A", null, null, null, null));
		service.create(1L,
				new RecordCreateRequest(otherHobbyId, LocalDate.of(2026, 10, 1), "연습 일지 B", null, null, null, null));
		service.create(1L,
				new RecordCreateRequest(hobbyId, LocalDate.of(2026, 9, 1), "지난달 연습 일지", null, null, null, null));

		RecordListResponse byHobby = service.list(1L, hobbyId, null, null, "연습 일지", null, null);
		assertThat(byHobby.totalCount()).isEqualTo(2);

		RecordListResponse byHobbyAndDate = service.list(1L, hobbyId, LocalDate.of(2026, 10, 1),
				LocalDate.of(2026, 10, 31), "연습 일지", null, null);
		assertThat(byHobbyAndDate.totalCount()).isEqualTo(1);

		RecordListResponse paged = service.list(1L, null, null, null, "연습 일지", 0, 1);
		assertThat(paged.totalCount()).isEqualTo(3);
		assertThat(paged.items()).hasSize(1);
	}

	@Test
	void list_searchQuery_ownRecordsOnly() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		Long otherHobbyId = createHobby(2L, "피아노");
		service.create(1L, new RecordCreateRequest(hobbyId, LocalDate.of(2026, 10, 1), "같은낱말 연습", null, null, null, null));
		service.create(2L,
				new RecordCreateRequest(otherHobbyId, LocalDate.of(2026, 10, 1), "같은낱말 연습", null, null, null, null));

		RecordListResponse response = service.list(1L, null, null, null, "같은낱말", null, null);
		assertThat(response.totalCount()).isEqualTo(1);
	}

	@Test
	void list_searchQuery_tooLong_throwsValidationError() {
		RecordService service = recordService();
		String tooLong = "가".repeat(51);

		assertThatThrownBy(() -> service.list(1L, null, null, null, tooLong, null, null))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void list_searchQuery_blankIsIgnored() {
		RecordService service = recordService();
		Long hobbyId = createHobby(1L, "기타");
		service.create(1L, new RecordCreateRequest(hobbyId, LocalDate.of(2026, 10, 1), "기록", null, null, null, null));

		RecordListResponse response = service.list(1L, null, null, null, "   ", null, null);
		assertThat(response.totalCount()).isEqualTo(1);
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

		RecordListResponse response = service.list(1L, null, null, null, null, null, null);

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

	private Long doneRecordWithNextAction(Long userId, Long hobbyId, LocalDate date, String analysisJson) {
		com.growit.record.entity.ActivityRecord record = new com.growit.record.entity.ActivityRecord(userId, hobbyId,
				date, "기록", 20, null, Feeling.PROUD, false);
		record.markAnalysisDone(analysisJson);
		return activityRecordRepository.save(record).getId();
	}

	private static final String NO_ACTION = "{\"title\":\"t\",\"tags\":[],\"relatedRecordIds\":[],\"change\":null}";
	private static final String WITH_ACTION = "{\"title\":\"t\",\"tags\":[],\"relatedRecordIds\":[],\"change\":null,"
			+ "\"nextAction\":{\"text\":\"한 곡만 쳐 봐요\",\"minutes\":5,\"state\":null}}";

	@Test
	void latestNextAction_returnsNewestRecordsAction_orNull() {
		RecordService service = recordServiceWithAiEnabled();
		Long hobbyId = createHobby(1L, "기타");
		assertThat(service.latestNextAction(1L)).isNull();

		doneRecordWithNextAction(1L, hobbyId, LocalDate.of(2026, 10, 1), WITH_ACTION.replace("한 곡만", "옛날"));
		Long newest = doneRecordWithNextAction(1L, hobbyId, LocalDate.of(2026, 10, 5), WITH_ACTION);

		var latest = service.latestNextAction(1L);
		assertThat(latest.recordId()).isEqualTo(newest);
		assertThat(latest.text()).isEqualTo("한 곡만 쳐 봐요");
		assertThat(latest.minutes()).isEqualTo(5);
		assertThat(latest.state()).isNull();
	}

	@Test
	void latestNextAction_newestRecordWithoutAction_returnsNull() {
		RecordService service = recordServiceWithAiEnabled();
		Long hobbyId = createHobby(1L, "기타");
		doneRecordWithNextAction(1L, hobbyId, LocalDate.of(2026, 10, 1), WITH_ACTION);
		doneRecordWithNextAction(1L, hobbyId, LocalDate.of(2026, 10, 5), NO_ACTION);

		assertThat(service.latestNextAction(1L)).isNull();
	}

	@Test
	void reactToNextAction_marksTriedAndKeepsText() {
		RecordService service = recordServiceWithAiEnabled();
		Long hobbyId = createHobby(1L, "기타");
		Long id = doneRecordWithNextAction(1L, hobbyId, LocalDate.of(2026, 10, 5), WITH_ACTION);

		var response = service.reactToNextAction(1L, id, "TRIED");

		assertThat(response.state()).isEqualTo("TRIED");
		assertThat(service.latestNextAction(1L).state()).isEqualTo("TRIED");
		assertThat(service.latestNextAction(1L).text()).isEqualTo("한 곡만 쳐 봐요");
	}

	@Test
	void reactToNextAction_otherUsersRecord_isNotFound() {
		RecordService service = recordServiceWithAiEnabled();
		Long hobbyId = createHobby(2L, "기타");
		Long id = doneRecordWithNextAction(2L, hobbyId, LocalDate.of(2026, 10, 5), WITH_ACTION);

		assertThatThrownBy(() -> service.reactToNextAction(1L, id, "TRIED")).isInstanceOf(ApiException.class);
		assertThat(service.latestNextAction(1L)).isNull();
		assertThat(service.latestNextAction(2L).state()).isNull();
	}

	@Test
	void reactToNextAction_noActionOrBadValue_isRejected() {
		RecordService service = recordServiceWithAiEnabled();
		Long hobbyId = createHobby(1L, "기타");
		Long noAction = doneRecordWithNextAction(1L, hobbyId, LocalDate.of(2026, 10, 5), NO_ACTION);
		Long withAction = doneRecordWithNextAction(1L, hobbyId, LocalDate.of(2026, 10, 6), WITH_ACTION);

		assertThatThrownBy(() -> service.reactToNextAction(1L, noAction, "TRIED")).isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> service.reactToNextAction(1L, withAction, "TOO_HARD"))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void latestNextAction_includesReasonAndEvidenceFilledFromStoredRecord() {
		RecordService service = recordServiceWithAiEnabled();
		Long hobbyId = createHobby(1L, "기타");
		Long evidenceId = doneRecordWithNextAction(1L, hobbyId, LocalDate.of(2026, 10, 1), NO_ACTION);
		doneRecordWithNextAction(1L, hobbyId, LocalDate.of(2026, 10, 5),
				WITH_ACTION.replace("\"state\":null}", "\"state\":null,\"reason\":\"코드 전환이 걸렸어요\",\"evidenceRecordId\":"
						+ evidenceId + "}"));

		var latest = service.latestNextAction(1L);

		assertThat(latest.reason()).isEqualTo("코드 전환이 걸렸어요");
		assertThat(latest.evidence().id()).isEqualTo(evidenceId);
		assertThat(latest.evidence().content()).isEqualTo("기록");
	}

	@Test
	void latestNextAction_oldResultWithoutReason_hasNullReasonAndEvidence() {
		RecordService service = recordServiceWithAiEnabled();
		Long hobbyId = createHobby(1L, "기타");
		doneRecordWithNextAction(1L, hobbyId, LocalDate.of(2026, 10, 5), WITH_ACTION);

		var latest = service.latestNextAction(1L);

		assertThat(latest.text()).isEqualTo("한 곡만 쳐 봐요");
		assertThat(latest.reason()).isNull();
		assertThat(latest.evidence()).isNull();
	}

	@Test
	void latestNextAction_evidenceOfOtherUser_isNotExposed() {
		RecordService service = recordServiceWithAiEnabled();
		Long hobbyId = createHobby(1L, "기타");
		Long otherHobby = createHobby(2L, "피아노");
		Long othersRecord = doneRecordWithNextAction(2L, otherHobby, LocalDate.of(2026, 10, 1), NO_ACTION);
		doneRecordWithNextAction(1L, hobbyId, LocalDate.of(2026, 10, 5),
				WITH_ACTION.replace("\"state\":null}", "\"state\":null,\"reason\":\"이유\",\"evidenceRecordId\":"
						+ othersRecord + "}"));

		assertThat(service.latestNextAction(1L).evidence()).isNull();
	}
}
