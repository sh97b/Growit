package com.growit.record;

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

	boolean existsByHobbyId(Long hobbyId);

	long countByHobbyId(Long hobbyId);

	@Query("select r from ActivityRecord r where r.userId = :userId and r.hobbyId = :hobbyId "
			+ "and (r.recordDate < :recordDate or (r.recordDate = :recordDate and r.id < :id)) "
			+ "order by r.recordDate desc, r.id desc")
	List<ActivityRecord> findPrevious(@Param("userId") Long userId, @Param("hobbyId") Long hobbyId,
			@Param("recordDate") LocalDate recordDate, @Param("id") Long id, Pageable pageable);

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

	@Query("select r.recordDate as recordDate, r.hobbyId as hobbyId, count(r) as recordCount, "
			+ "coalesce(sum(r.minutes), 0) as totalMinutes from ActivityRecord r "
			+ "where r.userId = :userId and r.recordDate between :from and :to "
			+ "and (:hobbyId is null or r.hobbyId = :hobbyId) "
			+ "group by r.recordDate, r.hobbyId order by r.recordDate asc")
	List<ActivityMapRow> findActivityMap(@Param("userId") Long userId, @Param("from") LocalDate from,
			@Param("to") LocalDate to, @Param("hobbyId") Long hobbyId);
}
