package com.growit.hobby;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.common.ApiException;
import com.growit.common.CurrentUserProvider;
import com.growit.common.ErrorCode;
import com.growit.hobby.dto.HobbyCreateRequest;
import com.growit.hobby.dto.HobbyResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HobbyController.class)
@AutoConfigureMockMvc(addFilters = false)
class HobbyControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockBean
	private HobbyService hobbyService;

	@MockBean
	private CurrentUserProvider currentUserProvider;

	@Test
	void create_success_returnsCreated() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(1L);
		given(hobbyService.create(eq(1L), any())).willReturn(new HobbyResponse(1L, "기타", "코드 전환 연습", 0));

		mockMvc.perform(post("/api/hobbies")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new HobbyCreateRequest("기타", "코드 전환 연습"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name", is("기타")));
	}

	@Test
	void create_blankName_returnsValidationError() throws Exception {
		mockMvc.perform(post("/api/hobbies")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new HobbyCreateRequest("  ", null))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
	}

	@Test
	void create_duplicateName_returnsConflict() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(1L);
		willThrow(new ApiException(ErrorCode.HOBBY_NAME_TAKEN, "이미 사용 중인 분야 이름이에요"))
				.given(hobbyService).create(eq(1L), any());

		mockMvc.perform(post("/api/hobbies")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new HobbyCreateRequest("기타", null))))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code", is("HOBBY_NAME_TAKEN")));
	}

	@Test
	void delete_otherUsersHobby_returnsNotFound() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(1L);
		willThrow(new ApiException(ErrorCode.NOT_FOUND, "활동 분야를 찾을 수 없어요"))
				.given(hobbyService).delete(eq(1L), anyLong());

		mockMvc.perform(delete("/api/hobbies/{id}", 999L))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code", is("NOT_FOUND")));
	}

	@Test
	void delete_hobbyWithRecords_returnsConflict() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(1L);
		willThrow(new ApiException(ErrorCode.HOBBY_HAS_RECORDS, "기록이 있는 분야는 삭제할 수 없어요"))
				.given(hobbyService).delete(eq(1L), anyLong());

		mockMvc.perform(delete("/api/hobbies/{id}", 1L))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code", is("HOBBY_HAS_RECORDS")));
	}

	@Test
	void update_returnsOk() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(1L);
		given(hobbyService.update(eq(1L), anyLong(), any()))
				.willReturn(new HobbyResponse(1L, "피아노", null, 0));

		mockMvc.perform(patch("/api/hobbies/{id}", 1L)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"피아노\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name", is("피아노")));
	}

}
