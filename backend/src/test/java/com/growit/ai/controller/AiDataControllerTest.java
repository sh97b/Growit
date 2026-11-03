package com.growit.ai.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growit.ai.service.AiDataResetService;
import com.growit.auth.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AiDataController.class)
@AutoConfigureMockMvc(addFilters = false)
class AiDataControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private AiDataResetService aiDataResetService;

	@MockBean
	private CurrentUserProvider currentUserProvider;

	@Test
	void deleteMyData_returnsNoContentAndResetsOwnDataOnly() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(1L);

		mockMvc.perform(delete("/api/ai/data"))
				.andExpect(status().isNoContent());

		Mockito.verify(aiDataResetService).resetMyData(eq(1L));
	}
}
