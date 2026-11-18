package com.growit.dot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.dot.dto.DotCell;
import com.growit.dot.dto.DotCurrentResponse;
import com.growit.dot.dto.DotGoalResponse;
import com.growit.dot.dto.DotGoalStartRequest;
import com.growit.dot.dto.DotGrid;
import com.growit.dot.dto.DotPreset;
import com.growit.dot.dto.DotPresetResponse;
import com.growit.dot.dto.DotPreviewResponse;
import com.growit.dot.entity.DotGoal;
import com.growit.dot.entity.DotGoalState;
import com.growit.dot.entity.DotProgress;
import com.growit.dot.entity.DotSourceType;
import com.growit.dot.repository.DotGoalRepository;
import com.growit.dot.repository.DotProgressRepository;
import com.growit.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 도트 그림 목표의 시작·중단·칠하기·복구. 남의 목표는 존재 자체를 숨기려고 404로 답한다.
 * 시작일·완성일은 서버 시계의 Asia/Seoul 날짜이고, 칠하는 날짜는 사용자가 고른 기록 날짜다.
 */
@Service
@Transactional(readOnly = true)
public class DotGoalService {

	private static final String PHOTO_DEFAULT_TITLE = "내 사진";
	private static final int PHOTO_TITLE_MAX_LENGTH = 20;
	private static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Seoul");
	private static final String ACTIVE_EXISTS_MESSAGE = "이미 진행 중인 도트 그림이 있어요. 그 그림을 마치거나 멈춘 뒤 새로 시작해 주세요";
	private static final String ACTIVE_NOT_FOUND_MESSAGE = "진행 중인 도트 그림을 찾을 수 없어요";
	private static final String GOAL_NOT_FOUND_MESSAGE = "도트 그림을 찾을 수 없어요";

	private final DotGoalRepository goalRepository;
	private final DotProgressRepository progressRepository;
	private final UserRepository userRepository;
	private final DotPresetCatalog presetCatalog;
	private final ObjectMapper objectMapper;
	private final Clock clock;

	public DotGoalService(DotGoalRepository goalRepository, DotProgressRepository progressRepository,
			UserRepository userRepository, DotPresetCatalog presetCatalog, ObjectMapper objectMapper, Clock clock) {
		this.goalRepository = goalRepository;
		this.progressRepository = progressRepository;
		this.userRepository = userRepository;
		this.presetCatalog = presetCatalog;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	public List<DotPresetResponse> presets() {
		return presetCatalog.all().stream().map(preset -> new DotPresetResponse(preset.key(), preset.label(),
				preset.width(), preset.height(), preset.palette(), preset.pixels())).toList();
	}

	/**
	 * 사용자 행을 먼저 잠가 같은 사용자의 동시 시작을 한 줄로 세운다. 잠금 뒤에 ACTIVE를 확인하므로 두 번째 요청은 409가 된다.
	 * DB의 유니크 인덱스(active_user_id)는 이 잠금이 뚫렸을 때를 위한 마지막 안전장치다.
	 */
	@Transactional
	public DotGoalResponse start(Long userId, DotGoalStartRequest request) {
		int goalDays = request.goalDays();
		assertGoalDaysInRange(goalDays);
		boolean photo = request.mode() == DotSourceType.PHOTO;
		DotPreset preset = null;
		DotGrid grid;
		String title;
		if (photo) {
			title = photoTitle(request.title());
			grid = buildPhotoGrid(request, goalDays);
		} else {
			if (request.presetKey() == null || request.presetKey().isBlank()) {
				throw new ApiException(ErrorCode.VALIDATION_ERROR, "그림을 골라 주세요");
			}
			preset = presetCatalog.find(request.presetKey())
					.orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR, "알 수 없는 그림이에요"));
			title = preset.label();
			grid = buildGrid(preset, goalDays);
		}

		userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없어요"));
		if (goalRepository.findIdByUserIdAndState(userId, DotGoalState.ACTIVE).isPresent()) {
			throw new ApiException(ErrorCode.DOT_GOAL_ACTIVE, ACTIVE_EXISTS_MESSAGE);
		}

