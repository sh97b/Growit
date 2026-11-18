package com.growit.dot.repository;

import com.growit.dot.entity.DotGoal;
import com.growit.dot.entity.DotGoalState;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DotGoalRepository extends JpaRepository<DotGoal, Long> {

	Optional<DotGoal> findFirstByUserIdAndState(Long userId, DotGoalState state);

	Optional<DotGoal> findByIdAndUserId(Long id, Long userId);

	/** 잠그기 전에 id만 읽는다. 엔티티를 먼저 읽어 두면 잠근 뒤에도 오래된 상태를 들고 있을 수 있어서다. */
	@Query("select g.id from DotGoal g where g.userId = :userId and g.state = :state")
	Optional<Long> findIdByUserIdAndState(@Param("userId") Long userId, @Param("state") DotGoalState state);

	Optional<DotGoal> findFirstByUserIdAndStateAndCompletionSeenFalseOrderByCompletedOnDescIdDesc(Long userId,
			DotGoalState state);

	List<DotGoal> findByUserIdAndStateOrderByCompletedOnDescIdDesc(Long userId, DotGoalState state);

	/** 같은 목표에 동시에 칸을 더하거나 완성 처리하는 요청을 한 줄로 세우려고 행을 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select g from DotGoal g where g.id = :id")
	Optional<DotGoal> findByIdForUpdate(@Param("id") Long id);

	@Modifying(flushAutomatically = true)
	@Query("delete from DotGoal g where g.userId = :userId")
	void deleteByUserId(@Param("userId") Long userId);
}
