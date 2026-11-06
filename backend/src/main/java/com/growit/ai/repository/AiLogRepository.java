package com.growit.ai.repository;

import com.growit.ai.entity.AiLog;
import java.time.LocalDateTime;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiLogRepository extends JpaRepository<AiLog, Long> {

	void deleteByUserId(Long userId);

	@Query("select count(l) from AiLog l where l.state = :state and l.createdAt >= :from")
	long countByStateSince(@Param("state") String state, @Param("from") LocalDateTime from);

	@Query("select count(l) from AiLog l where l.userId = :userId and l.state = :state "
			+ "and l.resultType in :resultTypes and l.createdAt >= :from")
	long countByUserAndStateAndResultTypesSince(@Param("userId") Long userId, @Param("state") String state,
			@Param("resultTypes") Collection<String> resultTypes, @Param("from") LocalDateTime from);
}
