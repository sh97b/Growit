package com.growit.ai.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growit.ai.dto.AiUsageResponse;
import com.growit.ai.service.AiUsageService;
import com.growit.auth.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AiUsageController.class)
@AutoConfigureMockMvc(addFilters = false)
class AiUsageControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private AiUsageService aiUsageService;

	@MockBean
	private CurrentUserProvider currentUserProvider;

	@Test
	void usage_returnsOwnUsage() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(1L);
		given(aiUsageService.usage(1L)).willReturn(new AiUsageResponse(true, 5, 2, 3));

		mockMvc.perform(get("/api/ai/usage"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enabled", is(true)))
				.andExpect(jsonPath("$.limit", is(5)))
				.andExpect(jsonPath("$.used", is(2)))
				.andExpect(jsonPath("$.remaining", is(3)));
	}
}
