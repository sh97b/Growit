package com.growit.auth.security;

import com.growit.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 토큰이 없거나 틀려도 여기서 막지 않고 그대로 다음 필터로 넘긴다.
 * 인증이 필요한 주소에 인증 정보 없이 닿으면 {@link JwtAuthenticationEntryPoint}가 401로 응답한다.
 * 요청마다 사용자를 기본키로 한 번 조회해 토큰의 tv와 비교한다 - 비용은 PK 조회라 가볍지만, 그래도
 * 요청마다 DB를 한 번 더 타는 셈이라 비밀번호를 바꾼 적 없는 대부분의 요청에도 똑같이 적용된다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenProvider jwtTokenProvider;
	private final UserRepository userRepository;

	public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
		this.jwtTokenProvider = jwtTokenProvider;
		this.userRepository = userRepository;
	}

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith(BEARER_PREFIX)) {
			String token = header.substring(BEARER_PREFIX.length());
			Optional<Long> userId = jwtTokenProvider.parseUserId(token);
			userId.ifPresent(id -> {
				int tokenVersion = jwtTokenProvider.parseTokenVersion(token);
				userRepository.findById(id)
						.filter(user -> user.getTokenVersion() == tokenVersion)
						.ifPresent(user -> {
							var authentication = new UsernamePasswordAuthenticationToken(id, null, List.of());
							SecurityContextHolder.getContext().setAuthentication(authentication);
						});
			});
		}
		filterChain.doFilter(request, response);
	}
}
