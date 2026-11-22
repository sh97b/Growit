package com.growit.stats.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.record.entity.ActivityRecord;
import com.growit.record.entity.AiStatus;
import com.growit.record.entity.Feeling;
import com.growit.record.repository.ActivityRecordRepository;
import com.growit.stats.dto.ActivityMapEntry;
import com.growit.stats.dto.GrowthResponse;
import com.growit.stats.dto.StatsSummaryResponse;
import com.growit.stats.dto.WeekDay;
import com.growit.stats.dto.WeekDayRecord;
import com.growit.stats.dto.WeekDetailResponse;
import com.growit.stats.dto.WeekFeelingCounts;
import com.growit.stats.dto.WeekHobbySummary;
import com.growit.stats.dto.WeekSummaryResponse;
import com.growit.stats.dto.WeekTagCount;
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
	private static final int MAX_GROWTH_WEEKS = 52;
	private static final int DAILY_BUCKET_MAX_DAYS = 31;
	private static final int MAX_TOPICS = 8;
	private static final int MAX_MILESTONES = 12;
	private static final int MAX_FEELING_CHIPS = 12;
	private static final int PREVIEW_MAX = 40;

	private final ActivityRecordRepository activityRecordRepository;
	private final HobbyRepository hobbyRepository;
	private final ObjectMapper objectMapper;

	public StatsService(ActivityRecordRepository activityRecordRepository, HobbyRepository hobbyRepository,
			ObjectMapper objectMapper) {
		this.activityRecordRepository = activityRecordRepository;
		this.hobbyRepository = hobbyRepository;
		this.objectMapper = objectMapper;
	}

	/** "오늘"과 쉰 일수는 서버 시간대(Asia/Seoul) 기준이다. 사용자의 로컬 시간이 아니다. */
	public StatsSummaryResponse summary(Long userId) {
		LocalDate today = LocalDate.now(SERVER_ZONE);
		long recordedDays = activityRecordRepository.countDistinctRecordDateByUserId(userId);
		long totalMinutes = activityRecordRepository.sumMinutesByUserId(userId);
		long hobbyCount = hobbyRepository.countByUserIdAndArchivedAtIsNull(userId);
		LocalDate lastRecordedDate = activityRecordRepository.findMaxRecordDateByUserId(userId);
		LocalDate firstRecordedDate = activityRecordRepository.findMinRecordDateByUserId(userId);
		Long daysSinceLastRecord = lastRecordedDate == null ? null
				: ChronoUnit.DAYS.between(lastRecordedDate, today);
		boolean todayRecorded = lastRecordedDate != null && lastRecordedDate.isEqual(today);

		return new StatsSummaryResponse(recordedDays, totalMinutes, hobbyCount, lastRecordedDate,
				daysSinceLastRecord, todayRecorded, today, firstRecordedDate);
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

		List<WeekTagCount> tags = computeTopTags(weekRecords);

		return new WeekSummaryResponse(weekStart, (int) recordedDays, weekRecords.size(), totalMinutes, hobbies,
				feelings, tags);
	}

	/** ai_status=DONE 기록의 analysis.tags를 세어 상위 5개만 돌려준다. */
	private List<WeekTagCount> computeTopTags(List<ActivityRecord> weekRecords) {
		return computeTopTags(weekRecords, 5);
	}

	private List<WeekTagCount> computeTopTags(List<ActivityRecord> weekRecords, int limit) {
		Map<String, Long> counts = new java.util.LinkedHashMap<>();
		for (ActivityRecord record : weekRecords) {
			if (record.getAiStatus() != AiStatus.DONE) {
				continue;
			}
			for (String tag : extractTags(record.getAiAnalysis())) {
				counts.merge(tag, 1L, Long::sum);
			}
		}
		return counts.entrySet().stream()
				.sorted(Map.Entry.<String, Long>comparingByValue().reversed())
				.limit(limit)
				.map(entry -> new WeekTagCount(entry.getKey(), entry.getValue()))
				.toList();
	}

	/** 기록의 태그는 record 도메인의 저장 모양(StoredAnalysis)을 몰라도 되게, tags 칸만 가볍게 읽는다. */
	private List<String> extractTags(String aiAnalysisJson) {
		if (aiAnalysisJson == null) {
			return List.of();
		}
		try {
			JsonNode node = objectMapper.readTree(aiAnalysisJson).path("tags");
			if (!node.isArray()) {
				return List.of();
			}
			List<String> tags = new ArrayList<>();
			node.forEach(tagNode -> {
				if (tagNode.isTextual()) {
					tags.add(tagNode.asText());
				}
			});
			return tags;
		} catch (JsonProcessingException e) {
			return List.of();
		}
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

	/**
	 * 성장 화면용 집계. from이 없으면 to(기본 오늘)로부터 400일 전부터, 범위가 400일을 넘으면 400(주별 집계와 같은 상한).
	 * 주는 월요일 시작, 날짜는 Asia/Seoul 기준이다. 감정 방향은 기본 감정은 고정 매핑, 직접 추가한 감정은 기록에 복사된 값을 쓴다.
	 */
	public GrowthResponse growth(Long userId, LocalDate from, LocalDate to, Long hobbyId) {
		LocalDate end = to != null ? to : LocalDate.now(SERVER_ZONE);
		LocalDate start = from != null ? from : end.minusDays(MAX_RANGE_DAYS);
		validateRange(start, end);
		if (hobbyId != null) {
			requireOwnedHobby(userId, hobbyId);
		}

		List<ActivityRecord> records = hobbyId == null
				? activityRecordRepository.findByUserIdAndRecordDateBetween(userId, start, end)
				: activityRecordRepository.findByUserIdAndHobbyIdAndRecordDateBetween(userId, hobbyId, start, end);
		List<Hobby> hobbies = hobbyRepository.findByUserIdOrderByIdAsc(userId);
		Map<Long, Hobby> hobbyById = hobbies.stream().collect(Collectors.toMap(Hobby::getId, h -> h));
		Map<Long, LocalDate> firstDates = new HashMap<>();
		for (Object[] row : activityRecordRepository.findFirstRecordDatesByHobby(userId)) {
			firstDates.put((Long) row[0], (LocalDate) row[1]);
		}

		LocalDate firstRecordDate = hobbyId != null ? firstDates.get(hobbyId)
				: firstDates.values().stream().min(LocalDate::compareTo).orElse(null);
		GrowthResponse.Summary summary = new GrowthResponse.Summary(
				(int) records.stream().map(ActivityRecord::getRecordDate).distinct().count(), records.size(),
				minutesOf(records), firstRecordDate);

		// 주별: 기록 없는 주도 0으로 채우고, 너무 길면 최근 52주만.
		Map<LocalDate, List<ActivityRecord>> byWeek = records.stream()
				.collect(Collectors.groupingBy(r -> weekStartOf(r.getRecordDate())));
		List<GrowthResponse.Weekly> weekly = new ArrayList<>();
		for (LocalDate week = weekStartOf(start); !week.isAfter(weekStartOf(end)); week = week.plusDays(7)) {
			List<ActivityRecord> weekRecords = byWeek.getOrDefault(week, List.of());
			// 돌아보기의 주별 집계(/weeks)와 같은 계산을 쓴다.
			WeekSummaryResponse week7 = summarizeWeek(week, weekRecords);
			weekly.add(new GrowthResponse.Weekly(week, week7.recordedDays(), week7.totalMinutes()));
		}
		if (weekly.size() > MAX_GROWTH_WEEKS) {
			weekly = new ArrayList<>(weekly.subList(weekly.size() - MAX_GROWTH_WEEKS, weekly.size()));
		}

		Map<Long, List<ActivityRecord>> byHobbyRecords = records.stream()
				.collect(Collectors.groupingBy(ActivityRecord::getHobbyId));
		List<GrowthResponse.ByHobby> byHobby = byHobbyRecords.entrySet().stream()
				.map(entry -> {
					Hobby hobby = hobbyById.get(entry.getKey());
					return new GrowthResponse.ByHobby(entry.getKey(), hobby == null ? "" : hobby.getName(),
							entry.getValue().size(), minutesOf(entry.getValue()), firstDates.get(entry.getKey()),
							hobby != null && hobby.isArchived());
				})
				.sorted(Comparator.comparingLong(GrowthResponse.ByHobby::records).reversed()
						.thenComparing(GrowthResponse.ByHobby::hobbyId))
				.toList();

		int[] weekdayCounts = new int[7];
		for (ActivityRecord record : records) {
			weekdayCounts[record.getRecordDate().getDayOfWeek().getValue() - 1]++;
		}
		List<Integer> weekday = java.util.Arrays.stream(weekdayCounts).boxed().toList();

		List<GrowthResponse.Milestone> milestones = records.stream().filter(ActivityRecord::isMilestone)
				.sorted(Comparator.comparing(ActivityRecord::getRecordDate).reversed()
						.thenComparing(ActivityRecord::getId, Comparator.reverseOrder()))
				.limit(MAX_MILESTONES)
				.map(r -> new GrowthResponse.Milestone(r.getId(), r.getRecordDate(),
						hobbyById.containsKey(r.getHobbyId()) ? hobbyById.get(r.getHobbyId()).getName() : "",
						previewOf(r.getContent())))
				.toList();

		return new GrowthResponse(summary, weekly, byHobby, weekday, growthFeelings(records, start, end),
				computeTopTags(records, MAX_TOPICS), milestones);
	}

	private GrowthResponse.Feelings growthFeelings(List<ActivityRecord> records, LocalDate start, LocalDate end) {
		boolean daily = ChronoUnit.DAYS.between(start, end) + 1 <= DAILY_BUCKET_MAX_DAYS;
		Map<LocalDate, long[]> bucketCounts = new java.util.LinkedHashMap<>();
		if (daily) {
			for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
				bucketCounts.put(d, new long[4]);
			}
		} else {
			for (LocalDate w = weekStartOf(start); !w.isAfter(weekStartOf(end)); w = w.plusDays(7)) {
				bucketCounts.put(w, new long[4]);
			}
		}
		Map<String, long[]> byLabel = new HashMap<>();
		Map<String, String> labelDirection = new HashMap<>();
		Map<String, Boolean> labelBuiltIn = new HashMap<>();
		long[] totals = new long[4];
		for (ActivityRecord record : records) {
			String direction = directionOf(record);
			int slot = direction == null ? 3 : switch (direction) {
				case "GOOD" -> 0;
				case "NEUTRAL" -> 1;
				default -> 2;
			};
			bucketCounts.get(daily ? record.getRecordDate() : weekStartOf(record.getRecordDate()))[slot]++;
			totals[slot]++;
			String label = labelOf(record);
			if (label != null && direction != null) {
				String key = (record.getFeeling() != null ? "B:" : "C:") + label;
				byLabel.computeIfAbsent(key, k -> new long[1])[0]++;
				labelDirection.put(key, direction);
				labelBuiltIn.put(key, record.getFeeling() != null);
			}
		}
		List<GrowthResponse.Bucket> buckets = bucketCounts.entrySet().stream()
				.map(e -> new GrowthResponse.Bucket(e.getKey(), daily ? e.getKey() : e.getKey().plusDays(6),
						e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]))
				.toList();
		List<GrowthResponse.ByFeeling> byFeeling = byLabel.entrySet().stream()
				.map(e -> new GrowthResponse.ByFeeling(e.getKey().substring(2), labelDirection.get(e.getKey()),
						labelBuiltIn.get(e.getKey()), e.getValue()[0]))
				.sorted(Comparator.comparingLong(GrowthResponse.ByFeeling::count).reversed()
						.thenComparing(GrowthResponse.ByFeeling::label))
				.limit(MAX_FEELING_CHIPS)
				.toList();
		return new GrowthResponse.Feelings(buckets, byFeeling,
				new GrowthResponse.Totals(totals[0], totals[1], totals[2], totals[3]));
	}

	/** 기본 감정은 고정 매핑, 직접 추가한 감정은 기록에 복사된 방향(감정 목록에서 지워도 남는다). 안 고르면 null. */
	private static String directionOf(ActivityRecord record) {
		if (record.getFeeling() != null) {
			return switch (record.getFeeling()) {
				case PROUD, RELIEVED -> "GOOD";
				case NEUTRAL -> "NEUTRAL";
				case STUCK -> "HARD";
			};
		}
		return record.getFeelingLabel() == null ? null : record.getFeelingDirection();
	}

	private static String labelOf(ActivityRecord record) {
		if (record.getFeeling() != null) {
			return switch (record.getFeeling()) {
				case PROUD -> "뿌듯함";
				case RELIEVED -> "개운함";
				case NEUTRAL -> "담담함";
				case STUCK -> "답답함";
			};
		}
		return record.getFeelingLabel();
	}

	private static long minutesOf(List<ActivityRecord> records) {
		return records.stream().mapToLong(r -> r.getMinutes() == null ? 0 : r.getMinutes()).sum();
	}

	private static String previewOf(String content) {
		String flat = content == null ? "" : content.replaceAll("\\s+", " ").trim();
		return flat.length() > PREVIEW_MAX ? flat.substring(0, PREVIEW_MAX) : flat;
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
