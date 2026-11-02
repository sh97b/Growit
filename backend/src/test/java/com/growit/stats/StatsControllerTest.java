package com.growit.stats;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growit.common.ApiException;
import com.growit.common.CurrentUserProvider;
import com.growit.common.ErrorCode;
import com.growit.stats.dto.ActivityMapEntry;
import com.growit.stats.dto.StatsSummaryResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StatsController.class)
@AutoConfigureMockMvc(addFilters = false)
class StatsControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private StatsService statsService;

	@MockBean
	private CurrentUserProvider currentUserProvider;

	@Test
	void summary_returnsOk() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(1L);
		given(statsService.summary(1L)).willReturn(
				new StatsSummaryResponse(3, 60, 2, LocalDate.of(2026, 10, 28), 0L, true, LocalDate.of(2026, 10, 28)));

		mockMvc.perform(get("/api/stats/summary"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recordedDays", is(3)))
				.andExpect(jsonPath("$.todayRecorded", is(true)));
	}

	@Test
	void activityMap_returnsOk() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(1L);
		given(statsService.activityMap(eq(1L), eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 10, 2)),
				isNull()))
				.willReturn(List.of(new ActivityMapEntry(LocalDate.of(2026, 10, 1), 10L, 2, 30)));

		mockMvc.perform(get("/api/stats/activity-map").param("from", "2026-10-01").param("to", "2026-10-02"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].count", is(2)));
	}

	@Test
	void activityMap_invalidRange_returnsValidationError() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(1L);
		willThrow(new ApiException(ErrorCode.VALIDATION_ERROR, "시작일이 끝일보다 늦을 수 없어요"))
				.given(statsService).activityMap(eq(1L), any(), any(), isNull());

		mockMvc.perform(get("/api/stats/activity-map").param("from", "2026-10-10").param("to", "2026-10-01"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
	}

	@Test
	void activityMap_otherUsersHobbyId_returnsNotFound() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(1L);
		willThrow(new ApiException(ErrorCode.NOT_FOUND, "활동 분야를 찾을 수 없어요"))
				.given(statsService).activityMap(eq(1L), any(), any(), eq(999L));

		mockMvc.perform(get("/api/stats/activity-map")
						.param("from", "2026-10-01")
						.param("to", "2026-10-02")
						.param("hobbyId", "999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code", is("NOT_FOUND")));
	}
}
