package com.growit.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.ai.dto.AiViewResponse;
import com.growit.ai.dto.CompareRecordCard;
import com.growit.ai.dto.WeeklyRequest;
import com.growit.ai.entity.AiResult;
import com.growit.ai.entity.AiResultType;
import com.growit.ai.repository.AiResultRepository;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.record.entity.ActivityRecord;
import com.growit.record.repository.ActivityRecordRepository;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeeklyService {

	private static final String PROMPT_RESOURCE_NAME = "weekly-system.txt";
	/** prompts/weekly-system.txt를 바꿀 때마다 올린다. */
	static final int PROMPT_VERSION = 1;

	private final ActivityRecordRepository activityRecordRepository;
	private final HobbyRepository hobbyRepository;
	private final AiResultRepository aiResultRepository;
	private final AiCallService aiCallService;
	private final AiResponseValidator aiResponseValidator;
	private final ObjectMapper objectMapper;

	PromptResourceLoader promptLoader = new PromptResourceLoader(PROMPT_RESOURCE_NAME);

	public WeeklyService(ActivityRecordRepository activityRecordRepository, HobbyRepository hobbyRepository,
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
	public AiViewResponse weekly(Long userId, WeeklyRequest request) {
		LocalDate weekStart = request.weekStart();
		if (weekStart == null || weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "weekStart는 월요일이어야 해요");
		}
		Hobby hobby = null;
		if (request.hobbyId() != null) {
			hobby = hobbyRepository.findByIdAndUserId(request.hobbyId(), userId)
					.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "활동 분야를 찾을 수 없어요"));
		}

		LocalDate weekEnd = weekStart.plusDays(6);
		List<ActivityRecord> weekRecords = request.hobbyId() == null
				? activityRecordRepository.findByUserIdAndRecordDateBetween(userId, weekStart, weekEnd)
				: activityRecordRepository.findByUserIdAndHobbyIdAndRecordDateBetween(userId, request.hobbyId(),
						weekStart, weekEnd);

		if (weekRecords.isEmpty()) {
			return AiViewResponse.emptyWeek();
		}

		String cacheKey = CacheKeys.sha256(PROMPT_VERSION + "|WEEKLY|" + request.hobbyId() + "|" + weekStart + "|"
				+ ViewCacheKeys.recordsFingerprint(weekRecords));

		AiResult existing = aiResultRepository.findByUserIdAndCacheKey(userId, cacheKey).orElse(null);
		if (existing != null) {
			return toResponse(existing, true, userId);
		}

		String systemPrompt;
		try {
			systemPrompt = promptLoader.load();
		} catch (IOException e) {
			throw new ApiException(ErrorCode.AI_FAILED, "지금은 AI를 쓸 수 없어요. 기록은 안전하게 저장돼 있어요.");
		}

		WeeklyPayload payload = new WeeklyPayload(weekStart.toString(), ViewCacheKeys.toPayloadRecords(weekRecords));

		AiCallResult result = aiCallService.call(userId, AiResultType.WEEKLY, systemPrompt, payload);
		List<Long> allowedIds = weekRecords.stream().map(ActivityRecord::getId).toList();
		StoredViewResult stored = ViewCacheKeys.validate(aiResponseValidator, result.content(), allowedIds);

		Long hobbyId = hobby == null ? null : hobby.getId();
		AiResult saved = new AiResult(userId, hobbyId, AiResultType.WEEKLY.name(), weekStart, weekEnd,
				toJson(stored), toJson(allowedIds), cacheKey, PROMPT_VERSION, result.model());
		aiResultRepository.save(saved);

		return toResponse(saved, false, userId);
	}

	@Transactional(readOnly = true)
	public AiViewResponse getById(Long userId, Long id) {
		AiResult result = aiResultRepository.findByIdAndUserId(id, userId)
				.filter(r -> r.getResultType().equals(AiResultType.WEEKLY.name()))
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "결과를 찾을 수 없어요"));
		return toResponse(result, true, userId);
	}

	@Transactional
	public AiViewResponse submitFeedback(Long userId, Long id, String value) {
		AiResult result = aiResultRepository.findByIdAndUserId(id, userId)
				.filter(r -> r.getResultType().equals(AiResultType.WEEKLY.name()))
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "결과를 찾을 수 없어요"));
		result.setFeedback(value);
		return toResponse(result, true, userId);
	}

	@Transactional
	public AiViewResponse editText(Long userId, Long id, String text) {
		AiResult result = aiResultRepository.findByIdAndUserId(id, userId)
				.filter(r -> r.getResultType().equals(AiResultType.WEEKLY.name()))
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "결과를 찾을 수 없어요"));
		StoredViewResult stored = readJson(result.getResult()).withEditedText(text);
		result.setResult(toJson(stored));
		result.setFeedback("EDITED");
		return toResponse(result, true, userId);
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("WEEKLY 결과를 JSON으로 바꾸지 못함", e);
		}
	}

	private StoredViewResult readJson(String json) {
		try {
			return objectMapper.readValue(json, StoredViewResult.class);
		} catch (JsonProcessingException e) {
			throw new ApiException(ErrorCode.NOT_FOUND, "저장된 결과를 읽지 못했어요");
		}
	}

	private AiViewResponse toResponse(AiResult result, boolean reused, Long userId) {
		StoredViewResult stored = readJson(result.getResult());
		List<CompareRecordCard> evidence = ViewCacheKeys.loadEvidence(activityRecordRepository, objectMapper, userId,
				stored.evidenceIds());
		String displayText = stored.editedText() != null ? stored.editedText() : stored.text();
		return new AiViewResponse(false, null, null, false, result.getId(), result.getStartDate(),
				result.getEndDate(), result.getCreatedAt(), reused, stored.text(), stored.editedText(), displayText,
				evidence, result.getFeedback());
	}
}
