package com.growit.auth;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.common.CurrentUserProvider;
import java.util.Optional;
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
	private UserRepository userRepository;

	@MockBean
	private CurrentUserProvider currentUserProvider;

	private User user(String displayName) {
		User user = new User("growit_user@example.com", "hashed", displayName);
		return user;
	}

	@Test
	void updateMe_trimsAndSavesDisplayName() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(1L);
		given(userRepository.findById(anyLong())).willReturn(Optional.of(user("growit_user")));

		mockMvc.perform(patch("/api/users/me")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(java.util.Map.of("displayName", "  서현  "))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName", is("서현")));
	}

	@Test
	void updateMe_blankName_returnsValidationError() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(1L);
		given(userRepository.findById(anyLong())).willReturn(Optional.of(user("growit_user")));

		mockMvc.perform(patch("/api/users/me")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(java.util.Map.of("displayName", "   "))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
	}

	@Test
	void updateMe_tooLongName_returnsValidationError() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(1L);
		given(userRepository.findById(anyLong())).willReturn(Optional.of(user("growit_user")));

		String tooLong = "가".repeat(31);
		mockMvc.perform(patch("/api/users/me")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(java.util.Map.of("displayName", tooLong))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
	}

	@Test
	void me_returnsOwnInfo() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(1L);
		given(userRepository.findById(anyLong())).willReturn(Optional.of(user("growit_user")));

		mockMvc.perform(get("/api/users/me"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email", is("growit_user@example.com")))
				.andExpect(jsonPath("$.displayName", is("growit_user")));
	}
}
