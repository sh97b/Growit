package com.growit.user.repository;

import com.growit.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@link User} 조회. 로그인(이메일로 찾기)과 가입(중복 검사)에 쓰인다.
 */
public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);
}
