package com.growit.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenProvider jwtTokenProvider,
			JwtAuthenticationEntryPoint entryPoint) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(PUBLIC_PATHS).permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(exception -> exception.authenticationEntryPoint(entryPoint))
				.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
						UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}
}
