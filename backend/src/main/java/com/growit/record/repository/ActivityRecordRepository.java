package com.growit.record.repository;

import com.growit.record.entity.ActivityRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@link ActivityRecord} 조회. 거의 모든 메서드가 {@code userId}로 범위를 제한해 남의 기록이 섞이지 않게 한다.
 */
public interface ActivityRecordRepository
		extends JpaRepository<ActivityRecord, Long>, JpaSpecificationExecutor<ActivityRecord> {

	/** 소유자 확인을 겸한 단건 조회. 없거나 내 것이 아니면 빈 값. */
	Optional<ActivityRecord> findByIdAndUserId(Long id, Long userId);

	/** 분야 삭제 전 "기록이 하나라도 있는지" 검사에 쓰인다(보관 여부와 무관, 사용자 제한 없음 — 호출 쪽에서 분야 소유를 이미 확인함). */
	boolean existsByHobbyId(Long hobbyId);

	/** 활동 분야 응답의 {@code recordCount}를 채우는 데 쓰인다. */
	long countByHobbyId(Long hobbyId);

	/** 기록 상세의 "이전 기록" 링크용. {@code (recordDate, id)} 기준으로 바로 앞 1건을 찾는다(호출부에서 {@code Pageable.ofSize(1)} 사용). */
	@Query("select r from ActivityRecord r where r.userId = :userId and r.hobbyId = :hobbyId "
			+ "and (r.recordDate < :recordDate or (r.recordDate = :recordDate and r.id < :id)) "
			+ "order by r.recordDate desc, r.id desc")
	List<ActivityRecord> findPrevious(@Param("userId") Long userId, @Param("hobbyId") Long hobbyId,
			@Param("recordDate") LocalDate recordDate, @Param("id") Long id, Pageable pageable);

	/** 기록 상세의 "다음 기록" 링크용. {@link #findPrevious}와 반대 방향. */
	@Query("select r from ActivityRecord r where r.userId = :userId and r.hobbyId = :hobbyId "
			+ "and (r.recordDate > :recordDate or (r.recordDate = :recordDate and r.id > :id)) "
			+ "order by r.recordDate asc, r.id asc")
	List<ActivityRecord> findNext(@Param("userId") Long userId, @Param("hobbyId") Long hobbyId,
			@Param("recordDate") LocalDate recordDate, @Param("id") Long id, Pageable pageable);

	/** 홈 통계의 "기록한 날 수"(같은 날 여러 건을 써도 하루로 센다). */
	@Query("select count(distinct r.recordDate) from ActivityRecord r where r.userId = :userId")
	long countDistinctRecordDateByUserId(@Param("userId") Long userId);

	/** 홈 통계의 "누적 연습 시간"(분). 기록이 없으면 0. */
	@Query("select coalesce(sum(r.minutes), 0) from ActivityRecord r where r.userId = :userId")
	long sumMinutesByUserId(@Param("userId") Long userId);

	/** 홈 통계의 "마지막 기록 날짜". 기록이 없으면 null. */
	@Query("select max(r.recordDate) from ActivityRecord r where r.userId = :userId")
	LocalDate findMaxRecordDateByUserId(@Param("userId") Long userId);

	/** 활동 지도용 날짜·분야별 집계. {@code hobbyId}가 null이면 전체 분야를 더한다(보관된 분야의 기록도 포함). */
	@Query("select r.recordDate as recordDate, r.hobbyId as hobbyId, count(r) as recordCount, "
			+ "coalesce(sum(r.minutes), 0) as totalMinutes from ActivityRecord r "
			+ "where r.userId = :userId and r.recordDate between :from and :to "
			+ "and (:hobbyId is null or r.hobbyId = :hobbyId) "
			+ "group by r.recordDate, r.hobbyId order by r.recordDate asc")
	List<ActivityMapRow> findActivityMap(@Param("userId") Long userId, @Param("from") LocalDate from,
			@Param("to") LocalDate to, @Param("hobbyId") Long hobbyId);
}
