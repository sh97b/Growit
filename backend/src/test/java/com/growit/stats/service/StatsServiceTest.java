package com.growit.stats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.record.entity.ActivityRecord;
import com.growit.record.repository.ActivityRecordRepository;
import com.growit.record.entity.Feeling;
import com.growit.stats.dto.ActivityMapEntry;
import com.growit.record.entity.FeelingDirection;
import com.growit.stats.dto.GrowthResponse;
import com.growit.stats.dto.StatsSummaryResponse;
import com.growit.stats.dto.WeekTagCount;
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
		return new StatsService(activityRecordRepository, hobbyRepository, new ObjectMapper());
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
		assertThat(response.firstRecordedDate()).isNull();
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
	void summary_firstRecordedDate_isEarliestRecordDate() {
		StatsService service = statsService();
		Long hobbyId = createHobby(1L, "기타");
		LocalDate today = LocalDate.now(SERVER_ZONE);
		createRecord(1L, hobbyId, today, 10);
		createRecord(1L, hobbyId, today.minusDays(30), 10);
		createRecord(1L, hobbyId, today.minusDays(10), 10);

		StatsSummaryResponse response = service.summary(1L);

		assertThat(response.firstRecordedDate()).isEqualTo(today.minusDays(30));
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
	void weeks_countsTagsFromDoneRecordsOnly_top5() {
		StatsService service = statsService();
		Long hobbyId = createHobby(1L, "기타");
		LocalDate monday = LocalDate.of(2026, 11, 2);

		ActivityRecord tagged1 = activityRecordRepository
				.save(new ActivityRecord(1L, hobbyId, monday, "연습했다", 10, null, Feeling.PROUD, false));
		tagged1.markAnalysisDone("{\"title\":\"t\",\"tags\":[\"박자\",\"호흡\"]}");
		activityRecordRepository.save(tagged1);

		ActivityRecord tagged2 = activityRecordRepository
				.save(new ActivityRecord(1L, hobbyId, monday.plusDays(1), "연습했다", 10, null, Feeling.PROUD, false));
		tagged2.markAnalysisDone("{\"title\":\"t\",\"tags\":[\"박자\"]}");
		activityRecordRepository.save(tagged2);

		// 분석이 끝나지 않은 기록의 태그는 세지 않는다.
		createRecord(1L, hobbyId, monday.plusDays(2), 10);

		List<com.growit.stats.dto.WeekSummaryResponse> weeks = service.weeks(1L, monday, monday, null);

		assertThat(weeks.get(0).tags()).hasSize(2);
		assertThat(weeks.get(0).tags().get(0).tag()).isEqualTo("박자");
		assertThat(weeks.get(0).tags().get(0).count()).isEqualTo(2);
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

	private ActivityRecord save(Long userId, Long hobbyId, LocalDate date, String content, Integer minutes,
			Feeling feeling, boolean milestone) {
		return activityRecordRepository
				.save(new ActivityRecord(userId, hobbyId, date, content, minutes, null, feeling, milestone));
	}

	@Test
	void growth_hobbyFilter_countsOnlyThatHobby_andOtherUsersHobbyIsNotFound() {
		StatsService service = statsService();
		Long guitar = createHobby(1L, "기타");
		Long draw = createHobby(1L, "드로잉");
		Long others = createHobby(2L, "남의 분야");
		LocalDate d = LocalDate.of(2026, 11, 10);
		save(1L, guitar, d, "기타", 20, Feeling.PROUD, false);
		save(1L, draw, d, "그림", 30, Feeling.NEUTRAL, false);
		save(2L, others, d, "남의 기록", 99, Feeling.STUCK, false);

		GrowthResponse all = service.growth(1L, d.minusDays(5), d.plusDays(5), null);
		GrowthResponse only = service.growth(1L, d.minusDays(5), d.plusDays(5), guitar);

		assertThat(all.summary().records()).isEqualTo(2);
		assertThat(all.summary().minutes()).isEqualTo(50);
		assertThat(all.byHobby()).extracting(GrowthResponse.ByHobby::name).containsExactlyInAnyOrder("기타", "드로잉");
		assertThat(only.summary().records()).isEqualTo(1);
		assertThat(only.byHobby()).extracting(GrowthResponse.ByHobby::hobbyId).containsExactly(guitar);
		assertThat(all.feelings().totals().hard()).isZero();
		assertThatThrownBy(() -> service.growth(1L, d.minusDays(5), d.plusDays(5), others))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void growth_weeksStartOnMonday_sundayAndMondayAreDifferentWeeks() {
		StatsService service = statsService();
		Long hobbyId = createHobby(1L, "기타");
		save(1L, hobbyId, LocalDate.of(2026, 11, 15), "일요일", 10, null, false);
		save(1L, hobbyId, LocalDate.of(2026, 11, 16), "월요일", 20, null, false);

		GrowthResponse response = service.growth(1L, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 22), null);

		assertThat(response.weekly()).extracting(GrowthResponse.Weekly::start)
				.containsExactly(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 16));
		assertThat(response.weekly().get(0).minutes()).isEqualTo(10);
		assertThat(response.weekly().get(1).minutes()).isEqualTo(20);
		// 월요일 칸(0)과 일요일 칸(6)에 각각 1개.
		assertThat(response.weekday()).containsExactly(1, 0, 0, 0, 0, 0, 1);
	}

	@Test
	void growth_feelings_builtInMapping_customCopiedOnRecord_andNoneForUnselected() {
		StatsService service = statsService();
		Long hobbyId = createHobby(1L, "기타");
		LocalDate d = LocalDate.of(2026, 11, 10);
		save(1L, hobbyId, d, "a", 10, Feeling.PROUD, false);
		save(1L, hobbyId, d, "b", 10, Feeling.RELIEVED, false);
		save(1L, hobbyId, d, "c", 10, Feeling.NEUTRAL, false);
		save(1L, hobbyId, d, "d", 10, Feeling.STUCK, false);
		save(1L, hobbyId, d, "e", 10, null, false);
		// 직접 추가한 감정: 감정 목록(custom_feelings)에 행이 없어도(지웠어도) 기록에 복사된 값이 남는다.
		ActivityRecord custom = save(1L, hobbyId, d, "f", 10, null, false);
		custom.setCustomFeeling("설렘", FeelingDirection.GOOD);
		ActivityRecord custom2 = save(1L, hobbyId, d, "g", 10, null, false);
		custom2.setCustomFeeling("설렘", FeelingDirection.GOOD);
		activityRecordRepository.flush();

		GrowthResponse.Feelings feelings = service.growth(1L, d, d, null).feelings();

		assertThat(feelings.totals()).isEqualTo(new GrowthResponse.Totals(4, 1, 1, 1));
		assertThat(feelings.buckets()).hasSize(1);
		assertThat(feelings.buckets().get(0)).isEqualTo(new GrowthResponse.Bucket(d, d, 4, 1, 1, 1));
		assertThat(feelings.byFeeling().get(0)).isEqualTo(new GrowthResponse.ByFeeling("설렘", "GOOD", false, 2));
		assertThat(feelings.byFeeling()).extracting(GrowthResponse.ByFeeling::label)
				.contains("뿌듯함", "개운함", "담담함", "답답함");
		assertThat(feelings.byFeeling()).filteredOn(GrowthResponse.ByFeeling::builtIn).hasSize(4);
	}

	@Test
	void growth_bucketsAreDailyUpTo31Days_andWeeklyBeyond() {
		StatsService service = statsService();
		LocalDate start = LocalDate.of(2026, 10, 1);

		assertThat(service.growth(1L, start, start.plusDays(30), null).feelings().buckets()).hasSize(31);
		List<GrowthResponse.Bucket> weekly = service.growth(1L, start, start.plusDays(31), null).feelings().buckets();
		assertThat(weekly).allSatisfy(b -> {
			assertThat(b.start().getDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY);
			assertThat(b.end()).isEqualTo(b.start().plusDays(6));
		});
	}

	@Test
	void growth_archivedHobby_isMarkedArchived_andKeepsFirstDateEvenOutsideRange() {
		StatsService service = statsService();
		Hobby hobby = hobbyRepository.save(new Hobby(1L, "보관한 분야", null));
		save(1L, hobby.getId(), LocalDate.of(2026, 1, 5), "예전", 10, null, false);
		save(1L, hobby.getId(), LocalDate.of(2026, 11, 10), "최근", 10, null, false);
		hobby.archive(java.time.LocalDateTime.of(2026, 11, 11, 0, 0));
		hobbyRepository.flush();

		GrowthResponse response = service.growth(1L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), null);

		assertThat(response.byHobby()).hasSize(1);
		assertThat(response.byHobby().get(0).archived()).isTrue();
		assertThat(response.byHobby().get(0).records()).isEqualTo(1);
		assertThat(response.byHobby().get(0).firstDate()).isEqualTo(LocalDate.of(2026, 1, 5));
		assertThat(response.summary().firstRecordDate()).isEqualTo(LocalDate.of(2026, 1, 5));
	}

	@Test
	void growth_emptyPeriod_isAllZerosAndEmptyLists() {
		StatsService service = statsService();
		createHobby(1L, "기타");

		GrowthResponse response = service.growth(1L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 14), null);

		assertThat(response.summary()).isEqualTo(new GrowthResponse.Summary(0, 0, 0, null));
		assertThat(response.weekly()).hasSize(3).allSatisfy(w -> {
			assertThat(w.days()).isZero();
			assertThat(w.minutes()).isZero();
		});
		assertThat(response.byHobby()).isEmpty();
		assertThat(response.weekday()).containsExactly(0, 0, 0, 0, 0, 0, 0);
		assertThat(response.topics()).isEmpty();
		assertThat(response.milestones()).isEmpty();
		assertThat(response.feelings().byFeeling()).isEmpty();
	}

	@Test
	void growth_milestones_latestFirst_previewCutAt40_andTopicsFromExistingTags() {
		StatsService service = statsService();
		Long hobbyId = createHobby(1L, "기타");
		String longText = "가".repeat(60);
		ActivityRecord older = save(1L, hobbyId, LocalDate.of(2026, 11, 3), "처음 해낸 날", 10, null, true);
		ActivityRecord newer = save(1L, hobbyId, LocalDate.of(2026, 11, 9), longText, 10, null, true);
		save(1L, hobbyId, LocalDate.of(2026, 11, 10), "평범한 날", 10, null, false);
		older.markAnalysisDone("{\"tags\":[\"코드\",\"리듬\"]}");
		newer.markAnalysisDone("{\"tags\":[\"코드\"]}");
		activityRecordRepository.flush();

		GrowthResponse response = service.growth(1L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), null);

		assertThat(response.milestones()).extracting(GrowthResponse.Milestone::recordId)
				.containsExactly(newer.getId(), older.getId());
		assertThat(response.milestones().get(0).preview()).hasSize(40);
		assertThat(response.milestones().get(0).hobbyName()).isEqualTo("기타");
		assertThat(response.topics()).extracting(WeekTagCount::tag).containsExactly("코드", "리듬");
		assertThat(response.topics().get(0).count()).isEqualTo(2);
	}

	@Test
	void growth_rejectsRangeOver400Days_andReversedRange() {
		StatsService service = statsService();
		LocalDate d = LocalDate.of(2026, 11, 1);

		assertThatThrownBy(() -> service.growth(1L, d.minusDays(401), d, null)).isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> service.growth(1L, d, d.minusDays(1), null)).isInstanceOf(ApiException.class);
		assertThat(service.growth(1L, null, d, null).weekly()).hasSizeLessThanOrEqualTo(52);
	}
}
