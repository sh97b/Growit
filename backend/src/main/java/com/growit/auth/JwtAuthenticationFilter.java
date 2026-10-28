package com.growit.auth;

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
 * Authorization: Bearer {토큰}을 읽어 유효하면 SecurityContext에 사용자 id를 심는다.
 * 토큰이 없거나 유효하지 않으면 그대로 다음 필터로 넘기고, 인증이 필요한 주소는 이후 단계에서 401로 막힌다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenProvider jwtTokenProvider;

	public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
		this.jwtTokenProvider = jwtTokenProvider;
	}

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith(BEARER_PREFIX)) {
			String token = header.substring(BEARER_PREFIX.length());
			Optional<Long> userId = jwtTokenProvider.parseUserId(token);
			userId.ifPresent(id -> {
				var authentication = new UsernamePasswordAuthenticationToken(id, null, List.of());
				SecurityContextHolder.getContext().setAuthentication(authentication);
			});
		}
		filterChain.doFilter(request, response);
	}
}
