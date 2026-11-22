package com.growit.record.repository;

import com.growit.record.entity.ActivityRecord;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityRecordRepository
		extends JpaRepository<ActivityRecord, Long>, JpaSpecificationExecutor<ActivityRecord> {

	Optional<ActivityRecord> findByIdAndUserId(Long id, Long userId);

	List<ActivityRecord> findByUserId(Long userId);

	long countByUserId(Long userId);

	/** AI 회고 자동 생성 조건(마지막 COACH 생성 시각 이후에 저장된 기록) 계산에 쓴다(O1 E). */
	List<ActivityRecord> findByUserIdAndCreatedAtGreaterThan(Long userId, LocalDateTime since);

	void deleteByUserId(Long userId);

	boolean existsByHobbyId(Long hobbyId);

	long countByHobbyId(Long hobbyId);

	/** 로드맵 생성 때 현재 수준을 가늠할 최근 기록(분야별). */
	List<ActivityRecord> findByUserIdAndHobbyIdOrderByRecordDateDescIdDesc(Long userId, Long hobbyId,
			org.springframework.data.domain.Pageable pageable);

	/** 같은 분야 안에서 {@code (recordDate, id)} 순으로 바로 앞 1건을 찾는다. id를 함께 정렬해 같은 날짜가 여러 건이어도 순서가 흔들리지 않게 한다. */
	@Query("select r from ActivityRecord r where r.userId = :userId and r.hobbyId = :hobbyId "
			+ "and (r.recordDate < :recordDate or (r.recordDate = :recordDate and r.id < :id)) "
			+ "order by r.recordDate desc, r.id desc")
	List<ActivityRecord> findPrevious(@Param("userId") Long userId, @Param("hobbyId") Long hobbyId,
			@Param("recordDate") LocalDate recordDate, @Param("id") Long id, Pageable pageable);

	/** 분야와 상관없이 사용자의 바로 앞 기록(경과 일수 계산용). */
	@Query("select r from ActivityRecord r where r.userId = :userId "
			+ "and (r.recordDate < :recordDate or (r.recordDate = :recordDate and r.id < :id)) "
			+ "order by r.recordDate desc, r.id desc")
	List<ActivityRecord> findPreviousOfUser(@Param("userId") Long userId, @Param("recordDate") LocalDate recordDate,
			@Param("id") Long id, Pageable pageable);

	Optional<ActivityRecord> findFirstByUserIdOrderByRecordDateDescIdDesc(Long userId);

	/** {@link #findPrevious}와 같은 기준으로 반대 방향을 찾는다. */
	@Query("select r from ActivityRecord r where r.userId = :userId and r.hobbyId = :hobbyId "
			+ "and (r.recordDate > :recordDate or (r.recordDate = :recordDate and r.id > :id)) "
			+ "order by r.recordDate asc, r.id asc")
	List<ActivityRecord> findNext(@Param("userId") Long userId, @Param("hobbyId") Long hobbyId,
			@Param("recordDate") LocalDate recordDate, @Param("id") Long id, Pageable pageable);

	@Query("select count(distinct r.recordDate) from ActivityRecord r where r.userId = :userId")
	long countDistinctRecordDateByUserId(@Param("userId") Long userId);

	@Query("select coalesce(sum(r.minutes), 0) from ActivityRecord r where r.userId = :userId")
	long sumMinutesByUserId(@Param("userId") Long userId);

	@Query("select max(r.recordDate) from ActivityRecord r where r.userId = :userId")
	LocalDate findMaxRecordDateByUserId(@Param("userId") Long userId);

	@Query("select min(r.recordDate) from ActivityRecord r where r.userId = :userId")
	LocalDate findMinRecordDateByUserId(@Param("userId") Long userId);

	Optional<ActivityRecord> findFirstByUserIdAndHobbyIdOrderByRecordDateDescIdDesc(Long userId, Long hobbyId);

	Optional<ActivityRecord> findFirstByUserIdAndHobbyIdAndRecordDateLessThanEqualOrderByRecordDateDescIdDesc(
			Long userId, Long hobbyId, LocalDate recordDate);

	Optional<ActivityRecord> findFirstByUserIdAndHobbyIdOrderByRecordDateAscIdAsc(Long userId, Long hobbyId);

	@Query("select count(distinct r.recordDate) from ActivityRecord r "
			+ "where r.userId = :userId and r.hobbyId = :hobbyId and r.recordDate <= :onOrBefore")
	long countDistinctRecordDateByUserIdAndHobbyIdUpTo(@Param("userId") Long userId, @Param("hobbyId") Long hobbyId,
			@Param("onOrBefore") LocalDate onOrBefore);

	@Query("select coalesce(sum(r.minutes), 0) from ActivityRecord r "
			+ "where r.userId = :userId and r.hobbyId = :hobbyId and r.recordDate <= :onOrBefore")
	long sumMinutesByUserIdAndHobbyIdUpTo(@Param("userId") Long userId, @Param("hobbyId") Long hobbyId,
			@Param("onOrBefore") LocalDate onOrBefore);

	long countByUserIdAndHobbyId(Long userId, Long hobbyId);

	List<ActivityRecord> findByUserIdAndRecordDateBetween(Long userId, LocalDate from, LocalDate to);

	List<ActivityRecord> findByUserIdAndHobbyIdAndRecordDateBetween(Long userId, Long hobbyId, LocalDate from,
			LocalDate to);

	@Query("select max(r.recordDate) from ActivityRecord r where r.userId = :userId and r.hobbyId = :hobbyId")
	LocalDate findMaxRecordDateByUserIdAndHobbyId(@Param("userId") Long userId, @Param("hobbyId") Long hobbyId);

	/** 분야별 첫 기록일(기간과 상관없이). 성장 화면의 "첫 기록일"에 쓴다. */
	@Query("select r.hobbyId, min(r.recordDate) from ActivityRecord r where r.userId = :userId group by r.hobbyId")
	List<Object[]> findFirstRecordDatesByHobby(@Param("userId") Long userId);

	/** 날짜·분야별로 묶어 집계한다. 보관된 분야의 기록도 포함해서, 지난 흐름이 끊겨 보이지 않게 한다. */
	@Query("select r.recordDate as recordDate, r.hobbyId as hobbyId, count(r) as recordCount, "
			+ "coalesce(sum(r.minutes), 0) as totalMinutes from ActivityRecord r "
			+ "where r.userId = :userId and r.recordDate between :from and :to "
			+ "and (:hobbyId is null or r.hobbyId = :hobbyId) "
			+ "group by r.recordDate, r.hobbyId order by r.recordDate asc")
	List<ActivityMapRow> findActivityMap(@Param("userId") Long userId, @Param("from") LocalDate from,
			@Param("to") LocalDate to, @Param("hobbyId") Long hobbyId);
}
