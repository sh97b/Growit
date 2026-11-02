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

/**
 * 홈 화면과 활동 지도에 쓰이는 집계를 계산한다. 모든 집계는 호출한 사용자의 기록·분야만 본다.
 */
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

	/**
	 * "오늘"은 서버 시간대(Asia/Seoul)의 오늘이고, 쉰 일수·오늘 기록 여부도 이 날짜를 기준으로 계산한다.
	 * 기록이 하나도 없으면 {@code lastRecordedDate}·{@code daysSinceLastRecord}는 null, {@code todayRecorded}는 false다.
	 */
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

	/**
	 * 날짜·분야별 기록 집계. 기록이 있는 날짜·분야 조합만 돌려준다(없는 날은 아예 포함 안 함).
	 * 보관된 분야의 기록도 포함한다(지난 흐름을 그대로 보여주기 위함).
	 *
	 * @throws ApiException {@link ErrorCode#VALIDATION_ERROR} {@code from}이 {@code to}보다 늦거나
	 *                       범위가 400일을 넘을 때, {@link ErrorCode#NOT_FOUND} {@code hobbyId}가 내 분야가 아닐 때
	 */
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
