package com.growit.record.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.common.exception.ApiException;
import com.growit.auth.security.CurrentUserProvider;
import com.growit.common.exception.ErrorCode;
import com.growit.record.dto.AnalysisTagsEditRequest;
import com.growit.record.dto.RecordCreateRequest;
import com.growit.record.dto.RecordListResponse;
import com.growit.record.dto.RecordResponse;
import com.growit.record.dto.RecordSummaryResponse;
import com.growit.record.entity.AiStatus;
import com.growit.record.entity.Feeling;
import com.growit.record.service.RecordService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RecordController.class)
@AutoConfigureMockMvc(addFilters = false)
class RecordControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockBean
	private RecordService recordService;

	@MockBean
	private CurrentUserProvider currentUserProvider;

	@Test
	void create_success_returnsCreated() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(1L);
		given(recordService.create(eq(1L), any())).willReturn(
				new RecordResponse(1L, 10L, LocalDate.of(2026, 10, 28), "연습했다", 20, null, Feeling.PROUD, false,
						AiStatus.SKIPPED, null, null, null));

		mockMvc.perform(post("/api/records")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new RecordCreateRequest(10L, LocalDate.of(2026, 10, 28), "연습했다", 20, null,
										Feeling.PROUD, false))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.content", is("연습했다")))
				.andExpect(jsonPath("$.analysis").doesNotExist());
	}

	@Test
	void create_blankContent_returnsValidationError() throws Exception {
		mockMvc.perform(post("/api/records")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								new RecordCreateRequest(10L, LocalDate.of(2026, 10, 28), "  ", null, null, null,
										null))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
	}

	@Test
	void create_invalidFeeling_returnsValidationError() throws Exception {
		mockMvc.perform(post("/api/records")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"hobbyId\":10,\"recordDate\":\"2026-10-28\",\"content\":\"연습했다\",\"feeling\":\"HAPPY\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
	}

	@Test
	void get_otherUsersRecord_returnsNotFound() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(1L);
		willThrow(new ApiException(ErrorCode.NOT_FOUND, "기록을 찾을 수 없어요"))
				.given(recordService).get(eq(1L), anyLong());

		mockMvc.perform(get("/api/records/{id}", 999L))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code", is("NOT_FOUND")));
	}

	@Test
	void delete_otherUsersRecord_returnsNotFound() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(1L);
		willThrow(new ApiException(ErrorCode.NOT_FOUND, "기록을 찾을 수 없어요"))
				.given(recordService).delete(eq(1L), anyLong());

		mockMvc.perform(delete("/api/records/{id}", 999L))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code", is("NOT_FOUND")));
	}

	@Test
	void list_returnsItemsAndTotalCount() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(1L);
		RecordSummaryResponse item = new RecordSummaryResponse(1L, 10L, LocalDate.of(2026, 10, 28), "연습했다", 20, null,
				Feeling.PROUD, false, AiStatus.SKIPPED, null);
		given(recordService.list(eq(1L), isNull(), isNull(), isNull(), any(), any()))
				.willReturn(new RecordListResponse(List.of(item), 1));

		mockMvc.perform(get("/api/records"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalCount", is(1)))
				.andExpect(jsonPath("$.items[0].content", is("연습했다")));
	}

	@Test
	void reanalyze_aiDisabled_returnsConflict() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(1L);
		willThrow(new ApiException(ErrorCode.AI_DISABLED, "AI 기능이 꺼져 있어요"))
				.given(recordService).reanalyze(eq(1L), anyLong());

		mockMvc.perform(post("/api/records/{id}/analysis", 1L))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code", is("AI_DISABLED")));
	}

	@Test
	void reanalyze_success_returnsAccepted() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(1L);
		given(recordService.reanalyze(eq(1L), anyLong())).willReturn(
				new RecordResponse(1L, 10L, LocalDate.of(2026, 10, 28), "연습했다", 20, null, Feeling.PROUD, false,
						AiStatus.PENDING, null, null, null));

		mockMvc.perform(post("/api/records/{id}/analysis", 1L))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.aiStatus", is("PENDING")));
	}

	@Test
	void editTags_notReady_returnsConflict() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(1L);
		willThrow(new ApiException(ErrorCode.ANALYSIS_NOT_READY, "아직 분석이 끝나지 않았어요"))
				.given(recordService).editTags(eq(1L), anyLong(), any());

		mockMvc.perform(patch("/api/records/{id}/analysis/tags", 1L)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new AnalysisTagsEditRequest(List.of("연습")))))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code", is("ANALYSIS_NOT_READY")));
	}

	@Test
	void submitFeedback_success_delegatesToService() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(1L);
		given(recordService.submitFeedback(eq(1L), anyLong(), any())).willReturn(
				new RecordResponse(1L, 10L, LocalDate.of(2026, 10, 28), "연습했다", 20, null, Feeling.PROUD, false,
						AiStatus.DONE, null, null, null));

		mockMvc.perform(put("/api/records/{id}/analysis/feedback", 1L)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"change\":\"HELPFUL\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.aiStatus", is("DONE")));
	}
}
