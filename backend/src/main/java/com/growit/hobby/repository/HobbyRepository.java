package com.growit.hobby.repository;

import com.growit.hobby.entity.Hobby;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HobbyRepository extends JpaRepository<Hobby, Long> {

	List<Hobby> findByUserIdOrderByIdAsc(Long userId);

	List<Hobby> findByUserIdAndArchivedAtIsNullOrderByIdAsc(Long userId);

	Optional<Hobby> findByIdAndUserId(Long id, Long userId);

	/** 분야 행을 잠가 같은 분야의 로드맵 복원·보관이 동시에 겹치지 않게 한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select h from Hobby h where h.id = :id and h.userId = :userId")
	Optional<Hobby> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

	boolean existsByUserIdAndName(Long userId, String name);

	long countByUserIdAndArchivedAtIsNull(Long userId);

	void deleteByUserId(Long userId);
}
