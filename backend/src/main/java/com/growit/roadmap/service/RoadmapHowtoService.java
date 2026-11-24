package com.growit.roadmap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.growit.ai.entity.AiResultType;
import com.growit.ai.service.AiCallResult;
import com.growit.ai.service.AiCallService;
import com.growit.ai.service.AiLimitException;
import com.growit.ai.service.AiResponseValidator;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.record.entity.ActivityRecord;
import com.growit.record.repository.ActivityRecordRepository;
import com.growit.roadmap.dto.response.AiHowtoResponse;
import com.growit.roadmap.entity.BlockKind;
import com.growit.roadmap.entity.Roadmap;
import com.growit.roadmap.entity.RoadmapItem;
import com.growit.roadmap.entity.RoadmapItemBlock;
import com.growit.roadmap.entity.RoadmapStep;
import com.growit.roadmap.repository.RoadmapItemBlockRepository;
import com.growit.roadmap.repository.RoadmapRepository;
import com.growit.roadmap.repository.RoadmapStepRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StreamUtils;

/**
 * 항목의 "해 보는 방법" 블록을 AI로 만든다. 버튼을 누를 때만 호출하고 결과는 블록으로 저장한다(항목당 하나, 다시 만들면 덮어쓴다).
 * AI 호출은 트랜잭션 밖에서 하고, 조회·저장만 짧은 트랜잭션으로 묶는다. 로드맵 AI 하루 한도(AI_ROADMAP_USER_DAILY_LIMIT)를 함께 쓰고,
 * 다시 만들기는 블록마다 하루 3회까지다. 검증에 실패한 항목은 어떤 규칙이든 로그에 이유만 남기고(사용자 글은 남기지 않는다)
 * 한 번 다시 부른 뒤에도 실패하면 400으로 안내한다. 이 서비스는 URL을 만들지 않는다: AI가 URL 모양을 내면 그 응답을 버린다.
 */
