package com.growit.common.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배포 상태 확인용 핑 엔드포인트. 인증이 필요 없다.
 */
@SecurityRequirements
@RestController
public class HealthController {

	/** {@code GET /api/health} — 서버가 떠 있으면 200과 {@code {"status":"ok"}}. */
	@GetMapping("/api/health")
	public Map<String, String> health() {
		return Map.of("status", "ok");
	}
}
