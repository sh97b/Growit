package com.growit.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.ai.dto.AiCompareResponse;
import com.growit.ai.dto.CompareMetrics;
import com.growit.ai.dto.CompareRecordCard;
import com.growit.ai.dto.CompareRequest;
import com.growit.ai.dto.MetricPair;
import com.growit.ai.entity.AiResult;
import com.growit.ai.entity.AiResultType;
import com.growit.ai.repository.AiResultRepository;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.record.entity.ActivityRecord;
import com.growit.record.repository.ActivityRecordRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompareService {

	private static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Seoul");
	private static final int REQUIRED_RECORD_COUNT = 3;
	private static final int CONTENT_MAX = 300;
	private static final int DIFFICULTY_MAX = 200;
	private static final int INTERPRETATION_MAX = 200;
	private static final String PROMPT_RESOURCE_NAME = "ai-compare-system.txt";
	/** prompts/ai-compare-system.txt를 바꿀 때마다 올린다. */
	static final int PROMPT_VERSION = 1;
	private static final Map<String, Integer> OFFSET_DAYS =
			Map.of("1W", 7, "2W", 14, "1M", 30, "3M", 90);
	private static final List<String> FORBIDDEN_WORDS =
			List.of("실력", "늘었", "향상", "잘했", "훌륭", "대단", "점수", "순위");

	private final ActivityRecordRepository activityRecordRepository;
	private final HobbyRepository hobbyRepository;
	private final AiResultRepository aiResultRepository;
	private final AiCallService aiCallService;
	private final AiResponseValidator aiResponseValidator;
	private final ObjectMapper objectMapper;

	PromptResourceLoader promptLoader = new PromptResourceLoader(PROMPT_RESOURCE_NAME);

	public CompareService(ActivityRecordRepository activityRecordRepository, HobbyRepository hobbyRepository,
			AiResultRepository aiResultRepository, AiCallService aiCallService,
			AiResponseValidator aiResponseValidator, ObjectMapper objectMapper) {
		this.activityRecordRepository = activityRecordRepository;
		this.hobbyRepository = hobbyRepository;
		this.aiResultRepository = aiResultRepository;
		this.aiCallService = aiCallService;
		this.aiResponseValidator = aiResponseValidator;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public AiCompareResponse compare(Long userId, CompareRequest request) {
		Hobby hobby = hobbyRepository.findByIdAndUserId(request.hobbyId(), userId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "활동 분야를 찾을 수 없어요"));
		Integer offsetDays = OFFSET_DAYS.get(request.compareTo());
		if (offsetDays == null) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "compareTo 값이 올바르지 않아요");
		}

		long totalCount = activityRecordRepository.countByUserIdAndHobbyId(userId, hobby.getId());
		if (totalCount < REQUIRED_RECORD_COUNT) {
			return AiCompareResponse.locked(REQUIRED_RECORD_COUNT, (int) totalCount);
		}

		LocalDate today = LocalDate.now(SERVER_ZONE);
		LocalDate pastDate = today.minusDays(offsetDays);

		ActivityRecord current = activityRecordRepository
				.findFirstByUserIdAndHobbyIdOrderByRecordDateDescIdDesc(userId, hobby.getId())
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "기록을 찾을 수 없어요"));
		ActivityRecord past = activityRecordRepository
				.findFirstByUserIdAndHobbyIdAndRecordDateLessThanEqualOrderByRecordDateDescIdDesc(
						userId, hobby.getId(), pastDate)
				.orElse(null);

		if (past == null || past.getId().equals(current.getId())) {
			return AiCompareResponse.locked(REQUIRED_RECORD_COUNT, (int) totalCount);
		}

		String cacheKey = CacheKeys.sha256(PROMPT_VERSION + "|COMPARE|" + hobby.getId()
				+ "|" + past.getId() + ":" + past.getUpdatedAt()
				+ "|" + current.getId() + ":" + current.getUpdatedAt());

		AiResult existing = aiResultRepository.findByUserIdAndCacheKey(userId, cacheKey).orElse(null);
		if (existing != null) {
			return toResponse(existing, true, userId);
		}

		String systemPrompt;
		try {
			systemPrompt = promptLoader.load();
		} catch (java.io.IOException e) {
			throw new ApiException(ErrorCode.AI_FAILED, "지금은 AI를 쓸 수 없어요. 기록은 안전하게 저장돼 있어요.");
		}

		ComparePayload payload = new ComparePayload(
				new ComparePayloadHobby(hobby.getName(), hobby.getGoal()),
				toPayloadRecord(past),
				toPayloadRecord(current));

		AiCallResult result = aiCallService.call(userId, AiResultType.COMPARE, systemPrompt, payload);
		StoredCompareResult stored = validate(result.content(), past.getId(), current.getId());

		CompareMetrics metrics = computeMetrics(userId, hobby.getId(), pastDate, today);
		StoredCompareResult withMetrics = new StoredCompareResult(stored.interpretation(), stored.pastRecordId(),
				stored.currentRecordId(), metrics);

		AiResult saved = new AiResult(userId, hobby.getId(), AiResultType.COMPARE.name(), pastDate, today,
				toJson(withMetrics), toJson(List.of(past.getId(), current.getId())), cacheKey, PROMPT_VERSION,
				result.model());
		aiResultRepository.save(saved);

		return toResponse(saved, false, userId, past, current);
	}

	private ComparePayloadRecord toPayloadRecord(ActivityRecord record) {
		return new ComparePayloadRecord(record.getId(), record.getRecordDate().toString(),
				truncate(record.getContent(), CONTENT_MAX), truncate(record.getDifficulty(), DIFFICULTY_MAX));
	}

	private StoredCompareResult validate(String rawJson, Long pastId, Long currentId) {
		JsonNode node = aiResponseValidator.parse(rawJson);
		aiResponseValidator.requireFields(node, "interpretation", "pastRecordIds", "currentRecordIds");

		JsonNode interpretationNode = node.path("interpretation");
		if (!interpretationNode.isTextual()) {
			throw new ApiException(ErrorCode.AI_FAILED, "지금은 AI를 쓸 수 없어요. 기록은 안전하게 저장돼 있어요.");
		}
		String interpretation = interpretationNode.asText().trim();
		if (interpretation.isEmpty() || interpretation.length() > INTERPRETATION_MAX
				|| containsForbiddenWord(interpretation)) {
			throw new ApiException(ErrorCode.AI_FAILED, "지금은 AI를 쓸 수 없어요. 기록은 안전하게 저장돼 있어요.");
		}

		Set<Long> allowed = Set.of(pastId, currentId);
		Long usedPastId = containsId(node.path("pastRecordIds"), pastId, allowed) ? pastId : null;
		Long usedCurrentId = containsId(node.path("currentRecordIds"), currentId, allowed) ? currentId : null;

		return new StoredCompareResult(interpretation, usedPastId, usedCurrentId, null);
	}

	private boolean containsId(JsonNode idsNode, long targetId, Set<Long> allowed) {
		if (!idsNode.isArray()) {
			return false;
		}
		for (JsonNode idNode : idsNode) {
			if (idNode.canConvertToLong() && idNode.asLong() == targetId && allowed.contains(targetId)) {
				return true;
			}
		}
		return false;
	}

	private boolean containsForbiddenWord(String text) {
		return FORBIDDEN_WORDS.stream().anyMatch(text::contains);
	}

	private String truncate(String text, int maxLength) {
		if (text == null) {
			return null;
		}
		return text.length() > maxLength ? text.substring(0, maxLength) : text;
	}

	private CompareMetrics computeMetrics(Long userId, Long hobbyId, LocalDate pastDate, LocalDate today) {
		long pastDays = activityRecordRepository.countDistinctRecordDateByUserIdAndHobbyIdUpTo(userId, hobbyId,
				pastDate);
		long currentDays = activityRecordRepository.countDistinctRecordDateByUserIdAndHobbyIdUpTo(userId, hobbyId,
				today);
		long pastMinutes = activityRecordRepository.sumMinutesByUserIdAndHobbyIdUpTo(userId, hobbyId, pastDate);
		long currentMinutes = activityRecordRepository.sumMinutesByUserIdAndHobbyIdUpTo(userId, hobbyId, today);
		return new CompareMetrics(new MetricPair(pastDays, currentDays), new MetricPair(pastMinutes, currentMinutes));
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("COMPARE 결과를 JSON으로 바꾸지 못함", e);
		}
	}

	private StoredCompareResult readJson(String json) {
		try {
			return objectMapper.readValue(json, StoredCompareResult.class);
		} catch (JsonProcessingException e) {
			throw new ApiException(ErrorCode.NOT_FOUND, "저장된 결과를 읽지 못했어요");
		}
	}

	@Transactional(readOnly = true)
	public AiCompareResponse getById(Long userId, Long id) {
		AiResult result = aiResultRepository.findByIdAndUserId(id, userId)
				.filter(r -> r.getResultType().equals(AiResultType.COMPARE.name()))
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "결과를 찾을 수 없어요"));
		return toResponse(result, true, userId);
	}

	/** 404로 남의 결과인지조차 알려주지 않는다. */
	@Transactional
	public AiCompareResponse submitFeedback(Long userId, Long id, String value) {
		AiResult result = aiResultRepository.findByIdAndUserId(id, userId)
				.filter(r -> r.getResultType().equals(AiResultType.COMPARE.name()))
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "결과를 찾을 수 없어요"));
		result.setFeedback(value);
		return toResponse(result, true, userId);
	}

	AiCompareResponse toResponse(AiResult result, boolean reused, Long userId) {
		ActivityRecord past = activityRecordRepository.findByIdAndUserId(readJson(result.getResult()).pastRecordId(),
				userId).orElse(null);
		ActivityRecord current = activityRecordRepository
				.findByIdAndUserId(readJson(result.getResult()).currentRecordId(), userId).orElse(null);
		return toResponse(result, reused, userId, past, current);
	}

	private AiCompareResponse toResponse(AiResult result, boolean reused, Long userId, ActivityRecord past,
			ActivityRecord current) {
		StoredCompareResult stored = readJson(result.getResult());
		return new AiCompareResponse(
				false, null, null,
				result.getId(),
				result.getStartDate(),
				result.getEndDate(),
				result.getCreatedAt(),
				reused,
				stored.interpretation(),
				past == null ? null : toCard(past),
				current == null ? null : toCard(current),
				stored.metrics(),
				result.getFeedback());
	}

	private CompareRecordCard toCard(ActivityRecord record) {
		return new CompareRecordCard(record.getId(), record.getRecordDate(), record.getContent(),
				extractTags(record.getAiAnalysis()), record.getFeeling() == null ? null : record.getFeeling().name());
	}

	/** 기록 카드의 태그는 record 도메인의 저장 모양(StoredAnalysis)을 몰라도 되게, tags 칸만 가볍게 읽는다. */
	private List<String> extractTags(String aiAnalysisJson) {
		if (aiAnalysisJson == null) {
			return List.of();
		}
		try {
			JsonNode node = objectMapper.readTree(aiAnalysisJson).path("tags");
			if (!node.isArray()) {
				return List.of();
			}
			List<String> tags = new ArrayList<>();
			node.forEach(tagNode -> {
				if (tagNode.isTextual()) {
					tags.add(tagNode.asText());
				}
			});
			return tags;
		} catch (JsonProcessingException e) {
			return List.of();
		}
	}
}
