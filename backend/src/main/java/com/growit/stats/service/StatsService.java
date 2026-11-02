package com.growit.stats.service;

import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.record.repository.ActivityRecordRepository;
import com.growit.stats.dto.ActivityMapEntry;
import com.growit.stats.dto.StatsSummaryResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
}
