package com.growit.record.repository;

import com.growit.record.entity.CustomFeeling;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomFeelingRepository extends JpaRepository<CustomFeeling, Long> {

	List<CustomFeeling> findByUserIdOrderByIdAsc(Long userId);

	long countByUserId(Long userId);

	Optional<CustomFeeling> findByIdAndUserId(Long id, Long userId);

	void deleteByUserId(Long userId);
}
