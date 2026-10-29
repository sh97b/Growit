package com.growit.hobby;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HobbyRepository extends JpaRepository<Hobby, Long> {

	List<Hobby> findByUserIdOrderByIdAsc(Long userId);

	List<Hobby> findByUserIdAndArchivedAtIsNullOrderByIdAsc(Long userId);

	Optional<Hobby> findByIdAndUserId(Long id, Long userId);

	boolean existsByUserIdAndName(Long userId, String name);
}
