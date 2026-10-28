package com.growit.record;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.common.ApiException;
import com.growit.common.CurrentUserProvider;
import com.growit.common.ErrorCode;
import com.growit.record.dto.RecordCreateRequest;
import com.growit.record.dto.RecordListResponse;
import com.growit.record.dto.RecordResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RecordController.class)
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
				new RecordResponse(1L, 10L, LocalDate.of(2026, 10, 28), "연습했다", 20, null, Feeling.PROUD, false, null));

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
		RecordResponse item = new RecordResponse(1L, 10L, LocalDate.of(2026, 10, 28), "연습했다", 20, null,
				Feeling.PROUD, false, null);
		given(recordService.list(eq(1L), isNull(), isNull(), isNull(), any(), any()))
				.willReturn(new RecordListResponse(List.of(item), 1));

		mockMvc.perform(get("/api/records"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalCount", is(1)))
				.andExpect(jsonPath("$.items[0].content", is("연습했다")));
	}
}
