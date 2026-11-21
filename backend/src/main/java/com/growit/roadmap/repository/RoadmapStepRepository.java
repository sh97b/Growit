package com.growit.roadmap.repository;

import com.growit.roadmap.entity.RoadmapStep;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoadmapStepRepository extends JpaRepository<RoadmapStep, Long> {

	List<RoadmapStep> findByRoadmapIdOrderBySortOrderAscIdAsc(Long roadmapId);

	long countByRoadmapId(Long roadmapId);

	@Modifying(flushAutomatically = true)
	@Query("delete from RoadmapStep s where s.roadmapId in (select r.id from Roadmap r where r.userId = :userId)")
	void deleteByUserId(@Param("userId") Long userId);

	@Modifying(flushAutomatically = true)
	@Query("delete from RoadmapStep s where s.roadmapId in (select r.id from Roadmap r where r.hobbyId = :hobbyId)")
	void deleteByHobbyId(@Param("hobbyId") Long hobbyId);
}
