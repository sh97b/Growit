package com.growit.hobby.repository;

import com.growit.hobby.entity.Hobby;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@link Hobby} 조회. 모든 메서드가 {@code userId}를 받아 다른 사용자의 분야가 섞이지 않게 한다.
 */
public interface HobbyRepository extends JpaRepository<Hobby, Long> {

	/** 보관 여부와 무관하게 전체 목록(활동 분야 관리 화면용). */
	List<Hobby> findByUserIdOrderByIdAsc(Long userId);

	/** 보관되지 않은(활성) 분야만(기록 작성 화면의 분야 선택용). */
	List<Hobby> findByUserIdAndArchivedAtIsNullOrderByIdAsc(Long userId);

	/** 소유자 확인을 겸한 단건 조회. 없거나 내 것이 아니면 빈 값. */
	Optional<Hobby> findByIdAndUserId(Long id, Long userId);

	boolean existsByUserIdAndName(Long userId, String name);

	/** 홈 통계의 "활성 분야 수"에 쓰인다. */
	long countByUserIdAndArchivedAtIsNull(Long userId);
}