@Service
public class RoadmapHowtoService {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RoadmapHowtoService.class);

	/** prompts/roadmap-howto-system.txt를 바꿀 때마다 올린다. */
	static final int PROMPT_VERSION = 1;
	static final String FAIL_MESSAGE = "지금은 방법을 만들지 못했어요. 잠시 후 다시 시도해 주세요.";
	static final String REGEN_LIMIT_MESSAGE = "오늘 다시 만들 수 있는 횟수(3회)를 다 썼어요. 내일 다시 눌러 주세요.";
	static final int DAILY_REGENERATION_LIMIT = 3;

	private static final String PROMPT_RESOURCE_NAME = "roadmap-howto-system.txt";
	private static final int RECENT_RECORDS = 3;
	private static final int RECORD_CONTENT_MAX = 80;
	private static final int STEP_MIN = 3;
	private static final int STEP_MAX = 5;
	private static final int STEP_TEXT_MAX = 40;
	private static final int CHECK_MAX_COUNT = 3;
	private static final int MISTAKE_MAX_COUNT = 3;
	private static final int OTHER_TEXT_MAX = 60;
	private static final int QUERY_MAX = 20;
	private static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Seoul");
	private static final List<String> FORBIDDEN_WORDS = List.of(
			"실력", "늘었", "향상", "점수", "순위", "완벽", "최고", "천재", "틀림없", "반드시", "연속", "오늘 안",
			"내일까지", "치료", "처방", "진단", "복용", "약물", "영양제", "식단", "칼로리");
	/** URL·도메인 모양(http, www, 흔한 최상위 도메인). 방법 글에는 링크가 없어야 한다. */
	private static final Pattern URL_LIKE = Pattern.compile(
			"(?i)(https?:|www\\.|[a-z0-9-]+\\.(com|net|org|kr|io|co|tv|me|app)\\b)");

	private final RoadmapRepository roadmapRepository;
	private final RoadmapStepRepository stepRepository;
	private final RoadmapItemBlockRepository blockRepository;
	private final HobbyRepository hobbyRepository;
	private final ActivityRecordRepository activityRecordRepository;
	private final RoadmapBlockService blockService;
	private final AiCallService aiCallService;
	private final AiResponseValidator aiResponseValidator;
	private final ObjectMapper objectMapper;
	private final Clock clock;
	private final TransactionTemplate transactionTemplate;

	public RoadmapHowtoService(RoadmapRepository roadmapRepository, RoadmapStepRepository stepRepository,
			RoadmapItemBlockRepository blockRepository, HobbyRepository hobbyRepository,
			ActivityRecordRepository activityRecordRepository, RoadmapBlockService blockService,
			AiCallService aiCallService, AiResponseValidator aiResponseValidator, ObjectMapper objectMapper,
			Clock clock, PlatformTransactionManager transactionManager) {
		this.roadmapRepository = roadmapRepository;
		this.stepRepository = stepRepository;
		this.blockRepository = blockRepository;
		this.hobbyRepository = hobbyRepository;
		this.activityRecordRepository = activityRecordRepository;
		this.blockService = blockService;
		this.aiCallService = aiCallService;
		this.aiResponseValidator = aiResponseValidator;
		this.objectMapper = objectMapper;
		this.clock = clock;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public AiHowtoResponse generate(Long userId, Long itemId) {
		Prepared prepared = transactionTemplate.execute(status -> prepare(userId, itemId));
		String systemPrompt = loadPrompt();
		ParsedHowto howto = callAndValidate(userId, systemPrompt, prepared.payload());
		return transactionTemplate.execute(status -> save(userId, prepared, howto));
	}

	private Prepared prepare(Long userId, Long itemId) {
		RoadmapItem item = blockService.ownedItem(userId, itemId, true);
		RoadmapStep step = stepRepository.findById(item.getStepId()).orElseThrow();
		Roadmap roadmap = roadmapRepository.findById(step.getRoadmapId()).orElseThrow();
		Hobby hobby = hobbyRepository.findByIdAndUserId(roadmap.getHobbyId(), userId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "항목을 찾을 수 없어요"));

		LocalDate today = LocalDate.now(clock.withZone(SERVER_ZONE));
		RoadmapItemBlock existing = blockRepository.findFirstByItemIdAndKind(itemId, BlockKind.AI_HOWTO).orElse(null);
		int regenCount = 0;
		if (existing != null) {
			JsonNode old = readTree(existing.getPayload());
			if (today.toString().equals(old.path("regenDate").asText(""))) {
				regenCount = old.path("regenCount").asInt(0);
			}
			if (regenCount >= DAILY_REGENERATION_LIMIT) {
				throw new ApiException(ErrorCode.AI_LIMIT_EXCEEDED, REGEN_LIMIT_MESSAGE);
			}
		}

		List<RecentRecord> recent = activityRecordRepository
				.findByUserIdAndHobbyIdOrderByRecordDateDescIdDesc(userId, hobby.getId(),
						PageRequest.of(0, RECENT_RECORDS))
				.stream().map(this::toRecent).toList();
		HowtoPayload payload = new HowtoPayload(hobby.getName(), roadmap.getGoalText(), step.getTitle(),
				item.getContent(), recent);
		return new Prepared(payload, itemId, existing == null ? null : existing.getId(), regenCount, today);
	}

	/** 한 번 실패하면 같은 입력으로 교정 지시를 덧붙여 한 번만 다시 부른다(두 번째는 하루 한도에 세지 않는다). */
	private ParsedHowto callAndValidate(Long userId, String systemPrompt, HowtoPayload payload) {
		String prompt = systemPrompt;
		for (int attempt = 0; attempt < 2; attempt++) {
			AiCallResult result;
			try {
				result = aiCallService.call(userId, AiResultType.ROADMAP, prompt, payload, attempt > 0);
			} catch (AiLimitException e) {
				log.warn("ROADMAP_HOWTO_FAILED reason=LIMIT limit={}",
						e.isGlobal() ? "AI_GLOBAL_DAILY_LIMIT" : "AI_ROADMAP_USER_DAILY_LIMIT");
				throw new ApiException(ErrorCode.AI_LIMIT_EXCEEDED, "오늘은 방법을 더 만들 수 없어요. 내일 다시 시도해 주세요.");
			} catch (ApiException e) {
				log.warn("ROADMAP_HOWTO_FAILED reason={} attempt={}",
						e.getCode() == ErrorCode.AI_BUSY ? "BUSY" : "CALL", attempt);
				throw e;
			}
			try {
				return validate(result.content());
			} catch (InvalidHowto e) {
				log.warn("ROADMAP_HOWTO_DROPPED reason={} attempt={}", e.reason, attempt);
				prompt = systemPrompt + "\n\n[교정 지시] 직전 응답이 규칙을 어겼어요: " + e.getMessage()
						+ ". 같은 입력으로 규칙을 지켜 JSON 객체 하나만 다시 응답해요.";
			}
		}
		log.warn("ROADMAP_HOWTO_FAILED reason=VALIDATION");
		throw new ApiException(ErrorCode.VALIDATION_ERROR, FAIL_MESSAGE);
	}

	private AiHowtoResponse save(Long userId, Prepared prepared, ParsedHowto howto) {
		// AI를 기다리는 사이 항목이 지워지거나 보관됐을 수 있어 소유자·진행 상태를 다시 확인한다.
		RoadmapItem item = blockService.ownedItem(userId, prepared.itemId(), true);
		ObjectNode node = objectMapper.createObjectNode();
		node.set("steps", toArray(howto.steps()));
		node.set("checks", toArray(howto.checks()));
		node.set("mistakes", toArray(howto.mistakes()));
		node.put("query", howto.query());
		node.put("promptVersion", PROMPT_VERSION);
		node.put("generatedAt", LocalDateTime.now(clock.withZone(SERVER_ZONE)).toString());
		node.put("regenDate", prepared.today().toString());
		RoadmapItemBlock block;
		if (prepared.existingBlockId() != null) {
			block = blockRepository.findById(prepared.existingBlockId())
					.filter(found -> found.getItemId().equals(item.getId()))
					.orElse(null);
		} else {
			block = null;
		}
		if (block != null) {
			node.put("regenCount", prepared.regenCount() + 1);
			block.setPayload(BlockPayloads.serialize(node, objectMapper));
		} else {
			node.put("regenCount", 0);
			block = blockService.append(item, BlockKind.AI_HOWTO, BlockPayloads.serialize(node, objectMapper));
		}
		List<RoadmapItemBlock> all = blockRepository.findByItemIdOrderBySortOrderAscIdAsc(item.getId());
		int position = 1;
		for (int i = 0; i < all.size(); i++) {
			if (all.get(i).getId().equals(block.getId())) {
				position = i + 1;
			}
		}
		return new AiHowtoResponse(blockService.toResponse(block, position), howto.query());
	}

	// ---- 검증 ----

	/** 형식·개수·길이·금지어·URL 모양을 확인한다. 하나라도 어기면 InvalidHowto를 던진다. */
	ParsedHowto validate(String rawJson) {
		JsonNode node;
		try {
			node = aiResponseValidator.parse(rawJson);
		} catch (ApiException e) {
			throw new InvalidHowto("PARSE", "JSON 형식이 아님");
		}
		List<String> steps = textList(node.path("steps"), STEP_MIN, STEP_MAX, STEP_TEXT_MAX, "steps");
		List<String> checks = textList(node.path("checks"), 1, CHECK_MAX_COUNT, OTHER_TEXT_MAX, "checks");
		List<String> mistakes = node.path("mistakes").isMissingNode() || node.path("mistakes").isNull()
				? List.of()
				: textList(node.path("mistakes"), 0, MISTAKE_MAX_COUNT, OTHER_TEXT_MAX, "mistakes");
		JsonNode queryNode = node.path("query");
		if (!queryNode.isTextual()) {
			throw new InvalidHowto("FORMAT", "query가 문자열이 아님");
		}
		String query = queryNode.asText().trim();
		if (query.isEmpty() || query.length() > QUERY_MAX) {
			throw new InvalidHowto("LENGTH", "query는 1~" + QUERY_MAX + "자여야 함");
		}
		checkWording(query);
		return new ParsedHowto(steps, checks, mistakes, query);
	}

	private List<String> textList(JsonNode arrayNode, int min, int max, int maxLength, String label) {
		if (!arrayNode.isArray()) {
			throw new InvalidHowto("FORMAT", label + " 배열이 없음");
		}
		if (arrayNode.size() < min || arrayNode.size() > max) {
			throw new InvalidHowto("COUNT", label + "는 " + min + "~" + max + "개여야 함");
		}
		List<String> values = new ArrayList<>();
		for (JsonNode element : arrayNode) {
			if (!element.isTextual()) {
				throw new InvalidHowto("FORMAT", label + " 항목이 문자열이 아님");
			}
			String value = element.asText().trim();
			if (value.isEmpty() || value.length() > maxLength) {
				throw new InvalidHowto("LENGTH", label + " 항목은 1~" + maxLength + "자여야 함");
			}
			checkWording(value);
			values.add(value);
		}
		return values;
	}

	private void checkWording(String value) {
		for (String word : FORBIDDEN_WORDS) {
			if (value.contains(word)) {
				throw new InvalidHowto("FORBIDDEN", "금지 표현(" + word + ")을 씀");
			}
		}
		if (URL_LIKE.matcher(value).find()) {
			throw new InvalidHowto("URL", "링크나 사이트 주소 모양을 쓰지 않아요");
		}
	}

	// ---- 보조 ----

	private ArrayNode toArray(List<String> values) {
		ArrayNode array = objectMapper.createArrayNode();
		values.forEach(array::add);
		return array;
	}

	private JsonNode readTree(String json) {
		try {
			return objectMapper.readTree(json);
		} catch (com.fasterxml.jackson.core.JsonProcessingException e) {
			return objectMapper.createObjectNode();
		}
	}

	private RecentRecord toRecent(ActivityRecord record) {
		String content = record.getContent();
		if (content != null && content.length() > RECORD_CONTENT_MAX) {
			content = content.substring(0, RECORD_CONTENT_MAX);
		}
		return new RecentRecord(record.getRecordDate().toString(), content);
	}

	private String loadPrompt() {
		try (InputStream in = new ClassPathResource(PROMPT_RESOURCE_NAME).getInputStream()) {
			return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
		} catch (IOException e) {
			log.warn("ROADMAP_HOWTO_FAILED reason=PROMPT_FILE");
			throw new ApiException(ErrorCode.VALIDATION_ERROR, FAIL_MESSAGE);
		}
	}

	record ParsedHowto(List<String> steps, List<String> checks, List<String> mistakes, String query) {
	}

	/** AI에는 사용자 글을 지시가 아니라 데이터로만 넘긴다. */
	record HowtoPayload(String hobbyName, String roadmapGoal, String stepTitle, String itemText,
			List<RecentRecord> recentRecords) {
	}

	record RecentRecord(String recordDate, String content) {
	}

	private record Prepared(HowtoPayload payload, Long itemId, Long existingBlockId, int regenCount, LocalDate today) {
	}

	private static final class InvalidHowto extends RuntimeException {
		private final String reason;

		InvalidHowto(String reason, String message) {
			super(message);
			this.reason = reason;
		}
	}
}
