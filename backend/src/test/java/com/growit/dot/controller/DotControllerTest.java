package com.growit.dot.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growit.auth.security.CurrentUserProvider;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.dot.dto.DotCell;
import com.growit.dot.dto.DotCurrentResponse;
import com.growit.dot.dto.DotGoalResponse;
import com.growit.dot.dto.DotPresetResponse;
import com.growit.dot.entity.DotGoalState;
import com.growit.dot.entity.DotSourceType;
import com.growit.dot.service.DotGoalService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DotController.class)
@AutoConfigureMockMvc(addFilters = false)
class DotControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private DotGoalService dotGoalService;

	@MockBean
	private CurrentUserProvider currentUserProvider;

	private static DotGoalResponse goal(long id, DotGoalState state) {
		return new DotGoalResponse(id, "덤벨", DotSourceType.PRESET, "dumbbell", 14, 3, 5, 5, List.of("#112233"),
				List.of(new DotCell(0, 0, 0)), LocalDate.of(2026, 11, 18), state, null, false);
	}

	@Test
	void presets_returnsOk() throws Exception {
		given(dotGoalService.presets()).willReturn(
				List.of(new DotPresetResponse("dumbbell", "덤벨", 32, 32, List.of("#112233"), List.of("0"))));

		mockMvc.perform(get("/api/dots/presets")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].key", is("dumbbell")));
	}

	@Test
	void preview_returnsGridWithoutNeedingUserId() throws Exception {
		given(dotGoalService.preview("dumbbell", 30)).willReturn(new com.growit.dot.dto.DotPreviewResponse("dumbbell",
				"덤벨", 30, 9, 7, List.of("#112233"), List.of(new DotCell(0, 0, 0))));

		mockMvc.perform(get("/api/dots/presets/dumbbell/preview").param("goalDays", "30")).andExpect(status().isOk())
				.andExpect(jsonPath("$.key", is("dumbbell"))).andExpect(jsonPath("$.goalDays", is(30)))
				.andExpect(jsonPath("$.cells", hasSize(1)));
	}

	@Test
	void preview_unknownKey_is404_andBadGoalDays_is400() throws Exception {
		given(dotGoalService.preview("nope", 30)).willThrow(new ApiException(ErrorCode.NOT_FOUND, "알 수 없는 그림이에요"));
		given(dotGoalService.preview("dumbbell", 13))
				.willThrow(new ApiException(ErrorCode.VALIDATION_ERROR, "목표 일수는 14일부터 200일까지예요"));
		given(dotGoalService.preview("dumbbell", null))
				.willThrow(new ApiException(ErrorCode.VALIDATION_ERROR, "목표 일수를 입력해 주세요"));

		mockMvc.perform(get("/api/dots/presets/nope/preview").param("goalDays", "30")).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/dots/presets/dumbbell/preview").param("goalDays", "13"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
		mockMvc.perform(get("/api/dots/presets/dumbbell/preview")).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/dots/presets/dumbbell/preview").param("goalDays", "abc"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void current_passesCurrentUserId() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(7L);
		given(dotGoalService.current(7L)).willReturn(new DotCurrentResponse(goal(1, DotGoalState.ACTIVE), null));

		mockMvc.perform(get("/api/dots/current")).andExpect(status().isOk())
				.andExpect(jsonPath("$.goal.filledCount", is(3))).andExpect(jsonPath("$.justCompleted").doesNotExist());
	}

	@Test
	void start_valid_returnsCreated_andDoesNotReadUserIdFromBody() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(7L);
		given(dotGoalService.start(eq(7L), any())).willReturn(goal(5, DotGoalState.ACTIVE));

		mockMvc.perform(post("/api/dots/goals").contentType(MediaType.APPLICATION_JSON)
				.content("{\"presetKey\":\"dumbbell\",\"goalDays\":14,\"userId\":99}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.id", is(5)));
	}

	@Test
	void start_photoBody_isParsedWithCellsAndColorIndex() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(7L);
		org.mockito.ArgumentCaptor<com.growit.dot.dto.DotGoalStartRequest> captor = org.mockito.ArgumentCaptor
				.forClass(com.growit.dot.dto.DotGoalStartRequest.class);
		given(dotGoalService.start(eq(7L), captor.capture())).willReturn(goal(8, DotGoalState.ACTIVE));

		mockMvc.perform(post("/api/dots/goals").contentType(MediaType.APPLICATION_JSON).content(
				"{\"mode\":\"PHOTO\",\"title\":\"내 사진\",\"goalDays\":14,\"width\":4,\"height\":4,"
						+ "\"palette\":[\"#112233\"],\"cells\":[{\"x\":0,\"y\":1,\"c\":0},{\"x\":2,\"y\":3,\"c\":0}]}"))
				.andExpect(status().isCreated());

		com.growit.dot.dto.DotGoalStartRequest request = captor.getValue();
		org.assertj.core.api.Assertions.assertThat(request.mode()).isEqualTo(DotSourceType.PHOTO);
		org.assertj.core.api.Assertions.assertThat(request.cells()).hasSize(2);
		org.assertj.core.api.Assertions.assertThat(request.cells().get(1).x()).isEqualTo(2);
		org.assertj.core.api.Assertions.assertThat(request.cells().get(1).y()).isEqualTo(3);
		org.assertj.core.api.Assertions.assertThat(request.palette()).containsExactly("#112233");
	}

	@Test
	void start_tooManyCellsOrColorsOrUnknownMode_returnsBadRequest() throws Exception {
		String manyCells = java.util.stream.IntStream.range(0, 201).mapToObj(i -> "{\"x\":0,\"y\":0,\"c\":0}")
				.collect(java.util.stream.Collectors.joining(","));
		mockMvc.perform(post("/api/dots/goals").contentType(MediaType.APPLICATION_JSON).content(
				"{\"mode\":\"PHOTO\",\"goalDays\":200,\"width\":40,\"height\":40,\"palette\":[\"#112233\"],\"cells\":["
						+ manyCells + "]}")).andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/dots/goals").contentType(MediaType.APPLICATION_JSON).content(
				"{\"mode\":\"PHOTO\",\"goalDays\":14,\"palette\":[\"#1\",\"#2\",\"#3\",\"#4\",\"#5\",\"#6\",\"#7\",\"#8\",\"#9\"],"
						+ "\"cells\":[]}")).andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/dots/goals").contentType(MediaType.APPLICATION_JSON)
				.content("{\"mode\":\"BANANA\",\"goalDays\":14}")).andExpect(status().isBadRequest());
	}

	@Test
	void start_missingFields_returnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/dots/goals").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void start_alreadyActive_returnsConflictWithCode() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(7L);
		given(dotGoalService.start(eq(7L), any()))
				.willThrow(new ApiException(ErrorCode.DOT_GOAL_ACTIVE, "이미 진행 중인 도트 그림이 있어요"));

		mockMvc.perform(post("/api/dots/goals").contentType(MediaType.APPLICATION_JSON)
				.content("{\"presetKey\":\"dumbbell\",\"goalDays\":14}")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code", is("DOT_GOAL_ACTIVE")));
	}

	@Test
	void stop_returnsNoContent_andOthersGoalIsNotFound() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(7L);

		mockMvc.perform(post("/api/dots/goals/3/stop")).andExpect(status().isNoContent());
		verify(dotGoalService).stop(7L, 3L);

		willThrow(new ApiException(ErrorCode.NOT_FOUND, "진행 중인 도트 그림을 찾을 수 없어요")).given(dotGoalService)
				.stop(7L, 4L);
		mockMvc.perform(post("/api/dots/goals/4/stop")).andExpect(status().isNotFound());
	}

	@Test
	void album_detail_andCompletionSeen_work() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(7L);
		given(dotGoalService.album(7L)).willReturn(List.of(goal(1, DotGoalState.COMPLETED)));
		given(dotGoalService.detail(7L, 1L)).willReturn(goal(1, DotGoalState.COMPLETED));
		given(dotGoalService.detail(7L, 2L)).willThrow(new ApiException(ErrorCode.NOT_FOUND, "도트 그림을 찾을 수 없어요"));

		mockMvc.perform(get("/api/dots/goals")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
		mockMvc.perform(get("/api/dots/goals/1")).andExpect(status().isOk());
		mockMvc.perform(get("/api/dots/goals/2")).andExpect(status().isNotFound());
		mockMvc.perform(post("/api/dots/goals/1/completion-seen")).andExpect(status().isNoContent());
		verify(dotGoalService).markCompletionSeen(7L, 1L);
	}
}
