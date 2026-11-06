package com.growit.ai.service;

import com.growit.ai.config.AiProperties;
import com.growit.ai.dto.AiUsageResponse;
import com.growit.ai.entity.AiLog;
import com.growit.ai.entity.AiLogState;
import com.growit.ai.entity.AiResultType;
import com.growit.ai.repository.AiLogRepository;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AiUsageService {

	private static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Seoul");
	private static final List<String> USER_LIMITED_TYPES = List.of(
			AiResultType.COMPARE.name(), AiResultType.COACH.name());
	private static final List<String> VIEW_LIMITED_TYPES = List.of(
			AiResultType.WEEKLY.name(), AiResultType.METRICS.name());

	private final AiLogRepository aiLogRepository;
	private final AiProperties properties;

	private final Object lock = new Object();
	private LocalDate reservationDay = LocalDate.MIN;
	private int globalReserved;
	private final Map<Long, Integer> userReserved = new HashMap<>();
	private final Map<Long, Integer> analysisReserved = new HashMap<>();
	private final Map<Long, Integer> simplifyReserved = new HashMap<>();
	private final Map<Long, Integer> viewReserved = new HashMap<>();

	public AiUsageService(AiLogRepository aiLogRepository, AiProperties properties) {
		this.aiLogRepository = aiLogRepository;
		this.properties = properties;
	}

	/**
	 * 한도 확인(DB 집계)과 호출 결과 로그 저장 사이에는 시간차가 있어, 그 틈에 몰린 동시 요청이 DB 집계만으로는
	 * 같은 "아직 반영 안 된" 여유를 각자 보고 한도를 넘어 통과할 수 있다. 그래서 확인과 동시에 메모리에 자리를
	 * 임시로 맡아두고(reserve), 실제 ai_logs 행이 쓰인 뒤 release로 되돌린다. 완벽한 정확도보다 과다 호출
	 * 방지가 목표라 이 정도 수준으로 충분하다고 판단했다.
	 */
	public void reserve(Long userId, AiResultType type) {
		LocalDateTime startOfDay = LocalDate.now(SERVER_ZONE).atStartOfDay();
		synchronized (lock) {
			resetIfNewDay();

			long globalOk = aiLogRepository.countByStateSince(AiLogState.OK.name(), startOfDay);
			if (globalOk + globalReserved >= properties.getGlobalDailyLimit()) {
				recordLimitLog(userId, type);
				throw new ApiException(ErrorCode.AI_BUSY,
						"지금은 AI 요청이 많아요. 기록은 안전하게 저장돼 있어요. 잠시 후 다시 시도해 주세요.");
			}

			if (USER_LIMITED_TYPES.contains(type.name())) {
				long userOk = aiLogRepository.countByUserAndStateAndResultTypesSince(
						userId, AiLogState.OK.name(), USER_LIMITED_TYPES, startOfDay);
				int reserved = userReserved.getOrDefault(userId, 0);
				if (userOk + reserved >= properties.getUserDailyLimit()) {
					recordLimitLog(userId, type);
					throw new ApiException(ErrorCode.AI_LIMIT_EXCEEDED,
							"오늘 쓸 수 있는 AI 횟수를 다 썼어요. 기록은 안전하게 저장돼 있어요. 내일 다시 써 보세요.");
				}
				userReserved.put(userId, reserved + 1);
			} else if (type == AiResultType.ANALYSIS) {
				long userAnalysisOk = aiLogRepository.countByUserAndStateAndResultTypesSince(
						userId, AiLogState.OK.name(), List.of(AiResultType.ANALYSIS.name()), startOfDay);
				int reserved = analysisReserved.getOrDefault(userId, 0);
				if (userAnalysisOk + reserved >= properties.getAnalysisUserDailyLimit()) {
					recordLimitLog(userId, type);
					throw new ApiException(ErrorCode.AI_LIMIT_EXCEEDED,
							"오늘 쓸 수 있는 기록 정리 횟수를 다 썼어요. 기록은 안전하게 저장돼 있어요.");
				}
				analysisReserved.put(userId, reserved + 1);
			} else if (type == AiResultType.SIMPLIFY) {
				long userSimplifyOk = aiLogRepository.countByUserAndStateAndResultTypesSince(
						userId, AiLogState.OK.name(), List.of(AiResultType.SIMPLIFY.name()), startOfDay);
				int reserved = simplifyReserved.getOrDefault(userId, 0);
				if (userSimplifyOk + reserved >= properties.getSimplifyUserDailyLimit()) {
					recordLimitLog(userId, type);
					throw new ApiException(ErrorCode.AI_LIMIT_EXCEEDED,
							"오늘 더 작게 바꿀 수 있는 횟수를 다 썼어요. 기록은 안전하게 저장돼 있어요.");
				}
				simplifyReserved.put(userId, reserved + 1);
			} else if (VIEW_LIMITED_TYPES.contains(type.name())) {
				long userViewOk = aiLogRepository.countByUserAndStateAndResultTypesSince(
						userId, AiLogState.OK.name(), VIEW_LIMITED_TYPES, startOfDay);
				int reserved = viewReserved.getOrDefault(userId, 0);
				if (userViewOk + reserved >= properties.getViewUserDailyLimit()) {
					recordLimitLog(userId, type);
					throw new ApiException(ErrorCode.AI_LIMIT_EXCEEDED,
							"오늘 쓸 수 있는 AI 횟수를 다 썼어요. 기록은 안전하게 저장돼 있어요. 내일 다시 써 보세요.");
				}
				viewReserved.put(userId, reserved + 1);
			}

			globalReserved++;
		}
	}

	public void release(Long userId, AiResultType type) {
		synchronized (lock) {
			resetIfNewDay();
			globalReserved = Math.max(0, globalReserved - 1);
			if (USER_LIMITED_TYPES.contains(type.name())) {
				userReserved.computeIfPresent(userId, (id, count) -> Math.max(0, count - 1));
			} else if (type == AiResultType.ANALYSIS) {
				analysisReserved.computeIfPresent(userId, (id, count) -> Math.max(0, count - 1));
			} else if (type == AiResultType.SIMPLIFY) {
				simplifyReserved.computeIfPresent(userId, (id, count) -> Math.max(0, count - 1));
			} else if (VIEW_LIMITED_TYPES.contains(type.name())) {
				viewReserved.computeIfPresent(userId, (id, count) -> Math.max(0, count - 1));
			}
		}
	}

	public AiUsageResponse usage(Long userId) {
		LocalDateTime startOfDay = LocalDate.now(SERVER_ZONE).atStartOfDay();
		long used = aiLogRepository.countByUserAndStateAndResultTypesSince(
				userId, AiLogState.OK.name(), USER_LIMITED_TYPES, startOfDay);
		int limit = properties.getUserDailyLimit();
		return new AiUsageResponse(properties.isEnabled(), limit, used, Math.max(0, limit - used));
	}

	private void resetIfNewDay() {
		LocalDate today = LocalDate.now(SERVER_ZONE);
		if (!today.equals(reservationDay)) {
			reservationDay = today;
			globalReserved = 0;
			userReserved.clear();
			analysisReserved.clear();
			simplifyReserved.clear();
			viewReserved.clear();
		}
	}

	private void recordLimitLog(Long userId, AiResultType type) {
		aiLogRepository.save(new AiLog(userId, type.name(), AiLogState.LIMIT.name(), null, null, null));
	}
}
