package com.growit.stats.service;

import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.record.entity.ActivityRecord;
import com.growit.record.entity.Feeling;
import com.growit.record.repository.ActivityRecordRepository;
import com.growit.stats.dto.ActivityMapEntry;
import com.growit.stats.dto.StatsSummaryResponse;
import com.growit.stats.dto.WeekDay;
import com.growit.stats.dto.WeekDayRecord;
import com.growit.stats.dto.WeekDetailResponse;
import com.growit.stats.dto.WeekFeelingCounts;
import com.growit.stats.dto.WeekHobbySummary;
import com.growit.stats.dto.WeekSummaryResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StatsService {

	private static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Seoul");
	private static final long MAX_RANGE_DAYS = 400;

	private final ActivityRecordRepository activityRecordRepository;
	private final HobbyRepository hobbyRepository;

	public StatsService(ActivityRecordRepository activityRecordRepository, HobbyRepository hobbyRepository) {
		this.activityRecordRepository = activityRecordRepository;
		this.hobbyRepository = hobbyRepository;
	}

	/** "오늘"과 쉰 일수는 서버 시간대(Asia/Seoul) 기준이다. 사용자의 로컬 시간이 아니다. */
	public StatsSummaryResponse summary(Long userId) {
		LocalDate today = LocalDate.now(SERVER_ZONE);
		long recordedDays = activityRecordRepository.countDistinctRecordDateByUserId(userId);
		long totalMinutes = activityRecordRepository.sumMinutesByUserId(userId);
		long hobbyCount = hobbyRepository.countByUserIdAndArchivedAtIsNull(userId);
		LocalDate lastRecordedDate = activityRecordRepository.findMaxRecordDateByUserId(userId);
		Long daysSinceLastRecord = lastRecordedDate == null ? null
				: ChronoUnit.DAYS.between(lastRecordedDate, today);
		boolean todayRecorded = lastRecordedDate != null && lastRecordedDate.isEqual(today);

		return new StatsSummaryResponse(recordedDays, totalMinutes, hobbyCount, lastRecordedDate,
				daysSinceLastRecord, todayRecorded, today);
	}

	/** 날짜·분야별로 집계하고, 보관된 분야의 기록도 포함한다(지난 흐름이 끊겨 보이지 않게). */
	public List<ActivityMapEntry> activityMap(Long userId, LocalDate from, LocalDate to, Long hobbyId) {
		if (from.isAfter(to)) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "시작일이 끝일보다 늦을 수 없어요");
		}
		if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "조회 범위는 400일을 넘을 수 없어요");
		}
		if (hobbyId != null) {
			hobbyRepository.findByIdAndUserId(hobbyId, userId)
					.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "활동 분야를 찾을 수 없어요"));
		}

		return activityRecordRepository.findActivityMap(userId, from, to, hobbyId).stream()
				.map(row -> new ActivityMapEntry(row.getRecordDate(), row.getHobbyId(), row.getRecordCount(),
						row.getTotalMinutes()))
				.toList();
	}

	/** 월요일 시작, Asia/Seoul 기준. 보관된 분야의 기록도 포함한다. */
	public List<WeekSummaryResponse> weeks(Long userId, LocalDate from, LocalDate to, Long hobbyId) {
		validateRange(from, to);
		if (hobbyId != null) {
			requireOwnedHobby(userId, hobbyId);
		}

		LocalDate firstWeekStart = weekStartOf(from);
		LocalDate lastWeekStart = weekStartOf(to);

		List<ActivityRecord> records = hobbyId == null
				? activityRecordRepository.findByUserIdAndRecordDateBetween(userId, firstWeekStart,
						lastWeekStart.plusDays(6))
				: activityRecordRepository.findByUserIdAndHobbyIdAndRecordDateBetween(userId, hobbyId, firstWeekStart,
						lastWeekStart.plusDays(6));

		Map<LocalDate, List<ActivityRecord>> byWeek = records.stream()
				.collect(Collectors.groupingBy(r -> weekStartOf(r.getRecordDate())));

		List<WeekSummaryResponse> result = new ArrayList<>();
		for (LocalDate weekStart = firstWeekStart; !weekStart.isAfter(lastWeekStart); weekStart = weekStart.plusDays(7)) {
			List<ActivityRecord> weekRecords = byWeek.getOrDefault(weekStart, List.of());
			result.add(summarizeWeek(weekStart, weekRecords));
		}
		return result;
	}

	private WeekSummaryResponse summarizeWeek(LocalDate weekStart, List<ActivityRecord> weekRecords) {
		long recordedDays = weekRecords.stream().map(ActivityRecord::getRecordDate).distinct().count();
		long totalMinutes = weekRecords.stream().mapToLong(r -> r.getMinutes() == null ? 0 : r.getMinutes()).sum();

		Map<Long, List<ActivityRecord>> byHobby = weekRecords.stream()
				.collect(Collectors.groupingBy(ActivityRecord::getHobbyId));
		List<WeekHobbySummary> hobbies = byHobby.entrySet().stream()
				.map(e -> new WeekHobbySummary(e.getKey(),
						(int) e.getValue().stream().map(ActivityRecord::getRecordDate).distinct().count()))
				.sorted(Comparator.comparing(WeekHobbySummary::hobbyId))
				.toList();

		Map<Feeling, Long> feelingCounts = weekRecords.stream()
				.filter(r -> r.getFeeling() != null)
				.collect(Collectors.groupingBy(ActivityRecord::getFeeling, Collectors.counting()));
		WeekFeelingCounts feelings = new WeekFeelingCounts(
				feelingCounts.getOrDefault(Feeling.PROUD, 0L),
				feelingCounts.getOrDefault(Feeling.STUCK, 0L),
				feelingCounts.getOrDefault(Feeling.RELIEVED, 0L),
				feelingCounts.getOrDefault(Feeling.NEUTRAL, 0L));

		return new WeekSummaryResponse(weekStart, (int) recordedDays, weekRecords.size(), totalMinutes, hobbies,
				feelings);
	}

	/** 요청한 주가 미래로 넘어가면(오늘이 속한 주의 시작보다 늦으면) nextWeekStart는 null이다. */
	public WeekDetailResponse weekDetail(Long userId, LocalDate weekStart, Long hobbyId) {
		if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "weekStart는 월요일이어야 해요");
		}
		if (hobbyId != null) {
			requireOwnedHobby(userId, hobbyId);
		}

		LocalDate weekEnd = weekStart.plusDays(6);
		List<ActivityRecord> weekRecords = hobbyId == null
				? activityRecordRepository.findByUserIdAndRecordDateBetween(userId, weekStart, weekEnd)
				: activityRecordRepository.findByUserIdAndHobbyIdAndRecordDateBetween(userId, hobbyId, weekStart,
						weekEnd);

		Map<Long, String> hobbyNames = hobbyRepository.findByUserIdOrderByIdAsc(userId).stream()
				.collect(Collectors.toMap(Hobby::getId, Hobby::getName));

		Map<LocalDate, List<ActivityRecord>> byDate = weekRecords.stream()
				.collect(Collectors.groupingBy(ActivityRecord::getRecordDate));

		List<WeekDay> days = new ArrayList<>();
		for (LocalDate date = weekStart; !date.isAfter(weekEnd); date = date.plusDays(1)) {
			List<WeekDayRecord> dayRecords = byDate.getOrDefault(date, List.of()).stream()
					.map(r -> new WeekDayRecord(r.getId(), r.getHobbyId(),
							hobbyNames.getOrDefault(r.getHobbyId(), ""), r.getContent(), r.getMinutes(),
							r.getFeeling()))
					.toList();
			days.add(new WeekDay(date, dayRecords));
		}

		long recordedDays = byDate.size();
		long totalMinutes = weekRecords.stream().mapToLong(r -> r.getMinutes() == null ? 0 : r.getMinutes()).sum();
		long hobbyCount = weekRecords.stream().map(ActivityRecord::getHobbyId).distinct().count();

		LocalDate today = LocalDate.now(SERVER_ZONE);
		LocalDate currentWeekStart = weekStartOf(today);
		LocalDate nextWeekStart = weekStart.isBefore(currentWeekStart) ? weekStart.plusDays(7) : null;

		return new WeekDetailResponse(weekStart, weekEnd, (int) recordedDays, totalMinutes, hobbyCount, days,
				weekStart.minusDays(7), nextWeekStart);
	}

	private void validateRange(LocalDate from, LocalDate to) {
		if (from.isAfter(to)) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "시작일이 끝일보다 늦을 수 없어요");
		}
		if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "조회 범위는 400일을 넘을 수 없어요");
		}
	}

	private void requireOwnedHobby(Long userId, Long hobbyId) {
		hobbyRepository.findByIdAndUserId(hobbyId, userId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "활동 분야를 찾을 수 없어요"));
	}

	private static LocalDate weekStartOf(LocalDate date) {
		return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}
}
