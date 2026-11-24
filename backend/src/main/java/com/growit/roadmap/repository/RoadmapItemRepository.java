package com.growit.roadmap.repository;

import com.growit.roadmap.entity.RoadmapItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoadmapItemRepository extends JpaRepository<RoadmapItem, Long> {

	List<RoadmapItem> findByStepIdInOrderBySortOrderAscIdAsc(Collection<Long> stepIds);

	List<RoadmapItem> findByStepIdOrderBySortOrderAscIdAsc(Long stepId);

	long countByStepId(Long stepId);

	@Modifying(flushAutomatically = true)
	@Query("delete from RoadmapItem i where i.stepId in (select s.id from RoadmapStep s where s.roadmapId = :roadmapId)")
	void deleteByRoadmapId(@Param("roadmapId") Long roadmapId);

	@Modifying(flushAutomatically = true)
	@Query("delete from RoadmapItem i where i.stepId in (select s.id from RoadmapStep s where s.roadmapId in "
			+ "(select r.id from Roadmap r where r.userId = :userId))")
	void deleteByUserId(@Param("userId") Long userId);

	@Modifying(flushAutomatically = true)
	@Query("delete from RoadmapItem i where i.stepId in (select s.id from RoadmapStep s where s.roadmapId in "
			+ "(select r.id from Roadmap r where r.hobbyId = :hobbyId))")
	void deleteByHobbyId(@Param("hobbyId") Long hobbyId);

	@Modifying(flushAutomatically = true)
	@Query("delete from RoadmapItem i where i.stepId = :stepId")
	void deleteByStepId(@Param("stepId") Long stepId);
}
