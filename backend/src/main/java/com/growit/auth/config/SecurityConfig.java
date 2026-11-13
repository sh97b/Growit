package com.growit.auth.config;

import com.growit.auth.security.JwtAuthenticationEntryPoint;
import com.growit.auth.security.JwtAuthenticationFilter;
import com.growit.auth.security.JwtTokenProvider;
import com.growit.user.repository.UserRepository;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
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
 * 서버가 세션을 들고 있지 않도록 상태 비저장(stateless)으로 두고 토큰(JWT)만으로 인증한다.
 * <p>CORS를 인증 검사보다 먼저 등록한다. 순서가 바뀌면 브라우저의 사전 확인(OPTIONS) 요청이 토큰 없이 와서 401로 막혔었다.
 * <p>Swagger 경로는 항상 permitAll이지만, {@code app.swagger.enabled}가 false면 springdoc이 등록을 안 해서 어차피 404로 끝난다.
 */
@Configuration
public class SecurityConfig {

	/**
	 * 로그인 없이도 실제로 필요한 경로만 연다. {@code /api/push/public-key}는 로그인 전에도(가입 중·토큰이
	 * 아직 설정 전에도) VAPID 공개키를 읽을 수 있어야 해서 열어 두지만, 그 밖의 {@code /api/push/**}
	 * (구독 등록·해제·테스트)는 그대로 인증이 필요하다. {@link JwtAuthenticationFilter}는 공개 경로에서도
	 * Authorization 헤더가 유효하면 인증 정보를 그대로 채운다(선택적 인증) — 이 목록은 "인증 없이도 통과"만
	 * 결정할 뿐, 유효한 토큰이 있을 때 그걸 무시하지는 않는다.
	 */
	private static final String[] PUBLIC_PATHS = {
			"/api/health",
			"/api/auth/signup",
			"/api/auth/login",
			"/api/auth/oauth/**",
			"/api/push/public-key",
			"/v3/api-docs/**",
			"/swagger-ui/**",
			"/swagger-ui.html",
			"/error"
	};

	/** 비용(cost)은 설정값으로 받는다. 로그인 성공 시 저장된 해시의 비용이 더 낮으면 새 비용으로 다시 저장한다. */
	@Bean
	public PasswordEncoder passwordEncoder(@Value("${app.password.bcrypt-cost:10}") int bcryptCost) {
		return new BCryptPasswordEncoder(bcryptCost);
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenProvider jwtTokenProvider,
			JwtAuthenticationEntryPoint entryPoint, CorsConfigurationSource corsConfigurationSource,
			UserRepository userRepository) throws Exception {
		http
				.cors(cors -> cors.configurationSource(corsConfigurationSource))
				.csrf(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						// 컨트롤러에서 처리되지 않은 예외(Error 포함)는 서블릿 컨테이너가 /error로 ERROR
						// 디스패치를 만드는데, 보안 필터 체인이 그 디스패치에도 다시 적용돼 인증을 요구하면
						// 원래의 500이 401로 바뀌어 프론트가 잘못 로그아웃한다. ERROR 디스패치와 /error는
						// 인증 없이 통과시켜 원래 상태 코드가 그대로 나가게 한다.
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.requestMatchers(PUBLIC_PATHS).permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(exception -> exception.authenticationEntryPoint(entryPoint))
				.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, userRepository),
						UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}
}
