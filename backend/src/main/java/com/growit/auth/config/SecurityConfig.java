package com.growit.auth.config;

import com.growit.auth.security.JwtAuthenticationEntryPoint;
import com.growit.auth.security.JwtAuthenticationFilter;
import com.growit.auth.security.JwtTokenProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * 모든 API 요청에 적용되는 Spring Security 규칙을 정의한다.
 * <p>세션을 쓰지 않는 상태 비저장(stateless) 방식이다. 인증은 {@link JwtAuthenticationFilter}가
 * 요청마다 {@code Authorization} 헤더의 토큰을 검사해 {@code SecurityContext}에 사용자 id를 심는 방식으로 이뤄지고,
 * 심어지지 않은 상태로 인증이 필요한 경로에 닿으면 {@link JwtAuthenticationEntryPoint}가 401을 응답한다.
 * <p>CORS({@code cors(...)})는 인증 검사({@code authorizeHttpRequests(...)})보다 먼저 등록해서,
 * 브라우저가 보내는 사전 확인(OPTIONS) 요청이 토큰 없이도 먼저 통과하게 한다. 이 순서가 바뀌면
 * 프런트엔드가 보낸 모든 인증 요청의 사전 확인이 401로 막힌다.
 * <p>Swagger 주소(v3/api-docs, swagger-ui)는 항상 인증 없이 열어 둔다.
 * {@code app.swagger.enabled}가 false면 springdoc이 그 주소에 아무 것도 등록하지 않아
 * 이 permitAll은 의미가 없어지고 요청은 (인증 거부가 아니라) 404로 끝난다.
 */
@Configuration
public class SecurityConfig {

	private static final String[] PUBLIC_PATHS = {
			"/api/health",
			"/api/auth/signup",
			"/api/auth/login",
			"/v3/api-docs/**",
			"/swagger-ui/**",
			"/swagger-ui.html"
	};

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/** 위 클래스 설명대로 CORS→인증 순서, 공개 경로, JWT 필터를 연결해 필터 체인을 만든다. */
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenProvider jwtTokenProvider,
			JwtAuthenticationEntryPoint entryPoint, CorsConfigurationSource corsConfigurationSource)
			throws Exception {
		http
				.cors(cors -> cors.configurationSource(corsConfigurationSource))
				.csrf(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers(PUBLIC_PATHS).permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(exception -> exception.authenticationEntryPoint(entryPoint))
				.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
						UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}
}
