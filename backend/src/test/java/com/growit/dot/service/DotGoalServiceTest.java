package com.growit.dot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.dot.dto.DotCurrentResponse;
import com.growit.dot.dto.DotCell;
import com.growit.dot.dto.DotGoalResponse;
import com.growit.dot.dto.DotPhotoCell;
import com.growit.dot.dto.DotGoalStartRequest;
import com.growit.dot.dto.DotPreviewResponse;
import com.growit.dot.entity.DotGoal;
import com.growit.dot.entity.DotGoalState;
import com.growit.dot.entity.DotSourceType;
import com.growit.dot.repository.DotGoalRepository;
import com.growit.dot.repository.DotProgressRepository;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.record.entity.ActivityRecord;
import com.growit.record.repository.ActivityRecordRepository;
import com.growit.user.entity.User;
import com.growit.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class DotGoalServiceTest {

	/** 2026-11-18 정오(KST). */
	private static final Instant NOON_KST = Instant.parse("2026-11-18T03:00:00Z");
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

	@Autowired
	private DotGoalRepository goalRepository;

	@Autowired
	private DotProgressRepository progressRepository;

	@Autowired
	private ActivityRecordRepository recordRepository;

	@Autowired
	private HobbyRepository hobbyRepository;

	@Autowired
	private UserRepository userRepository;

	private Long userId;
	private Long otherUserId;
	private Long hobbyId;
	private Long otherHobbyId;

	@BeforeEach
	void setUp() {
		userId = userRepository.save(new User("dot-svc-a@example.com", "hash", "에이")).getId();
		otherUserId = userRepository.save(new User("dot-svc-b@example.com", "hash", "비")).getId();
		hobbyId = hobbyRepository.save(new Hobby(userId, "기타", null)).getId();
		otherHobbyId = hobbyRepository.save(new Hobby(otherUserId, "독서", null)).getId();
	}

	private DotGoalService service(Instant now) {
		return new DotGoalService(goalRepository, progressRepository, userRepository,
				new DotPresetCatalog(new ObjectMapper()), new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC));
	}

	private DotGoalService service() {
		return service(NOON_KST);
	}

	private void record(Long owner, Long hobby, LocalDate date) {
		recordRepository.save(new ActivityRecord(owner, hobby, date, "연습", null, null, null, false));
	}

	private DotGoalResponse start(Long owner, int days) {
		return service().start(owner, new DotGoalStartRequest("dumbbell", days));
	}

	@Test
	void start_buildsGridWithExactlyGoalDaysCells_andServerDecidesStartedOnInKst() {
		DotGoalResponse goal = start(userId, 30);

		assertThat(goal.cells()).hasSize(30);
		assertThat(goal.goalDays()).isEqualTo(30);
		assertThat(goal.filledCount()).isZero();
		assertThat(goal.state()).isEqualTo(DotGoalState.ACTIVE);
		assertThat(goal.startedOn()).isEqualTo(TODAY);
		assertThat(goal.title()).isEqualTo("덤벨");
		DotGoal saved = goalRepository.findById(goal.id()).orElseThrow();
		assertThat(saved.getGridJson()).contains("\"x\"");
		assertThat(saved.getPaletteJson()).startsWith("[");
	}

	@Test
	void start_secondActive_throwsConflict_butAfterStopOrCompleteANewOneIsAllowed() {
		DotGoalResponse first = start(userId, 14);

		assertThatThrownBy(() -> start(userId, 20)).isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DOT_GOAL_ACTIVE));
		service().stop(userId, first.id());
		DotGoalResponse second = start(userId, 20);
		service().stop(userId, second.id());
		start(userId, 25);

		assertThat(goalRepository.findByUserIdAndStateOrderByCompletedOnDescIdDesc(userId, DotGoalState.COMPLETED))
				.isEmpty();
		assertThat(goalRepository.findAll().stream().filter(g -> g.getUserId().equals(userId)).count()).isEqualTo(3);
	}

	@Test
	void start_otherUserCanStartWhileMineIsActive() {
		start(userId, 14);

		assertThat(start(otherUserId, 14).id()).isNotNull();
	}

	@Test
	void start_invalidInput_isRejected() {
		assertThatThrownBy(() -> start(userId, 13)).isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
		assertThatThrownBy(() -> start(userId, 201)).isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
		assertThatThrownBy(() -> service().start(userId, new DotGoalStartRequest("nope", 30)))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
		assertThat(goalRepository.findAll()).isEmpty();
	}

	@Test
	void markRecordDate_sameDayManyTimes_isOneCell() {
		DotGoalResponse goal = start(userId, 14);

		service().markRecordDate(userId, TODAY);
		service().markRecordDate(userId, TODAY);
		service().markRecordDate(userId, TODAY);

		assertThat(progressRepository.countByGoalId(goal.id())).isEqualTo(1);
	}

	@Test
	void markRecordDate_restDaysChangeNothing_onlyRecordedDaysAreCounted() {
		DotGoalResponse goal = start(userId, 14);

		service().markRecordDate(userId, TODAY);
		service().markRecordDate(userId, TODAY.plusDays(3));
		service().markRecordDate(userId, TODAY.plusDays(4));

		assertThat(progressRepository.countByGoalId(goal.id())).isEqualTo(3);
		assertThat(service().current(userId).goal().filledCount()).isEqualTo(3);
	}

	@Test
	void markRecordDate_beforeStartedOn_isIgnored() {
		DotGoalResponse goal = start(userId, 14);

		service().markRecordDate(userId, TODAY.minusDays(1));

		assertThat(progressRepository.countByGoalId(goal.id())).isZero();
	}

	@Test
	void markRecordDate_withoutActiveGoal_doesNothing() {
		service().markRecordDate(userId, TODAY);

		assertThat(progressRepository.count()).isZero();
	}

	@Test
	void markRecordDate_nthDay_completesGoalWithKstCompletedOn_andLaterDaysAreIgnored() {
		DotGoalResponse goal = start(userId, 14);
		for (int i = 0; i < 13; i++) {
			service().markRecordDate(userId, TODAY.plusDays(i));
		}
		assertThat(goalRepository.findById(goal.id()).orElseThrow().getState()).isEqualTo(DotGoalState.ACTIVE);

		service().markRecordDate(userId, TODAY.plusDays(13));

		DotGoal completed = goalRepository.findById(goal.id()).orElseThrow();
		assertThat(completed.getState()).isEqualTo(DotGoalState.COMPLETED);
		assertThat(completed.getCompletedOn()).isEqualTo(TODAY);
		service().markRecordDate(userId, TODAY.plusDays(20));
		assertThat(progressRepository.countByGoalId(goal.id())).isEqualTo(14);
	}

	@Test
	void markRecordDate_otherUsersRecordsNeverPaintMyGoal() {
		DotGoalResponse goal = start(userId, 14);

		service().markRecordDate(otherUserId, TODAY);

		assertThat(progressRepository.countByGoalId(goal.id())).isZero();
	}

	@Test
	void current_restoresDaysThatExistedBeforeStart_onceEachDayEvenWithManyRecords() {
		record(userId, hobbyId, TODAY);
		record(userId, hobbyId, TODAY);
		record(userId, hobbyId, TODAY.minusDays(1));
		record(otherUserId, otherHobbyId, TODAY);

		DotGoalResponse started = start(userId, 14);

		assertThat(started.filledCount()).isEqualTo(1);
		assertThat(service().current(userId).goal().filledCount()).isEqualTo(1);
	}

	@Test
	void current_isIdempotent_andFillsOnlyMissingDays_neverRemovesFilledOnes() {
		DotGoalResponse goal = start(userId, 14);
		record(userId, hobbyId, TODAY);
		service().markRecordDate(userId, TODAY.plusDays(2));

		DotCurrentResponse first = service().current(userId);
		DotCurrentResponse second = service().current(userId);

		assertThat(first.goal().filledCount()).isEqualTo(2);
		assertThat(second.goal().filledCount()).isEqualTo(2);
		assertThat(progressRepository.findDatesByGoalId(goal.id())).containsExactly(TODAY, TODAY.plusDays(2));
	}

	@Test
	void current_deletedRecordKeepsItsCell_andDeletedRecordDayCannotBeFilledLater() {
		DotGoalResponse goal = start(userId, 14);
		record(userId, hobbyId, TODAY);
		service().current(userId);
		recordRepository.deleteAll(recordRepository.findByUserId(userId));
		record(userId, hobbyId, TODAY.plusDays(1));
		recordRepository.deleteAll(recordRepository.findByUserId(userId));

		assertThat(service().current(userId).goal().filledCount()).isEqualTo(1);
		assertThat(progressRepository.findDatesByGoalId(goal.id())).containsExactly(TODAY);
	}

	@Test
	void current_countsRecordsOfArchivedHobby() {
		Hobby hobby = hobbyRepository.findById(hobbyId).orElseThrow();
		record(userId, hobbyId, TODAY);
		hobby.archive(java.time.LocalDateTime.now());
		hobbyRepository.saveAndFlush(hobby);

		assertThat(start(userId, 14).filledCount()).isEqualTo(1);
	}

	@Test
	void current_completedWhileRestoring_showsUpAsJustCompletedUntilSeen() {
		DotGoalResponse goal = start(userId, 14);
		for (int i = 0; i < 14; i++) {
			record(userId, hobbyId, TODAY.plusDays(i));
		}

		DotCurrentResponse current = service().current(userId);

		assertThat(current.goal()).isNull();
		assertThat(current.justCompleted().id()).isEqualTo(goal.id());
		assertThat(current.justCompleted().filledCount()).isEqualTo(14);
		service().markCompletionSeen(userId, goal.id());
		assertThat(service().current(userId).justCompleted()).isNull();
		service().markCompletionSeen(userId, goal.id());
	}

	@Test
	void album_showsOnlyCompletedOwnGoals_notStoppedOrActive() {
		DotGoalResponse completed = start(userId, 14);
		for (int i = 0; i < 14; i++) {
			service().markRecordDate(userId, TODAY.plusDays(i));
		}
		DotGoalResponse stopped = start(userId, 20);
		service().stop(userId, stopped.id());
		DotGoalResponse active = start(userId, 25);
		start(otherUserId, 14);

		assertThat(service().album(userId)).extracting(DotGoalResponse::id).containsExactly(completed.id());
		assertThat(service().album(otherUserId)).isEmpty();
		assertThat(active.id()).isNotNull();
	}

	@Test
	void detail_stoppedAndOthersGoalsAre404_ownCompletedAndActiveAreVisible() {
		DotGoalResponse active = start(userId, 14);
		DotGoalResponse theirs = start(otherUserId, 14);

		assertThat(service().detail(userId, active.id()).id()).isEqualTo(active.id());
		assertNotFound(() -> service().detail(userId, theirs.id()));
		service().stop(userId, active.id());
		assertNotFound(() -> service().detail(userId, active.id()));
	}

	@Test
	void stop_othersGoalOrNonActiveGoal_is404_andKeepsTheGoal() {
		DotGoalResponse theirs = start(otherUserId, 14);
		DotGoalResponse mine = start(userId, 14);

		assertNotFound(() -> service().stop(userId, theirs.id()));
		assertThat(goalRepository.findById(theirs.id()).orElseThrow().getState()).isEqualTo(DotGoalState.ACTIVE);
		service().stop(userId, mine.id());
		assertNotFound(() -> service().stop(userId, mine.id()));
		assertThat(goalRepository.findById(mine.id()).orElseThrow().getState()).isEqualTo(DotGoalState.STOPPED);
	}

	@Test
	void markCompletionSeen_onActiveOrOthersGoal_is404() {
		DotGoalResponse mine = start(userId, 14);
		DotGoalResponse theirs = start(otherUserId, 14);

		assertNotFound(() -> service().markCompletionSeen(userId, mine.id()));
		assertNotFound(() -> service().markCompletionSeen(userId, theirs.id()));
	}

	@Test
	void kstMidnight_startedOnFollowsSeoulDateNotUtcDate() {
		Instant beforeMidnightKst = Instant.parse("2026-11-17T14:59:59Z");
		Instant atMidnightKst = Instant.parse("2026-11-17T15:00:00Z");

		DotGoalResponse late = service(beforeMidnightKst).start(userId, new DotGoalStartRequest("dumbbell", 14));
		service(beforeMidnightKst).stop(userId, late.id());
		DotGoalResponse early = service(atMidnightKst).start(userId, new DotGoalStartRequest("dumbbell", 14));

		assertThat(late.startedOn()).isEqualTo(LocalDate.of(2026, 11, 17));
		assertThat(early.startedOn()).isEqualTo(LocalDate.of(2026, 11, 18));
		assertThat(ZoneId.of("Asia/Seoul").getRules().getOffset(atMidnightKst).getTotalSeconds()).isEqualTo(32400);
	}

	@Test
	void kstMidnight_recordFromBeforeMidnightIsNotCountedByAGoalStartedAfterIt() {
		DotGoalResponse goal = service(Instant.parse("2026-11-17T15:00:00Z"))
				.start(userId, new DotGoalStartRequest("dumbbell", 14));

		service(Instant.parse("2026-11-17T15:00:00Z")).markRecordDate(userId, LocalDate.of(2026, 11, 17));
		service(Instant.parse("2026-11-17T15:00:00Z")).markRecordDate(userId, LocalDate.of(2026, 11, 18));

		assertThat(progressRepository.findDatesByGoalId(goal.id())).containsExactly(LocalDate.of(2026, 11, 18));
	}

	@Test
	void kstMidnight_completedOnUsesSeoulDate() {
		Instant justAfterKstMidnight = Instant.parse("2026-11-17T15:00:00Z");
		DotGoalService service = service(justAfterKstMidnight);
		DotGoalResponse goal = service.start(userId, new DotGoalStartRequest("dumbbell", 14));
		for (int i = 0; i < 14; i++) {
			service.markRecordDate(userId, LocalDate.of(2026, 11, 18).plusDays(i));
		}

		assertThat(goalRepository.findById(goal.id()).orElseThrow().getCompletedOn())
				.isEqualTo(LocalDate.of(2026, 11, 18));
	}

	@Test
	void preview_equalsTheGridThatStartSaves_forEveryPresetAndSeveralGoalDays() {
		for (String key : List.of("sprout-pot", "guitar", "dumbbell", "book", "laptop")) {
			for (int days : new int[] { 14, 30, 50, 100, 150, 200 }) {
				DotPreviewResponse preview = service().preview(key, days);

				DotGoalResponse started = service().start(userId, new DotGoalStartRequest(key, days));

				assertThat(preview.cells()).as("%s %d칸 칸 목록", key, days).isEqualTo(started.cells());
				assertThat(preview.palette()).isEqualTo(started.palette());
				assertThat(preview.width()).isEqualTo(started.width());
				assertThat(preview.height()).isEqualTo(started.height());
				assertThat(preview.cells()).hasSize(days);
				assertThat(preview.key()).isEqualTo(key);
				assertThat(preview.goalDays()).isEqualTo(days);
				DotGoal saved = goalRepository.findById(started.id()).orElseThrow();
				assertThat(saved.getGridJson()).isEqualTo(toJson(preview.cells()));
				service().stop(userId, started.id());
			}
		}
	}

	@Test
	void preview_writesNothing() {
		service().preview("dumbbell", 30);
		service().preview("book", 200);

		assertThat(goalRepository.count()).isZero();
		assertThat(progressRepository.count()).isZero();
	}

	@Test
	void preview_goalDaysOutOfRangeOrMissing_is400() {
		for (Integer days : new Integer[] { 13, 201, 0, -5, null }) {
			assertThatThrownBy(() -> service().preview("dumbbell", days)).isInstanceOfSatisfying(ApiException.class,
					e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
		}
		service().preview("dumbbell", 14);
		service().preview("dumbbell", 200);
	}

	@Test
	void preview_unknownKey_is404_evenWithBadGoalDays() {
		assertNotFound(() -> service().preview("nope", 30));
		assertNotFound(() -> service().preview("nope", 5));
	}

	private static String toJson(Object value) {
		try {
			return new ObjectMapper().writeValueAsString(value);
		} catch (com.fasterxml.jackson.core.JsonProcessingException e) {
			throw new IllegalStateException(e);
		}
	}

	private static List<DotPhotoCell> photoCells(int count) {
		List<DotPhotoCell> cells = new java.util.ArrayList<>();
		for (int i = 0; i < count; i++) {
			cells.add(new DotPhotoCell(i % 4, i / 4, i % 2));
		}
		java.util.Collections.reverse(cells); // 일부러 거꾸로 보낸다(순서는 서버가 정한다)
		return cells;
	}

	private DotGoalStartRequest photoRequest(String title, int days, Integer width, Integer height,
			List<String> palette, List<DotPhotoCell> cells) {
		return new DotGoalStartRequest(DotSourceType.PHOTO, null, title, days, width, height, palette, cells);
	}

	private DotGoalResponse startPhoto(Long owner) {
		return service().start(owner, photoRequest(null, 14, 4, 4, List.of("#112233", "#AABBCC"), photoCells(14)));
	}

	private static void assertValidationError(Runnable call) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void startPhoto_valid_savesAsPhotoWithServerOrder_andDefaultTitle() {
		DotGoalResponse goal = startPhoto(userId);

		assertThat(goal.sourceType()).isEqualTo(DotSourceType.PHOTO);
		assertThat(goal.presetKey()).isNull();
		assertThat(goal.title()).isEqualTo("내 사진");
		assertThat(goal.goalDays()).isEqualTo(14);
		assertThat(goal.cells()).hasSize(14);
		for (int i = 1; i < goal.cells().size(); i++) {
			DotCell before = goal.cells().get(i - 1);
			DotCell after = goal.cells().get(i);
			assertThat(before.y() > after.y() || (before.y() == after.y() && before.x() < after.x()))
					.as("%d번째 칸 순서(아래 줄부터, 왼쪽부터)", i).isTrue();
		}
		assertThat(goal.cells().get(0)).isEqualTo(new DotCell(0, 3, 0));
		DotGoal saved = goalRepository.findById(goal.id()).orElseThrow();
		assertThat(saved.getSourceType()).isEqualTo(DotSourceType.PHOTO);
		assertThat(saved.getPresetKey()).isNull();
	}

	@Test
	void startPhoto_customTitleIsTrimmed_andTooLongTitleIsRejected() {
		DotGoalResponse goal = service().start(userId,
				photoRequest("  우리 고양이  ", 14, 4, 4, List.of("#112233", "#AABBCC"), photoCells(14)));
		assertThat(goal.title()).isEqualTo("우리 고양이");
		service().stop(userId, goal.id());

		assertValidationError(() -> service().start(userId,
				photoRequest("가".repeat(21), 14, 4, 4, List.of("#112233", "#AABBCC"), photoCells(14))));
		assertThat(service().start(userId,
				photoRequest("가".repeat(20), 14, 4, 4, List.of("#112233", "#AABBCC"), photoCells(14))).title())
				.hasSize(20);
	}

	@Test
	void startPhoto_cellCountDifferentFromGoalDays_isRejected() {
		assertValidationError(() -> service().start(userId,
				photoRequest(null, 14, 4, 4, List.of("#112233"), photoCells(13))));
		assertValidationError(() -> service().start(userId,
				photoRequest(null, 14, 4, 4, List.of("#112233"), photoCells(15))));
		assertThat(goalRepository.findAll()).isEmpty();
	}

	@Test
	void startPhoto_overLimits_areRejected() {
		assertValidationError(() -> service().start(userId,
				photoRequest(null, 14, 41, 4, List.of("#112233"), photoCells(14))));
		assertValidationError(() -> service().start(userId,
				photoRequest(null, 14, 4, 41, List.of("#112233"), photoCells(14))));
		List<String> nine = java.util.stream.IntStream.range(0, 9).mapToObj(i -> "#11223" + i).toList();
		assertValidationError(() -> service().start(userId, photoRequest(null, 14, 4, 4, nine, photoCells(14))));
		assertValidationError(() -> service().start(userId,
				photoRequest(null, 14, 4, 4, List.of("red"), photoCells(14))));
		assertValidationError(() -> service().start(userId,
				photoRequest(null, 201, 20, 20, List.of("#112233"), photoCells(14))));
		assertThat(goalRepository.findAll()).isEmpty();
	}

	@Test
	void startPhoto_duplicatedOrOutOfRangeCells_areRejected() {
		List<DotPhotoCell> duplicated = new java.util.ArrayList<>(photoCells(14));
		duplicated.set(0, duplicated.get(1));
		assertValidationError(() -> service().start(userId,
				photoRequest(null, 14, 4, 4, List.of("#112233", "#AABBCC"), duplicated)));

		List<DotPhotoCell> outside = new java.util.ArrayList<>(photoCells(14));
		outside.set(0, new DotPhotoCell(4, 0, 0));
		assertValidationError(() -> service().start(userId,
				photoRequest(null, 14, 4, 4, List.of("#112233", "#AABBCC"), outside)));

		List<DotPhotoCell> badColor = new java.util.ArrayList<>(photoCells(14));
		badColor.set(0, new DotPhotoCell(0, 0, 2));
		assertValidationError(() -> service().start(userId,
				photoRequest(null, 14, 4, 4, List.of("#112233", "#AABBCC"), badColor)));
	}

	@Test
	void startPhoto_missingGridInfo_isRejected() {
		assertValidationError(() -> service().start(userId,
				photoRequest(null, 14, null, 4, List.of("#112233"), photoCells(14))));
		assertValidationError(() -> service().start(userId, photoRequest(null, 14, 4, 4, null, photoCells(14))));
		assertValidationError(() -> service().start(userId,
				photoRequest(null, 14, 4, 4, List.of("#112233"), null)));
	}

	@Test
	void startPhoto_whileActiveGoalExists_isConflict_andPresetRequestStillNeedsAKey() {
		start(userId, 14);

		assertThatThrownBy(() -> startPhoto(userId)).isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DOT_GOAL_ACTIVE));
		assertValidationError(() -> service().start(otherUserId, new DotGoalStartRequest(null, 14)));
	}

	@Test
	void startPhoto_completedPhotoGoalAppearsInAlbumAsPhoto() {
		DotGoalResponse goal = startPhoto(userId);
		for (int i = 0; i < 14; i++) {
			service().markRecordDate(userId, TODAY.plusDays(i));
		}

		List<DotGoalResponse> album = service().album(userId);

		assertThat(album).extracting(DotGoalResponse::id).containsExactly(goal.id());
		assertThat(album.get(0).sourceType()).isEqualTo(DotSourceType.PHOTO);
		assertThat(album.get(0).presetKey()).isNull();
		assertThat(album.get(0).filledCount()).isEqualTo(14);
	}

	private static void assertNotFound(Runnable call) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}
}
