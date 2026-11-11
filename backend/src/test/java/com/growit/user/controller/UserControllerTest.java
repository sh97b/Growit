package com.growit.user.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.user.dto.MeResponse;
import com.growit.user.service.UserService;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockBean
	private UserService userService;

	@Test
	void updateMe_returnsUpdatedName() throws Exception {
		given(userService.updateMe(any()))
				.willReturn(new MeResponse(1L, "growit_user@example.com", "서현", true, true, LocalDateTime.now()));

		mockMvc.perform(patch("/api/users/me")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("displayName", "  서현  "))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName", is("서현")));
	}

	@Test
	void updateMe_blankName_returnsValidationError() throws Exception {
		willThrow(new ApiException(ErrorCode.VALIDATION_ERROR, "이름을 입력해 주세요"))
				.given(userService).updateMe(any());

		mockMvc.perform(patch("/api/users/me")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("displayName", "   "))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
	}

	@Test
	void me_returnsOwnInfo() throws Exception {
		given(userService.me())
				.willReturn(new MeResponse(1L, "growit_user@example.com", "growit_user", true, true, LocalDateTime.now()));

		mockMvc.perform(get("/api/users/me"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email", is("growit_user@example.com")))
				.andExpect(jsonPath("$.displayName", is("growit_user")));
	}
}
