package com.growit.stats;

import com.growit.common.CurrentUserProvider;
import com.growit.common.ErrorResponse;
import com.growit.stats.dto.ActivityMapEntry;
import com.growit.stats.dto.StatsSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "통계", description = "활동 요약과 활동 지도 집계")
@RestController
@RequestMapping("/api/stats")
public class StatsController {

	private final StatsService statsService;
	private final CurrentUserProvider currentUserProvider;

	public StatsController(StatsService statsService, CurrentUserProvider currentUserProvider) {
		this.statsService = statsService;
		this.currentUserProvider = currentUserProvider;
	}

	@Operation(summary = "활동 요약 (기록한 날 수, 누적 시간, 분야 수, 마지막 기록, 쉰 일수, 오늘 기록 여부)")
	@GetMapping("/summary")
	public StatsSummaryResponse summary() {
		return statsService.summary(currentUserProvider.getCurrentUserId());
	}

	@Operation(summary = "활동 지도 (날짜·분야별 기록 집계, 기록 있는 날만)")
	@ApiResponse(responseCode = "400", description = "날짜 범위 오류",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "내 활동 분야가 아님",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@GetMapping("/activity-map")
	public List<ActivityMapEntry> activityMap(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long hobbyId) {
		return statsService.activityMap(currentUserProvider.getCurrentUserId(), from, to, hobbyId);
	}
}
