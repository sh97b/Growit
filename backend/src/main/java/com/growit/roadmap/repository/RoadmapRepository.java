package com.growit.roadmap.repository;

import com.growit.roadmap.entity.Roadmap;
import com.growit.roadmap.entity.RoadmapState;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoadmapRepository extends JpaRepository<Roadmap, Long> {

	Optional<Roadmap> findByIdAndUserId(Long id, Long userId);

	Optional<Roadmap> findFirstByHobbyIdAndUserIdAndState(Long hobbyId, Long userId, RoadmapState state);

	/** 보관된 것까지 포함해 오늘 만든 개수(분야당 하루 한도). */
	long countByHobbyIdAndUserIdAndCreatedAtGreaterThanEqual(Long hobbyId, Long userId, LocalDateTime since);

	@Modifying(flushAutomatically = true)
	@Query("delete from Roadmap r where r.userId = :userId")
	void deleteByUserId(@Param("userId") Long userId);

	@Modifying(flushAutomatically = true)
	@Query("delete from Roadmap r where r.hobbyId = :hobbyId")
	void deleteByHobbyId(@Param("hobbyId") Long hobbyId);
}
