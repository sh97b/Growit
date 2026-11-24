package com.growit.roadmap.repository;

import com.growit.roadmap.entity.BlockKind;
import com.growit.roadmap.entity.RoadmapItemBlock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoadmapItemBlockRepository extends JpaRepository<RoadmapItemBlock, Long> {

	List<RoadmapItemBlock> findByItemIdOrderBySortOrderAscIdAsc(Long itemId);

	long countByItemId(Long itemId);

	Optional<RoadmapItemBlock> findFirstByItemIdAndKind(Long itemId, BlockKind kind);

	/** 항목별 블록 수(로드맵 응답의 blockCount용). 블록이 없는 항목은 결과에 없다. */
	@Query("select b.itemId, count(b) from RoadmapItemBlock b where b.itemId in :itemIds group by b.itemId")
	List<Object[]> countGroupedByItemId(@Param("itemIds") Collection<Long> itemIds);

	@Query("select count(b) from RoadmapItemBlock b where b.itemId in (select i.id from RoadmapItem i where i.stepId in "
			+ "(select s.id from RoadmapStep s where s.roadmapId = :roadmapId))")
	long countByRoadmapId(@Param("roadmapId") Long roadmapId);

	@Modifying(flushAutomatically = true)
	@Query("delete from RoadmapItemBlock b where b.itemId = :itemId")
	void deleteByItemId(@Param("itemId") Long itemId);

	@Modifying(flushAutomatically = true)
	@Query("delete from RoadmapItemBlock b where b.itemId in (select i.id from RoadmapItem i where i.stepId = :stepId)")
	void deleteByStepId(@Param("stepId") Long stepId);

	@Modifying(flushAutomatically = true)
	@Query("delete from RoadmapItemBlock b where b.itemId in (select i.id from RoadmapItem i where i.stepId in "
			+ "(select s.id from RoadmapStep s where s.roadmapId = :roadmapId))")
	void deleteByRoadmapId(@Param("roadmapId") Long roadmapId);

	@Modifying(flushAutomatically = true)
	@Query("delete from RoadmapItemBlock b where b.itemId in (select i.id from RoadmapItem i where i.stepId in "
			+ "(select s.id from RoadmapStep s where s.roadmapId in (select r.id from Roadmap r where r.hobbyId = :hobbyId)))")
	void deleteByHobbyId(@Param("hobbyId") Long hobbyId);

	@Modifying(flushAutomatically = true)
	@Query("delete from RoadmapItemBlock b where b.itemId in (select i.id from RoadmapItem i where i.stepId in "
			+ "(select s.id from RoadmapStep s where s.roadmapId in (select r.id from Roadmap r where r.userId = :userId)))")
	void deleteByUserId(@Param("userId") Long userId);
}
