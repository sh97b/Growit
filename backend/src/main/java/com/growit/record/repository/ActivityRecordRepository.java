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

public interface ActivityRecordRepository
		extends JpaRepository<ActivityRecord, Long>, JpaSpecificationExecutor<ActivityRecord> {

	Optional<ActivityRecord> findByIdAndUserId(Long id, Long userId);

	List<ActivityRecord> findByUserId(Long userId);

	boolean existsByHobbyId(Long hobbyId);

	long countByHobbyId(Long hobbyId);

	/** 같은 분야 안에서 {@code (recordDate, id)} 순으로 바로 앞 1건을 찾는다. id를 함께 정렬해 같은 날짜가 여러 건이어도 순서가 흔들리지 않게 한다. */
	@Query("select r from ActivityRecord r where r.userId = :userId and r.hobbyId = :hobbyId "
			+ "and (r.recordDate < :recordDate or (r.recordDate = :recordDate and r.id < :id)) "
			+ "order by r.recordDate desc, r.id desc")
	List<ActivityRecord> findPrevious(@Param("userId") Long userId, @Param("hobbyId") Long hobbyId,
			@Param("recordDate") LocalDate recordDate, @Param("id") Long id, Pageable pageable);

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

	Optional<ActivityRecord> findFirstByUserIdAndHobbyIdOrderByRecordDateDescIdDesc(Long userId, Long hobbyId);

	Optional<ActivityRecord> findFirstByUserIdAndHobbyIdAndRecordDateLessThanEqualOrderByRecordDateDescIdDesc(
			Long userId, Long hobbyId, LocalDate recordDate);

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

	/** 날짜·분야별로 묶어 집계한다. 보관된 분야의 기록도 포함해서, 지난 흐름이 끊겨 보이지 않게 한다. */
	@Query("select r.recordDate as recordDate, r.hobbyId as hobbyId, count(r) as recordCount, "
			+ "coalesce(sum(r.minutes), 0) as totalMinutes from ActivityRecord r "
			+ "where r.userId = :userId and r.recordDate between :from and :to "
			+ "and (:hobbyId is null or r.hobbyId = :hobbyId) "
			+ "group by r.recordDate, r.hobbyId order by r.recordDate asc")
	List<ActivityMapRow> findActivityMap(@Param("userId") Long userId, @Param("from") LocalDate from,
			@Param("to") LocalDate to, @Param("hobbyId") Long hobbyId);
}
