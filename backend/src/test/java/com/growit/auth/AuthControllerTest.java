package com.growit.auth;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.auth.dto.LoginRequest;
import com.growit.auth.dto.LoginResponse;
import com.growit.auth.dto.SignupRequest;
import com.growit.auth.dto.SignupResponse;
import com.growit.common.ApiException;
import com.growit.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockBean
	private AuthService authService;

	@Test
	void signup_success_returnsCreated() throws Exception {
		given(authService.signup(any())).willReturn(new SignupResponse(1L, "growit_user"));

		mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new SignupRequest("growit_user", "password1234"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.username", is("growit_user")));
	}

	@Test
	void signup_uppercaseUsername_returnsValidationError() throws Exception {
		mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new SignupRequest("Growit_User", "password1234"))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
	}

	@Test
	void signup_shortPassword_returnsValidationError() throws Exception {
		mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new SignupRequest("growit_user", "short"))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
	}

	@Test
	void signup_duplicateUsername_returnsConflict() throws Exception {
		willThrow(new ApiException(ErrorCode.USERNAME_TAKEN, "이미 사용 중인 아이디예요"))
				.given(authService).signup(any());

		mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new SignupRequest("growit_user", "password1234"))))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code", is("USERNAME_TAKEN")));
	}

	@Test
	void login_success_returnsToken() throws Exception {
		given(authService.login(any())).willReturn(new LoginResponse("jwt-token"));

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginRequest("growit_user", "password1234"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken", is("jwt-token")));
	}

	@Test
	void login_wrongPassword_returnsUnauthorized() throws Exception {
		willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "아이디 또는 비밀번호가 맞지 않아요"))
				.given(authService).login(any());

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginRequest("growit_user", "wrong-password"))))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
	}
}
