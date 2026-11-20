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
import com.growit.common.text.InputWords;
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

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RecordAnalysisService.class);
	private static final int RAW_LOG_MAX = 200;

	private static final int HISTORY_LIMIT = 5;
	private static final int HISTORY_CONTENT_MAX = 300;
	private static final int HISTORY_DIFFICULTY_MAX = 200;
	private static final int TITLE_MAX = 20;
	private static final int TAG_MAX = 12;
	private static final int TAGS_MAX_COUNT = 3;
	private static final int RELATED_MAX_COUNT = 2;
	private static final int CHANGE_TEXT_MAX = 90;
	private static final int SUPPORT_TEXT_MAX = 60;
	private static final int NEXT_ACTION_TEXT_MAX = 80;
	private static final int NEXT_ACTION_MIN_MINUTES = 5;
	private static final int NEXT_ACTION_MAX_MINUTES = 10;
	/**
	 * 접근 제한자를 package-private으로 완화함(E1 AI 품질 평가 하네스가 같은 패키지의 테스트에서 재사용).
	 * "잘했", "훌륭", "대단"은 뺐다 — 과정에 대한 구체적인 인정·응원은 허용하고, 근거 없는 일반 칭찬이 되지 않게
	 * 프롬프트가 제한한다.
	 */
	static final List<String> FORBIDDEN_WORDS = List.of(
			// 능력 단정·점수화
			"실력", "늘었", "향상", "점수", "순위",
			// 과장
			"완벽", "최고", "천재",
			// 단정적 예측
			"틀림없", "될 거예요", "될 거에요");
	/** 다음 행동에는 재촉하는 말도 막는다(기록 해석의 금지어에 더해). */
	static final List<String> URGENCY_WORDS = List.of("꼭", "반드시", "해야", "빨리");
	/** 아무 기록에나 맞는 뻔한 제안 표현. 프롬프트가 막지만 서버도 한 번 더 거른다. */
	static final List<String> GENERIC_PHRASES =
			List.of("다시 연습", "복습", "꾸준히", "자신 있는 곳", "한 번 더 연습", "한 번 더 해");
	private static final int NEXT_ACTION_REASON_MAX = 40;
	private static final String PROMPT_RESOURCE_NAME = "record-analysis-system.txt";
	/** prompts/record-analysis-system.txt의 내용을 바꿀 때마다 올린다. 저장된 결과가 어떤 지시문으로 만들어졌는지 추적하기 위해서다. */
	static final int PROMPT_VERSION = 8;

	private final ActivityRecordRepository activityRecordRepository;
	private final HobbyRepository hobbyRepository;
	private final AiCallService aiCallService;
	private final AiResponseValidator aiResponseValidator;
	private final AiLogRepository aiLogRepository;
	private final ObjectMapper objectMapper;
	private final TransactionTemplate transactionTemplate;

	/** 패키지 안 테스트에서만 다른 리소스 이름으로 바꿔 끼워 읽기 실패를 재현한다. */
	SystemPromptLoader promptLoader = new SystemPromptLoader(PROMPT_RESOURCE_NAME);

	/**
	 * true면 구체성 검사(입력 낱말·일반 표현)에 걸린 제안을 버린다. 기본은 완화(false)라서 로그(NEXT_ACTION_SOFT)만
	 * 남기고 통과시킨다. 환경변수 AI_NEXT_ACTION_STRICT=true로 켠다.
	 */
	boolean strictSpecificity = "true".equalsIgnoreCase(System.getenv("AI_NEXT_ACTION_STRICT"));

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
			analysis = validate(result.content(), input.allowedHistoryIds(), result.model(), input.recordId(),
						input.corpus());
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
				// 직접 추가한 감정의 이름은 길이가 8자로 제한된 "자료"로만 넘긴다(지시로 읽히지 않게 프롬프트의 사용자 글 규칙을 따른다)
				feelingText(record),
				record.getMinutes(),
				record.getId(),
				feelingDirectionOf(record));
		List<AnalysisHistoryItem> historyItems = history.stream()
				.map(h -> new AnalysisHistoryItem(h.getId(), h.getRecordDate().toString(),
						truncate(h.getContent(), HISTORY_CONTENT_MAX),
						truncate(h.getDifficulty(), HISTORY_DIFFICULTY_MAX)))
				.toList();
		List<ActivityRecord> previousOfUser = activityRecordRepository.findPreviousOfUser(userId,
				record.getRecordDate(), record.getId(), PageRequest.of(0, 1));
		AnalysisContext context = previousOfUser.isEmpty()
				? new AnalysisContext(true, null)
				: new AnalysisContext(false, (int) java.time.temporal.ChronoUnit.DAYS
						.between(previousOfUser.get(0).getRecordDate(), record.getRecordDate()));
		AnalysisPayload payload = new AnalysisPayload(
				new AnalysisHobbyInput(hobby.getName(), hobby.getGoal()), recordInput, historyItems, context);

		Set<Long> allowedIds = history.stream().map(ActivityRecord::getId)
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		return new AnalysisInput(payload, allowedIds, record.getId(), corpusOf(hobby, record, history));
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

	/** 접근 제한자를 package-private으로 완화함(E1 AI 품질 평가 하네스가 같은 패키지의 테스트에서 재사용). */
	StoredAnalysis validate(String rawJson, Set<Long> allowedHistoryIds, String model) {
		return validate(rawJson, allowedHistoryIds, model, null, null);
	}

	/** corpus가 null이면 낱말 포함 검사는 건너뛴다(품질 평가 하네스처럼 입력 전문이 없는 호출). */
	StoredAnalysis validate(String rawJson, Set<Long> allowedHistoryIds, String model, Long recordId,
			String corpus) {
		JsonNode node = aiResponseValidator.parse(rawJson);
		aiResponseValidator.requireFields(node, "title", "tags", "relatedRecordIds");

		String title = sanitizeTitle(node.path("title"));
		List<String> tags = sanitizeTags(node.path("tags"));
		List<Long> relatedRecordIds = sanitizeRelatedIds(node.path("relatedRecordIds"), allowedHistoryIds);
		// 비교할 이전 기록이 없을 때는 이 기록 자신이 근거가 될 수 있다(그래서 이 기록의 id도 허용한다).
		Set<Long> nextActionEvidenceIds = new LinkedHashSet<>(allowedHistoryIds);
		if (recordId != null) {
			nextActionEvidenceIds.add(recordId);
		}
		StoredChange change = sanitizeChange(node.path("change"), nextActionEvidenceIds, recordId);

		// nextAction이 이상해도 나머지 결과는 살린다(전체 실패 금지).
		StoredNextAction nextAction = sanitizeNextAction(node.path("nextAction"), nextActionEvidenceIds, corpus,
				recordId);

		return new StoredAnalysis(title, tags, relatedRecordIds, change, null, null, PROMPT_VERSION, model,
				nextAction);
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

	private StoredChange sanitizeChange(JsonNode changeNode, Set<Long> allowedIds, Long recordId) {
		if (changeNode.isMissingNode() || changeNode.isNull() || !changeNode.isObject()) {
			return null;
		}
		// 근거 id가 틀리면 어느 문장도 화면에 낼 수 없으므로 통째로 버린다.
		JsonNode evidenceNode = changeNode.path("evidenceRecordId");
		if (!evidenceNode.canConvertToLong() || !allowedIds.contains(evidenceNode.asLong())) {
			log.warn("CHANGE_DROPPED recordId={} field=change|support reason=EVIDENCE length=- raw={}", recordId,
					rawOf(changeNode));
			return null;
		}
		long evidenceId = evidenceNode.asLong();
		// 한쪽만 실패하면 그 필드만 버리고 나머지는 살린다.
		String text = sanitizeChangeField(changeNode.path("text"), "change", CHANGE_TEXT_MAX, recordId);
		String support = sanitizeChangeField(changeNode.path("support"), "support", SUPPORT_TEXT_MAX, recordId);
		JsonNode supportNode = changeNode.path("support");
		if (supportNode.isMissingNode() || supportNode.isNull()) {
			log.warn("SUPPORT_MISSING recordId={}", recordId);
		}
		if (text == null && support == null) {
			return null;
		}
		return new StoredChange(text, evidenceId, support);
	}

	/** 형식·길이·금지어를 검사해 통과하면 다듬은 글을, 아니면 null을 준다(버릴 때마다 필드와 이유를 로그로 남긴다). */
	private String sanitizeChangeField(JsonNode node, String field, int maxLength, Long recordId) {
		if (node.isMissingNode() || node.isNull()) {
			return null;
		}
		if (!node.isTextual()) {
			droppedField(recordId, field, "FORMAT", node, -1);
			return null;
		}
		String value = node.asText().trim();
		if (value.isEmpty()) {
			droppedField(recordId, field, "EMPTY", node, 0);
			return null;
		}
		if (value.length() > maxLength) {
			droppedField(recordId, field, "LENGTH", node, value.length());
			return null;
		}
		if (containsForbiddenWord(value)) {
			droppedField(recordId, field, "FORBIDDEN", node, value.length());
			return null;
		}
		return value;
	}

	/** raw는 앞 150자만 남긴다(사용자 글 전체를 로그에 남기지 않기 위해). */
	private void droppedField(Long recordId, String field, String reason, JsonNode node, int length) {
		String raw = node.toString();
		log.warn("CHANGE_DROPPED recordId={} field={} reason={} length={} raw={}", recordId, field, reason, length,
				raw.length() > 150 ? raw.substring(0, 150) : raw);
	}

	/**
	 * 하드 검사(형식·길이·분 범위·금지어·재촉어·남의 기록 id)에 걸리면 nextAction만 버리고, 근거 id가 없거나
	 * 입력에 없으면 "이 기록"으로 대체한다. 구체성 검사는 기본적으로 로그만 남기고 통과시킨다. 버릴 때마다
	 * NEXT_ACTION_DROPPED 로그로 이유를 남겨 어디서 막히는지 운영에서 볼 수 있게 한다.
	 */
	private StoredNextAction sanitizeNextAction(JsonNode actionNode, Set<Long> allowedIds, String corpus,
			Long recordId) {
		if (actionNode.isMissingNode() || actionNode.isNull()) {
			return dropped(recordId, "MISSING", actionNode);
		}
		if (!actionNode.isObject()) {
			return dropped(recordId, "PARSE", actionNode);
		}
		JsonNode textNode = actionNode.path("text");
		JsonNode minutesNode = actionNode.path("minutes");
		JsonNode reasonNode = actionNode.path("reason");
		if (!textNode.isTextual() || !minutesNode.isIntegralNumber() || !minutesNode.canConvertToInt()
				|| !reasonNode.isTextual()) {
			return dropped(recordId, "FIELD", actionNode);
		}
		String text = textNode.asText().trim();
		String reason = reasonNode.asText().trim();
		int minutes = minutesNode.asInt();
		if (minutes < NEXT_ACTION_MIN_MINUTES || minutes > NEXT_ACTION_MAX_MINUTES) {
			return dropped(recordId, "RANGE", actionNode);
		}
		if (text.isEmpty() || text.length() > NEXT_ACTION_TEXT_MAX) {
			return dropped(recordId, "LENGTH", actionNode, "text=" + text.length() + "/" + NEXT_ACTION_TEXT_MAX);
		}
		if (reason.isEmpty() || reason.length() > NEXT_ACTION_REASON_MAX) {
			return dropped(recordId, "LENGTH", actionNode, "reason=" + reason.length() + "/" + NEXT_ACTION_REASON_MAX);
		}
		if (isForbiddenWording(text) || isForbiddenWording(reason)) {
			return dropped(recordId, "FORBIDDEN", actionNode);
		}

		JsonNode evidenceNode = actionNode.path("evidenceRecordId");
		Long evidenceId = evidenceNode.isIntegralNumber() && evidenceNode.canConvertToLong() ? evidenceNode.asLong()
				: null;
		if (evidenceId != null && !allowedIds.contains(evidenceId)) {
			if (isOtherUsersRecord(evidenceId, recordId)) {
				return dropped(recordId, "EVIDENCE", actionNode);
			}
			evidenceId = null;
		}
		if (evidenceId == null) {
			if (recordId == null) {
				return dropped(recordId, "EVIDENCE", actionNode);
			}
			log.info("NEXT_ACTION_EVIDENCE_SUBSTITUTED recordId={}", recordId);
			evidenceId = recordId;
		}

		if (corpus != null) {
			String soft = GENERIC_PHRASES.stream().anyMatch(text::contains) ? "GENERIC"
					: !InputWords.mentions(text, corpus) ? "NO_INPUT_WORD" : null;
			if (soft != null) {
				if (strictSpecificity) {
					return dropped(recordId, soft, actionNode);
				}
				log.warn("NEXT_ACTION_SOFT recordId={} reason={} raw={}", recordId, soft, rawOf(actionNode));
			}
		}
		return new StoredNextAction(text, minutes, null, reason, evidenceId);
	}

	private StoredNextAction dropped(Long recordId, String reason, JsonNode actionNode) {
		return dropped(recordId, reason, actionNode, "-");
	}

	/** detail은 어느 필드가 몇 자였는지 같은 보충(없으면 "-"). */
	private StoredNextAction dropped(Long recordId, String reason, JsonNode actionNode, String detail) {
		log.warn("NEXT_ACTION_DROPPED recordId={} reason={} detail={} raw={}", recordId, reason, detail,
				rawOf(actionNode));
		return null;
	}

	private String rawOf(JsonNode node) {
		String raw = node.isMissingNode() ? "" : node.toString();
		return raw.length() > RAW_LOG_MAX ? raw.substring(0, RAW_LOG_MAX) : raw;
	}

	/** 입력에 없는 id가 실제로 다른 사용자의 기록이면 true(그 경우는 대체하지 않고 버린다). */
	private boolean isOtherUsersRecord(Long evidenceId, Long recordId) {
		if (recordId == null) {
			return false;
		}
		Long ownerId = activityRecordRepository.findById(recordId).map(ActivityRecord::getUserId).orElse(null);
		return activityRecordRepository.findById(evidenceId)
				.map(other -> ownerId == null || !other.getUserId().equals(ownerId)).orElse(false);
	}

	private boolean isForbiddenWording(String text) {
		return containsForbiddenWord(text) || URGENCY_WORDS.stream().anyMatch(text::contains);
	}

	private String corpusOf(Hobby hobby, ActivityRecord record, List<ActivityRecord> history) {
		StringBuilder corpus = new StringBuilder();
		corpus.append(hobby.getName()).append(' ').append(nullToEmpty(hobby.getGoal())).append(' ')
				.append(record.getContent()).append(' ').append(nullToEmpty(record.getDifficulty()));
		for (ActivityRecord item : history) {
			corpus.append(' ').append(item.getContent()).append(' ').append(nullToEmpty(item.getDifficulty()));
		}
		return corpus.toString();
	}

	private String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private String feelingDirectionOf(ActivityRecord record) {
		if (record.getFeeling() == null) {
			return record.getFeelingDirection();
		}
		return switch (record.getFeeling()) {
			case PROUD, RELIEVED -> "GOOD";
			case NEUTRAL -> "NEUTRAL";
			case STUCK -> "HARD";
		};
	}

	/** AI가 읽기 좋게 기본 감정은 한글 이름으로, 직접 추가한 감정은 이름 그대로 넘긴다(자료로만 쓰인다). */
	private String feelingText(ActivityRecord record) {
		if (record.getFeeling() == null) {
			return record.getFeelingLabel();
		}
		return switch (record.getFeeling()) {
			case PROUD -> "뿌듯함";
			case STUCK -> "답답함";
			case RELIEVED -> "개운함";
			case NEUTRAL -> "잘 모르겠음";
		};
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
