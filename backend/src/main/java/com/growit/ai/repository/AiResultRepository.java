package com.growit.ai.repository;

import com.growit.ai.entity.AiResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiResultRepository extends JpaRepository<AiResult, Long> {

	void deleteByUserId(Long userId);

	Optional<AiResult> findByIdAndUserId(Long id, Long userId);

	Optional<AiResult> findByUserIdAndCacheKey(Long userId, String cacheKey);

	List<AiResult> findByUserIdAndResultTypeOrderByCreatedAtDesc(Long userId, String resultType, Pageable pageable);

	List<AiResult> findByUserIdAndResultTypeAndHobbyIdOrderByCreatedAtDesc(Long userId, String resultType,
			Long hobbyId, Pageable pageable);

	long countByUserIdAndResultType(Long userId, String resultType);

	long countByUserIdAndResultTypeAndHobbyId(Long userId, String resultType, Long hobbyId);

	boolean existsByUserIdAndResultTypeAndCreatedAtGreaterThanEqual(Long userId, String resultType,
			LocalDateTime from);
}