		DotGoal goal = new DotGoal(userId, title, photo ? DotSourceType.PHOTO : DotSourceType.PRESET,
				photo ? null : preset.key(), goalDays, grid.width(), grid.height(), toJson(grid.cells()),
				toJson(grid.palette()), today());
		try {
			goalRepository.saveAndFlush(goal);
		} catch (DataIntegrityViolationException e) {
			throw new ApiException(ErrorCode.DOT_GOAL_ACTIVE, ACTIVE_EXISTS_MESSAGE);
		}
		restoreMissing(goal);
		return toResponse(goal);
	}

	/**
	 * 시작할 때 저장될 그림을 미리 보여 준다. 시작({@link #start})과 같은 {@link #buildGrid}를 쓰므로 같은 그림과 목표 일수면
	 * 저장되는 칸과 완전히 같다. 읽기 전용이고 아무것도 저장하지 않는다.
	 */
	public DotPreviewResponse preview(String presetKey, Integer goalDays) {
		DotPreset preset = presetCatalog.find(presetKey)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "알 수 없는 그림이에요"));
		if (goalDays == null) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "목표 일수를 입력해 주세요");
		}
		assertGoalDaysInRange(goalDays);
		DotGrid grid = buildGrid(preset, goalDays);
		return new DotPreviewResponse(preset.key(), preset.label(), goalDays, grid.width(), grid.height(),
				grid.palette(), grid.cells());
	}

	/**
	 * 진행 중인 그림과, 완성했지만 연출을 아직 안 본 그림을 돌려준다.
	 * GET이지만 쓰기가 있다 — 기록을 저장할 때 칠하기가 실패했어도(실패는 삼킨다) 여기서 빠진 날을 채워 넣어야 해서
	 * 읽기 전용 트랜잭션이 아니라 쓰기 트랜잭션으로 둔다. 몇 번을 불러도 결과는 같다.
	 */
	@Transactional
	public DotCurrentResponse current(Long userId) {
		DotGoal active = lockActive(userId).orElse(null);
		if (active != null) {
			restoreMissing(active);
		}
		DotGoalResponse activeResponse = active != null && active.getState() == DotGoalState.ACTIVE ? toResponse(active)
				: null;
		DotGoalResponse justCompleted = goalRepository
				.findFirstByUserIdAndStateAndCompletionSeenFalseOrderByCompletedOnDescIdDesc(userId,
						DotGoalState.COMPLETED)
				.map(this::toResponse).orElse(null);
		return new DotCurrentResponse(activeResponse, justCompleted);
	}

	/** 도감에는 완성한 그림만 보인다. 중단한 그림은 보관만 한다. */
	public List<DotGoalResponse> album(Long userId) {
		return goalRepository.findByUserIdAndStateOrderByCompletedOnDescIdDesc(userId, DotGoalState.COMPLETED).stream()
				.map(this::toResponse).toList();
	}

	public DotGoalResponse detail(Long userId, Long goalId) {
		DotGoal goal = goalRepository.findByIdAndUserId(goalId, userId)
				.filter(found -> found.getState() != DotGoalState.STOPPED)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, GOAL_NOT_FOUND_MESSAGE));
		return toResponse(goal);
	}

	@Transactional
	public void stop(Long userId, Long goalId) {
		DotGoal goal = goalRepository.findByIdForUpdate(goalId)
				.filter(found -> found.getUserId().equals(userId) && found.getState() == DotGoalState.ACTIVE)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, ACTIVE_NOT_FOUND_MESSAGE));
		goal.stop();
	}

	@Transactional
	public void markCompletionSeen(Long userId, Long goalId) {
		DotGoal goal = goalRepository.findByIdAndUserId(goalId, userId)
				.filter(found -> found.getState() == DotGoalState.COMPLETED)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, GOAL_NOT_FOUND_MESSAGE));
		goal.markCompletionSeen();
	}

	/**
	 * 기록을 저장한 직후 그 날짜를 한 칸 칠한다. 진행 중인 그림이 있고 날짜가 시작일 이후일 때만 칠하고,
	 * 같은 날은 몇 번을 불러도 한 칸이다.
	 *
	 * <p>호출한 쪽(기록 저장)의 트랜잭션과 따로 돌려, 여기서 실패해도 기록 저장이 롤백되지 않게 한다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markRecordDate(Long userId, LocalDate recordDate) {
		DotGoal goal = lockActive(userId).orElse(null);
		if (goal == null || recordDate.isBefore(goal.getStartedOn())) {
			return;
		}
		fill(goal, List.of(recordDate));
	}

	/** 시작일 이후 기록이 있는데 아직 안 칠해진 날을 한꺼번에 채운다. 이미 칠한 칸은 지우지 않는다. 호출하는 쪽이 목표 행을 잠근 상태여야 한다. */
	private void restoreMissing(DotGoal goal) {
		if (goal.getState() != DotGoalState.ACTIVE) {
			return;
		}
		fill(goal, progressRepository.findMissingDates(goal.getUserId(), goal.getId(), goal.getStartedOn()));
	}

	private void fill(DotGoal goal, List<LocalDate> dates) {
		long filled = progressRepository.countByGoalId(goal.getId());
		for (LocalDate date : dates) {
			if (filled >= goal.getGoalDays()) {
				break;
			}
			if (!progressRepository.existsByGoalIdAndRecordDate(goal.getId(), date)) {
				progressRepository.save(new DotProgress(goal.getId(), date));
				filled++;
			}
		}
		if (filled >= goal.getGoalDays()) {
			goal.complete(today());
		}
	}

	private static void assertGoalDaysInRange(int goalDays) {
		if (goalDays < DotGridValidator.MIN_GOAL_DAYS || goalDays > DotGridValidator.MAX_GOAL_DAYS) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "목표 일수는 " + DotGridValidator.MIN_GOAL_DAYS + "일부터 "
					+ DotGridValidator.MAX_GOAL_DAYS + "일까지예요");
		}
	}

	private static String photoTitle(String raw) {
		String title = raw == null ? "" : raw.trim();
		if (title.isEmpty()) {
			return PHOTO_DEFAULT_TITLE;
		}
		if (title.length() > PHOTO_TITLE_MAX_LENGTH) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "이름은 " + PHOTO_TITLE_MAX_LENGTH + "자 이하로 입력해 주세요");
		}
		return title;
	}

	/**
	 * 사진으로 만든 격자는 브라우저가 보낸 값을 믿지 않고 기본 그림과 같은 기준(칸 수 == 목표 일수, 크기·색 상한, 좌표 범위·중복)으로
	 * 검증한다. 채워지는 순서(아래 줄부터, 같은 줄은 왼쪽부터)는 보낸 순서와 상관없이 서버가 정해서 저장한다.
	 */
	private static DotGrid buildPhotoGrid(DotGoalStartRequest request, int goalDays) {
		if (request.width() == null || request.height() == null || request.palette() == null || request.cells() == null) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "사진 그림 정보가 부족해요");
		}
		List<DotCell> cells = request.cells().stream().map(cell -> new DotCell(cell.x(), cell.y(), cell.c()))
				.sorted(Comparator.comparingInt(DotCell::y).reversed().thenComparingInt(DotCell::x)).toList();
		DotGrid grid = new DotGrid(request.width(), request.height(), List.copyOf(request.palette()), cells);
		DotGridValidator.validate(goalDays, grid);
		return grid;
	}

	private static DotGrid buildGrid(DotPreset preset, int goalDays) {
		DotGrid grid = DotGridBuilder.build(preset, goalDays);
		DotGridValidator.validate(goalDays, grid);
		return grid;
	}

	/** id만 읽고 잠가서 가져온다. 잠금을 얻은 뒤의 최신 상태가 ACTIVE일 때만 돌려준다. */
	private Optional<DotGoal> lockActive(Long userId) {
		return goalRepository.findIdByUserIdAndState(userId, DotGoalState.ACTIVE)
				.flatMap(goalRepository::findByIdForUpdate)
				.filter(goal -> goal.getState() == DotGoalState.ACTIVE);
	}

	private LocalDate today() {
		return LocalDate.now(clock.withZone(SERVER_ZONE));
	}

	private DotGoalResponse toResponse(DotGoal goal) {
		int filled = (int) progressRepository.countByGoalId(goal.getId());
		return new DotGoalResponse(goal.getId(), goal.getTitle(), goal.getSourceType(), goal.getPresetKey(),
				goal.getGoalDays(), filled, goal.getWidth(), goal.getHeight(),
				fromJson(goal.getPaletteJson(), new TypeReference<List<String>>() {
				}), fromJson(goal.getGridJson(), new TypeReference<List<DotCell>>() {
				}), goal.getStartedOn(), goal.getState(), goal.getCompletedOn(), goal.isCompletionSeen());
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("그림 데이터를 저장 형식으로 바꾸지 못했어요", e);
		}
	}

	private <T> T fromJson(String json, TypeReference<T> type) {
		try {
			return objectMapper.readValue(json, type);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("저장된 그림 데이터를 읽지 못했어요", e);
		}
	}
}
