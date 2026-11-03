package com.growit.record.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.ai.entity.AiLog;
import com.growit.ai.entity.AiLogState;
import com.growit.ai.entity.AiResultType;
import com.growit.ai.repository.AiLogRepository;
import com.growit.ai.service.AiCallResult;
import com.growit.ai.service.AiCallService;
import com.growit.ai.service.AiResponseValidator;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.record.entity.ActivityRecord;
import com.growit.record.entity.AiStatus;
import com.growit.record.repository.ActivityRecordRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RecordAnalysisService {

	private static final int HISTORY_LIMIT = 5;
	private static final int HISTORY_CONTENT_MAX = 300;
	private static final int HISTORY_DIFFICULTY_MAX = 200;
	private static final int TITLE_MAX = 20;
	private static final int TAG_MAX = 12;
	private static final int TAGS_MAX_COUNT = 3;
	private static final int RELATED_MAX_COUNT = 2;
	private static final int CHANGE_TEXT_MAX = 60;
	private static final List<String> FORBIDDEN_WORDS =
			List.of("실력", "늘었", "향상", "잘했", "훌륭", "대단", "점수", "순위");
	private static final String PROMPT_RESOURCE_NAME = "record-analysis-system.txt";

	private final ActivityRecordRepository activityRecordRepository;
	private final HobbyRepository hobbyRepository;
	private final AiCallService aiCallService;
	private final AiResponseValidator aiResponseValidator;
	private final AiLogRepository aiLogRepository;
	private final ObjectMapper objectMapper;
	private final TransactionTemplate transactionTemplate;

	/** 패키지 안 테스트에서만 다른 리소스 이름으로 바꿔 끼워 읽기 실패를 재현한다. */
	SystemPromptLoader promptLoader = new SystemPromptLoader(PROMPT_RESOURCE_NAME);

	public RecordAnalysisService(ActivityRecordRepository activityRecordRepository, HobbyRepository hobbyRepository,
			AiCallService aiCallService, AiResponseValidator aiResponseValidator, AiLogRepository aiLogRepository,
			ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
		this.activityRecordRepository = activityRecordRepository;
		this.hobbyRepository = hobbyRepository;
		this.aiCallService = aiCallService;
		this.aiResponseValidator = aiResponseValidator;
		this.aiLogRepository = aiLogRepository;
		this.objectMapper = objectMapper;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	/**
	 * AI 호출(네트워크 I/O)은 트랜잭션 밖에서 한다. DB 조회·저장만 각각 짧은 트랜잭션으로 묶어, AI 응답을
	 * 기다리는 동안 커넥션을 붙들고 있지 않게 했다. 분석 시작 전과 저장 직전 모두 상태가 PENDING인지 다시
	 * 확인해, 그 사이 기록이 수정·삭제되었으면 조용히 건너뛴다.
	 */
	public void analyze(Long recordId, Long userId) {
		AnalysisInput input;
		try {
			input = transactionTemplate.execute(status -> prepareInput(recordId, userId));
		} catch (RuntimeException e) {
			transactionTemplate.executeWithoutResult(status -> markFailedIfPending(recordId, userId));
			return;
		}
		if (input == null) {
			return;
		}

		String systemPrompt;
		try {
			systemPrompt = promptLoader.load();
		} catch (IOException e) {
			aiLogRepository.save(new AiLog(userId, AiResultType.ANALYSIS.name(), AiLogState.FAIL.name(), null, null,
					"프롬프트 파일을 읽지 못함"));
			transactionTemplate.executeWithoutResult(status -> markFailedIfPending(recordId, userId));
			return;
		}

		StoredAnalysis analysis;
		try {
			AiCallResult result = aiCallService.call(userId, AiResultType.ANALYSIS, systemPrompt, input.payload());
			analysis = validate(result.content(), input.allowedHistoryIds());
		} catch (RuntimeException e) {
			analysis = null;
		}

		StoredAnalysis finalAnalysis = analysis;
		transactionTemplate.executeWithoutResult(status -> applyResult(recordId, userId, finalAnalysis));
	}

	public void markQueueRejected(Long recordId, Long userId) {
		transactionTemplate.executeWithoutResult(status -> markFailedIfPending(recordId, userId));
	}

	private AnalysisInput prepareInput(Long recordId, Long userId) {
		ActivityRecord record = activityRecordRepository.findByIdAndUserId(recordId, userId).orElse(null);
		if (record == null || record.getAiStatus() != AiStatus.PENDING) {
			return null;
		}
		Hobby hobby = hobbyRepository.findById(record.getHobbyId()).orElseThrow();

		List<ActivityRecord> history = activityRecordRepository.findPrevious(userId, record.getHobbyId(),
				record.getRecordDate(), record.getId(), PageRequest.of(0, HISTORY_LIMIT));

		AnalysisRecordInput recordInput = new AnalysisRecordInput(
				record.getRecordDate().toString(),
				record.getContent(),
				record.getDifficulty(),
				record.getFeeling() == null ? null : record.getFeeling().name(),
				record.getMinutes());
		List<AnalysisHistoryItem> historyItems = history.stream()
				.map(h -> new AnalysisHistoryItem(h.getId(), h.getRecordDate().toString(),
						truncate(h.getContent(), HISTORY_CONTENT_MAX),
						truncate(h.getDifficulty(), HISTORY_DIFFICULTY_MAX)))
				.toList();
		AnalysisPayload payload = new AnalysisPayload(
				new AnalysisHobbyInput(hobby.getName(), hobby.getGoal()), recordInput, historyItems);

		Set<Long> allowedIds = history.stream().map(ActivityRecord::getId)
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		return new AnalysisInput(payload, allowedIds);
	}

	private void applyResult(Long recordId, Long userId, StoredAnalysis analysis) {
		ActivityRecord record = activityRecordRepository.findByIdAndUserId(recordId, userId).orElse(null);
		if (record == null || record.getAiStatus() != AiStatus.PENDING) {
			return;
		}
		if (analysis == null) {
			record.markAnalysisFailed();
			return;
		}
		record.markAnalysisDone(toJson(analysis));
	}

	private void markFailedIfPending(Long recordId, Long userId) {
		activityRecordRepository.findByIdAndUserId(recordId, userId)
				.filter(r -> r.getAiStatus() == AiStatus.PENDING)
				.ifPresent(ActivityRecord::markAnalysisFailed);
	}

	private StoredAnalysis validate(String rawJson, Set<Long> allowedHistoryIds) {
		JsonNode node = aiResponseValidator.parse(rawJson);
		aiResponseValidator.requireFields(node, "title", "tags", "relatedRecordIds");

		String title = sanitizeTitle(node.path("title"));
		List<String> tags = sanitizeTags(node.path("tags"));
		List<Long> relatedRecordIds = sanitizeRelatedIds(node.path("relatedRecordIds"), allowedHistoryIds);
		StoredChange change = sanitizeChange(node.path("change"), allowedHistoryIds);

		return new StoredAnalysis(title, tags, relatedRecordIds, change, null, null);
	}

	private String sanitizeTitle(JsonNode titleNode) {
		if (!titleNode.isTextual()) {
			return null;
		}
		String title = titleNode.asText().trim();
		if (title.isEmpty() || title.length() > TITLE_MAX || containsForbiddenWord(title)) {
			return null;
		}
		return title;
	}

	private List<String> sanitizeTags(JsonNode tagsNode) {
		List<String> result = new ArrayList<>();
		if (!tagsNode.isArray()) {
			return result;
		}
		for (JsonNode tagNode : tagsNode) {
			if (!tagNode.isTextual()) {
				continue;
			}
			String tag = tagNode.asText().trim();
			if (tag.isEmpty() || tag.length() > TAG_MAX || containsForbiddenWord(tag)) {
				continue;
			}
			result.add(tag);
			if (result.size() == TAGS_MAX_COUNT) {
				break;
			}
		}
		return result;
	}

	private List<Long> sanitizeRelatedIds(JsonNode idsNode, Set<Long> allowedIds) {
		List<Long> result = new ArrayList<>();
		if (!idsNode.isArray()) {
			return result;
		}
		for (JsonNode idNode : idsNode) {
			if (!idNode.canConvertToLong()) {
				continue;
			}
			long id = idNode.asLong();
			if (allowedIds.contains(id) && !result.contains(id)) {
				result.add(id);
			}
			if (result.size() == RELATED_MAX_COUNT) {
				break;
			}
		}
		return result;
	}

	private StoredChange sanitizeChange(JsonNode changeNode, Set<Long> allowedIds) {
		if (changeNode.isMissingNode() || changeNode.isNull() || !changeNode.isObject()) {
			return null;
		}
		JsonNode textNode = changeNode.path("text");
		if (!textNode.isTextual()) {
			return null;
		}
		String text = textNode.asText().trim();
		if (text.isEmpty() || text.length() > CHANGE_TEXT_MAX || containsForbiddenWord(text)) {
			return null;
		}
		JsonNode evidenceNode = changeNode.path("evidenceRecordId");
		if (!evidenceNode.canConvertToLong()) {
			return null;
		}
		long evidenceId = evidenceNode.asLong();
		if (!allowedIds.contains(evidenceId)) {
			return null;
		}
		return new StoredChange(text, evidenceId);
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

	String toJson(StoredAnalysis analysis) {
		try {
			return objectMapper.writeValueAsString(analysis);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("분석 결과를 JSON으로 바꾸지 못함", e);
		}
	}

	StoredAnalysis readJson(String json) {
		try {
			return objectMapper.readValue(json, StoredAnalysis.class);
		} catch (JsonProcessingException e) {
			throw new ApiException(ErrorCode.ANALYSIS_NOT_READY, "저장된 분석을 읽지 못했어요");
		}
	}
}
