package com.growit.stats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.record.entity.ActivityRecord;
import com.growit.record.repository.ActivityRecordRepository;
import com.growit.record.entity.Feeling;
import com.growit.stats.dto.ActivityMapEntry;
import com.growit.stats.dto.StatsSummaryResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class StatsServiceTest {

	private static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Seoul");

	@Autowired
	private ActivityRecordRepository activityRecordRepository;

	@Autowired
	private HobbyRepository hobbyRepository;

	private StatsService statsService() {
		return new StatsService(activityRecordRepository, hobbyRepository);
	}

	private Long createHobby(Long userId, String name) {
		return hobbyRepository.save(new Hobby(userId, name, null)).getId();
	}

	private void createRecord(Long userId, Long hobbyId, LocalDate date, Integer minutes) {
		activityRecordRepository
				.save(new ActivityRecord(userId, hobbyId, date, "연습했다", minutes, null, Feeling.PROUD, false));
	}

	@Test
	void summary_noRecords_returnsZerosAndNullLastRecord() {
		StatsService service = statsService();
		createHobby(1L, "기타");

		StatsSummaryResponse response = service.summary(1L);

		assertThat(response.recordedDays()).isZero();
		assertThat(response.totalMinutes()).isZero();
		assertThat(response.hobbyCount()).isEqualTo(1);
		assertThat(response.lastRecordedDate()).isNull();
		assertThat(response.daysSinceLastRecord()).isNull();
		assertThat(response.todayRecorded()).isFalse();
		assertThat(response.today()).isEqualTo(LocalDate.now(SERVER_ZONE));
	}

	@Test
	void summary_recordedToday_setsTodayRecordedTrueAndZeroDaysSince() {
		StatsService service = statsService();
		Long hobbyId = createHobby(1L, "기타");
		LocalDate today = LocalDate.now(SERVER_ZONE);
		createRecord(1L, hobbyId, today, 20);

		StatsSummaryResponse response = service.summary(1L);

		assertThat(response.todayRecorded()).isTrue();
		assertThat(response.daysSinceLastRecord()).isZero();
		assertThat(response.lastRecordedDate()).isEqualTo(today);
		assertThat(response.totalMinutes()).isEqualTo(20);
		assertThat(response.recordedDays()).isEqualTo(1);
	}

	@Test
	void summary_lastRecordDaysAgo_calculatesDaysSinceLastRecord() {
		StatsService service = statsService();
		Long hobbyId = createHobby(1L, "기타");
		LocalDate today = LocalDate.now(SERVER_ZONE);
		createRecord(1L, hobbyId, today.minusDays(9), 10);

		StatsSummaryResponse response = service.summary(1L);

		assertThat(response.daysSinceLastRecord()).isEqualTo(9);
		assertThat(response.todayRecorded()).isFalse();
	}

	@Test
	void summary_recordedDaysCountsDistinctDatesNotRecords() {
		StatsService service = statsService();
		Long hobbyId = createHobby(1L, "기타");
		LocalDate today = LocalDate.now(SERVER_ZONE);
		createRecord(1L, hobbyId, today, 10);
		createRecord(1L, hobbyId, today, 15);
		createRecord(1L, hobbyId, today.minusDays(1), 5);

		StatsSummaryResponse response = service.summary(1L);

		assertThat(response.recordedDays()).isEqualTo(2);
		assertThat(response.totalMinutes()).isEqualTo(30);
	}

	@Test
	void summary_separatesDataByUser() {
		StatsService service = statsService();
		Long hobbyId1 = createHobby(1L, "기타");
		Long hobbyId2 = createHobby(2L, "피아노");
		createRecord(1L, hobbyId1, LocalDate.now(SERVER_ZONE), 10);
		createRecord(2L, hobbyId2, LocalDate.now(SERVER_ZONE), 999);

		StatsSummaryResponse response = service.summary(1L);

		assertThat(response.totalMinutes()).isEqualTo(10);
	}

	@Test
	void activityMap_aggregatesByDateAndHobby() {
		StatsService service = statsService();
		Long hobbyId = createHobby(1L, "기타");
		LocalDate day1 = LocalDate.of(2026, 10, 1);
		LocalDate day2 = LocalDate.of(2026, 10, 2);
		createRecord(1L, hobbyId, day1, 10);
		createRecord(1L, hobbyId, day1, 20);
		createRecord(1L, hobbyId, day2, 15);

		List<ActivityMapEntry> entries = service.activityMap(1L, day1, day2, null);

		assertThat(entries).hasSize(2);
		assertThat(entries.get(0).date()).isEqualTo(day1);
		assertThat(entries.get(0).count()).isEqualTo(2);
		assertThat(entries.get(0).minutes()).isEqualTo(30);
		assertThat(entries.get(1).date()).isEqualTo(day2);
		assertThat(entries.get(1).count()).isEqualTo(1);
	}

	@Test
	void activityMap_includesArchivedHobbyRecords() {
		StatsService service = statsService();
		Long hobbyId = createHobby(1L, "기타");
		Hobby hobby = hobbyRepository.findById(hobbyId).orElseThrow();
		hobby.archive(java.time.LocalDateTime.now());
		hobbyRepository.save(hobby);
		LocalDate day = LocalDate.of(2026, 10, 1);
		createRecord(1L, hobbyId, day, 10);

		List<ActivityMapEntry> entries = service.activityMap(1L, day, day, null);

		assertThat(entries).hasSize(1);
	}

	@Test
	void activityMap_fromAfterTo_throwsValidationError() {
		StatsService service = statsService();

		assertThatThrownBy(
				() -> service.activityMap(1L, LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 1), null))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void activityMap_rangeOver400Days_throwsValidationError() {
		StatsService service = statsService();
		LocalDate from = LocalDate.of(2025, 1, 1);
		LocalDate to = from.plusDays(401);

		assertThatThrownBy(() -> service.activityMap(1L, from, to, null))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void activityMap_otherUsersHobbyId_throwsNotFound() {
		StatsService service = statsService();
		Long hobbyId = createHobby(2L, "피아노");

		assertThatThrownBy(
				() -> service.activityMap(1L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), hobbyId))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void weeks_sundayAndMondayFallIntoDifferentWeeks() {
		StatsService service = statsService();
		Long hobbyId = createHobby(1L, "기타");
		// 2026-11-01은 일요일, 2026-11-02는 월요일(다음 주 시작)
		LocalDate sunday = LocalDate.of(2026, 11, 1);
		LocalDate monday = LocalDate.of(2026, 11, 2);
		createRecord(1L, hobbyId, sunday, 10);
		createRecord(1L, hobbyId, monday, 20);

		List<com.growit.stats.dto.WeekSummaryResponse> weeks = service.weeks(1L, sunday, monday, null);

		assertThat(weeks).hasSize(2);
		assertThat(weeks.get(0).weekStart()).isEqualTo(LocalDate.of(2026, 10, 26));
		assertThat(weeks.get(0).recordedDays()).isEqualTo(1);
		assertThat(weeks.get(0).totalMinutes()).isEqualTo(10);
		assertThat(weeks.get(1).weekStart()).isEqualTo(monday);
		assertThat(weeks.get(1).recordedDays()).isEqualTo(1);
		assertThat(weeks.get(1).totalMinutes()).isEqualTo(20);
	}

	@Test
	void weeks_noRecordsInWeek_fillsZero() {
		StatsService service = statsService();
		createHobby(1L, "기타");
		LocalDate from = LocalDate.of(2026, 11, 2);
		LocalDate to = LocalDate.of(2026, 11, 8);

		List<com.growit.stats.dto.WeekSummaryResponse> weeks = service.weeks(1L, from, to, null);

		assertThat(weeks).hasSize(1);
		assertThat(weeks.get(0).recordedDays()).isZero();
		assertThat(weeks.get(0).recordCount()).isZero();
		assertThat(weeks.get(0).totalMinutes()).isZero();
		assertThat(weeks.get(0).hobbies()).isEmpty();
	}

	@Test
	void weeks_separatesDataByUser() {
		StatsService service = statsService();
		Long hobbyId1 = createHobby(1L, "기타");
		Long hobbyId2 = createHobby(2L, "피아노");
		LocalDate monday = LocalDate.of(2026, 11, 2);
		createRecord(1L, hobbyId1, monday, 10);
		createRecord(2L, hobbyId2, monday, 999);

		List<com.growit.stats.dto.WeekSummaryResponse> weeks = service.weeks(1L, monday, monday, null);

		assertThat(weeks.get(0).totalMinutes()).isEqualTo(10);
	}

	@Test
	void weeks_includesArchivedHobbyRecords() {
		StatsService service = statsService();
		Long hobbyId = createHobby(1L, "기타");
		Hobby hobby = hobbyRepository.findById(hobbyId).orElseThrow();
		hobby.archive(java.time.LocalDateTime.now());
		hobbyRepository.save(hobby);
		LocalDate monday = LocalDate.of(2026, 11, 2);
		createRecord(1L, hobbyId, monday, 10);

		List<com.growit.stats.dto.WeekSummaryResponse> weeks = service.weeks(1L, monday, monday, null);

		assertThat(weeks.get(0).recordCount()).isEqualTo(1);
	}

	@Test
	void weeks_fromAfterTo_throwsValidationError() {
		StatsService service = statsService();

		assertThatThrownBy(
				() -> service.weeks(1L, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1), null))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void weeks_rangeOver400Days_throwsValidationError() {
		StatsService service = statsService();
		LocalDate from = LocalDate.of(2025, 1, 1);

		assertThatThrownBy(() -> service.weeks(1L, from, from.plusDays(401), null))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void weeks_otherUsersHobbyId_throwsNotFound() {
		StatsService service = statsService();
		Long hobbyId = createHobby(2L, "피아노");
		LocalDate monday = LocalDate.of(2026, 11, 2);

		assertThatThrownBy(() -> service.weeks(1L, monday, monday, hobbyId))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void weekDetail_returnsAllSevenDaysWithEmptyDaysFilled() {
		StatsService service = statsService();
		Long hobbyId = createHobby(1L, "기타");
		LocalDate monday = LocalDate.of(2026, 11, 2);
		createRecord(1L, hobbyId, monday, 10);

		com.growit.stats.dto.WeekDetailResponse detail = service.weekDetail(1L, monday, null);

		assertThat(detail.weekStart()).isEqualTo(monday);
		assertThat(detail.weekEnd()).isEqualTo(monday.plusDays(6));
		assertThat(detail.days()).hasSize(7);
		assertThat(detail.days().get(0).records()).hasSize(1);
		assertThat(detail.days().get(1).records()).isEmpty();
		assertThat(detail.recordedDays()).isEqualTo(1);
		assertThat(detail.previousWeekStart()).isEqualTo(monday.minusDays(7));
	}

	@Test
	void weekDetail_futureWeek_nextWeekStartIsNull() {
		StatsService service = statsService();
		createHobby(1L, "기타");
		LocalDate currentWeekMonday = LocalDate.now(SERVER_ZONE).with(
				java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));

		com.growit.stats.dto.WeekDetailResponse detail = service.weekDetail(1L, currentWeekMonday, null);

		assertThat(detail.nextWeekStart()).isNull();
	}

	@Test
	void weekDetail_pastWeek_hasNextWeekStart() {
		StatsService service = statsService();
		createHobby(1L, "기타");
		LocalDate pastMonday = LocalDate.of(2020, 1, 6);

		com.growit.stats.dto.WeekDetailResponse detail = service.weekDetail(1L, pastMonday, null);

		assertThat(detail.nextWeekStart()).isEqualTo(pastMonday.plusDays(7));
	}

	@Test
	void weekDetail_notMonday_throwsValidationError() {
		StatsService service = statsService();

		assertThatThrownBy(() -> service.weekDetail(1L, LocalDate.of(2026, 11, 3), null))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void weekDetail_separatesDataByUser() {
		StatsService service = statsService();
		Long hobbyId1 = createHobby(1L, "기타");
		Long hobbyId2 = createHobby(2L, "피아노");
		LocalDate monday = LocalDate.of(2026, 11, 2);
		createRecord(1L, hobbyId1, monday, 10);
		createRecord(2L, hobbyId2, monday, 999);

		com.growit.stats.dto.WeekDetailResponse detail = service.weekDetail(1L, monday, null);

		assertThat(detail.totalMinutes()).isEqualTo(10);
	}

	@Test
	void weekDetail_otherUsersHobbyId_throwsNotFound() {
		StatsService service = statsService();
		Long hobbyId = createHobby(2L, "피아노");
		LocalDate monday = LocalDate.of(2026, 11, 2);

		assertThatThrownBy(() -> service.weekDetail(1L, monday, hobbyId))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}
}
