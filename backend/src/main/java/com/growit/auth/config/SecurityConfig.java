package com.growit.auth.config;

import com.growit.auth.security.JwtAuthenticationEntryPoint;
import com.growit.auth.security.JwtAuthenticationFilter;
import com.growit.auth.security.JwtTokenProvider;
import com.growit.user.repository.UserRepository;
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

	private static final String[] PUBLIC_PATHS = {
			"/api/health",
			"/api/auth/signup",
			"/api/auth/login",
			"/v3/api-docs/**",
			"/swagger-ui/**",
			"/swagger-ui.html"
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
						.requestMatchers(PUBLIC_PATHS).permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(exception -> exception.authenticationEntryPoint(entryPoint))
				.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, userRepository),
						UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}
}
