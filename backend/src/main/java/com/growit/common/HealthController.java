package com.growit.common;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SecurityRequirements
@RestController
public class HealthController {

	@GetMapping("/api/health")
	public Map<String, String> health() {
		return Map.of("status", "ok");
	}
}
