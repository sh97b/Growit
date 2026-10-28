package com.growit.record;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ActivityRecordRepository
		extends JpaRepository<ActivityRecord, Long>, JpaSpecificationExecutor<ActivityRecord> {

	Optional<ActivityRecord> findByIdAndUserId(Long id, Long userId);

	boolean existsByHobbyId(Long hobbyId);

	long countByHobbyId(Long hobbyId);
}
