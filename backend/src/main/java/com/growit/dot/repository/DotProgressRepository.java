package com.growit.dot.repository;

import com.growit.dot.entity.DotProgress;
import com.growit.dot.entity.DotProgressId;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DotProgressRepository extends JpaRepository<DotProgress, DotProgressId> {

	long countByGoalId(Long goalId);

	boolean existsByGoalIdAndRecordDate(Long goalId, LocalDate recordDate);

	@Query("select p.recordDate from DotProgress p where p.goalId = :goalId order by p.recordDate")
	List<LocalDate> findDatesByGoalId(@Param("goalId") Long goalId);

	/** 시작일 이후 기록이 있는 날 중 아직 칠해지지 않은 날. 같은 날 기록이 여럿이어도 한 번만 나온다. */
	@Query("""
			select distinct r.recordDate from ActivityRecord r
			where r.userId = :userId and r.recordDate >= :from
			and not exists (select 1 from DotProgress p where p.goalId = :goalId and p.recordDate = r.recordDate)
			order by r.recordDate
			""")
	List<LocalDate> findMissingDates(@Param("userId") Long userId, @Param("goalId") Long goalId,
			@Param("from") LocalDate from);

	@Modifying(flushAutomatically = true)
	@Query("delete from DotProgress p where p.goalId in (select g.id from DotGoal g where g.userId = :userId)")
	void deleteByUserId(@Param("userId") Long userId);
}
