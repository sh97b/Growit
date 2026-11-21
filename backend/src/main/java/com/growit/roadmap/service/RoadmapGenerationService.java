package com.growit.roadmap.service;

import com.fasterxml.jackson.databind.JsonNode;
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
import com.growit.roadmap.dto.response.RoadmapResponse;
import com.growit.roadmap.entity.Roadmap;
import com.growit.roadmap.entity.RoadmapItem;
import com.growit.roadmap.entity.RoadmapItemOrigin;
import com.growit.roadmap.entity.RoadmapState;
import com.growit.roadmap.entity.RoadmapStep;
import com.growit.roadmap.repository.RoadmapItemRepository;
import com.growit.roadmap.repository.RoadmapRepository;
import com.growit.roadmap.repository.RoadmapStepRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StreamUtils;

/**
 * 분야 목표로 AI 로드맵을 만든다. AI 호출은 트랜잭션 밖에서 하고(커넥션을 오래 붙들지 않으려고), 조회·저장만
 * 짧은 트랜잭션으로 묶는다. 같은 분야의 동시 생성은 메모리 락으로 한 번에 하나만 받는다(서버가 한 대인 전제).
 */
@Service
public class RoadmapGenerationService {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RoadmapGenerationService.class);

	/** prompts/roadmap-system.txt를 바꿀 때마다 올린다. */
	static final int PROMPT_VERSION = 1;
	/** 형식·검증 실패. 다시 누르면 대개 된다. */
	static final String FAIL_MESSAGE = "로드맵을 만들지 못했어요. 한 번 더 눌러 주세요.";
	/** 하루 한도(분야당·사용자·전체)에 도달. */
	static final String LIMIT_MESSAGE = "오늘은 로드맵을 더 만들 수 없어요. 내일 다시 시도해 주세요.";
	static final int DAILY_LIMIT_PER_HOBBY = 3;

	private static final String PROMPT_RESOURCE_NAME = "roadmap-system.txt";
	private static final int RECENT_RECORDS = 8;
	private static final int RECORD_CONTENT_MAX = 80;
	private static final int MIN_STEPS = 3;
	private static final int MAX_STEPS = 5;
	private static final int MIN_ITEMS = 2;
	private static final int MAX_ITEMS = 4;
	private static final int TITLE_MAX = 40;
	private static final int ITEM_MAX = 60;
	private static final int RAW_LOG_MAX = 150;
	private static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Seoul");
	private static final Pattern PERIOD_HINT = Pattern.compile("^약 \\d{1,2}(~\\d{1,2})?주$");
	private static final List<String> FORBIDDEN_WORDS = List.of(
			"실력", "늘었", "향상", "점수", "순위", "완벽", "최고", "천재", "틀림없", "될 거예요",
			"꼭", "반드시", "해야", "빨리", "매일", "연속", "오늘 안", "내일까지",
			"치료", "처방", "진단", "복용", "약물", "영양제", "식단", "칼로리");
	private static final List<Pattern> DATE_PATTERNS = List.of(
			Pattern.compile("\\d{4}[-./]\\d{1,2}[-./]\\d{1,2}"),
			Pattern.compile("\\d{1,2}월\\s*\\d{1,2}일"),
			Pattern.compile("[월화수목금토일]요일"),
			Pattern.compile("D-\\d+"),
			Pattern.compile("\\d+일\\s*(차|째)"));

	private final HobbyRepository hobbyRepository;
	private final ActivityRecordRepository activityRecordRepository;
	private final RoadmapRepository roadmapRepository;
	private final RoadmapStepRepository stepRepository;
	private final RoadmapItemRepository itemRepository;
	private final AiCallService aiCallService;
	private final AiResponseValidator aiResponseValidator;
	private final RoadmapService roadmapService;
	private final Clock clock;
	private final TransactionTemplate transactionTemplate;
	private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

	public RoadmapGenerationService(HobbyRepository hobbyRepository, ActivityRecordRepository activityRecordRepository,
			RoadmapRepository roadmapRepository, RoadmapStepRepository stepRepository,
			RoadmapItemRepository itemRepository, AiCallService aiCallService, AiResponseValidator aiResponseValidator,
			RoadmapService roadmapService, Clock clock, PlatformTransactionManager transactionManager) {
		this.hobbyRepository = hobbyRepository;
		this.activityRecordRepository = activityRecordRepository;
		this.roadmapRepository = roadmapRepository;
		this.stepRepository = stepRepository;
		this.itemRepository = itemRepository;
		this.aiCallService = aiCallService;
		this.aiResponseValidator = aiResponseValidator;
		this.roadmapService = roadmapService;
		this.clock = clock;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public RoadmapResponse create(Long userId, Long hobbyId, LocalDate targetDate) {
		ReentrantLock lock = locks.computeIfAbsent(hobbyId, key -> new ReentrantLock());
		if (!lock.tryLock()) {
			throw new ApiException(ErrorCode.ROADMAP_IN_PROGRESS, "이 분야의 로드맵을 만들고 있어요. 잠시만 기다려 주세요.");
		}
		try {
			return createLocked(userId, hobbyId, targetDate);
		} finally {
			lock.unlock();
		}
	}

	private RoadmapResponse createLocked(Long userId, Long hobbyId, LocalDate targetDate) {
		Prepared prepared = transactionTemplate.execute(status -> prepare(userId, hobbyId, targetDate));
		String systemPrompt = loadPrompt();

		List<ParsedStep> steps = generate(userId, systemPrompt, prepared.payload());

		return transactionTemplate.execute(status -> save(userId, prepared, steps));
	}

	/** 한 번 실패하면 어떤 규칙을 어겼는지 시스템 지시에 덧붙여 한 번만 다시 부른다. */
	private List<ParsedStep> generate(Long userId, String systemPrompt, RoadmapPayload payload) {
		String prompt = systemPrompt;
		for (int attempt = 0; attempt < 2; attempt++) {
			AiCallResult result;
			try {
				// 두 번째 호출은 교정 재호출이라 하루 한도에 세지 않는다.
				result = aiCallService.call(userId, AiResultType.ROADMAP, prompt, payload, attempt > 0);
			} catch (AiLimitException e) {
				log.warn("ROADMAP_FAILED reason=LIMIT limit={}",
						e.isGlobal() ? "AI_GLOBAL_DAILY_LIMIT" : "AI_ROADMAP_USER_DAILY_LIMIT");
				throw new ApiException(ErrorCode.AI_LIMIT_EXCEEDED, LIMIT_MESSAGE);
			} catch (ApiException e) {
				// 동시 호출 제한·공급자 429(AI_BUSY)와 호출 실패(AI_FAILED, 시간 초과 등)는 그 문구 그대로 보낸다.
				log.warn("ROADMAP_FAILED reason={} attempt={}",
						e.getCode() == ErrorCode.AI_BUSY ? "BUSY" : "CALL", attempt);
				throw e;
			}
			try {
				return validate(result.content());
			} catch (InvalidRoadmap e) {
				log.warn("ROADMAP_DROPPED reason={} attempt={} raw={}", e.reason, attempt, rawOf(result.content()));
				prompt = systemPrompt + "\n\n[교정 지시] 직전 응답이 규칙을 어겼어요: " + e.getMessage()
						+ ". 같은 입력으로 규칙을 지켜 JSON 객체 하나만 다시 응답해요.";
			}
		}
		log.warn("ROADMAP_FAILED reason=VALIDATION");
		throw new ApiException(ErrorCode.VALIDATION_ERROR, FAIL_MESSAGE);
	}

	private Prepared prepare(Long userId, Long hobbyId, LocalDate targetDate) {
		Hobby hobby = hobbyRepository.findByIdAndUserId(hobbyId, userId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "활동 분야를 찾을 수 없어요"));
		String goal = hobby.getGoal() == null ? "" : hobby.getGoal().trim();
		if (goal.isEmpty()) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "목표를 먼저 적어 주세요");
		}
		if (targetDate != null && targetDate.isBefore(LocalDate.now(clock.withZone(SERVER_ZONE)))) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "목표일은 오늘 이후 날짜로 정해 주세요");
		}
		if (roadmapRepository.findFirstByHobbyIdAndUserIdAndState(hobbyId, userId, RoadmapState.ACTIVE).isPresent()) {
			throw new ApiException(ErrorCode.ROADMAP_ACTIVE, "이미 진행 중인 로드맵이 있어요");
		}
		long today = roadmapRepository.countByHobbyIdAndUserIdAndCreatedAtGreaterThanEqual(hobbyId, userId,
				LocalDate.now(clock.withZone(SERVER_ZONE)).atStartOfDay());
		if (today >= DAILY_LIMIT_PER_HOBBY) {
			log.warn("ROADMAP_FAILED reason=LIMIT limit=HOBBY_DAILY_LIMIT({})", DAILY_LIMIT_PER_HOBBY);
			throw new ApiException(ErrorCode.AI_LIMIT_EXCEEDED, LIMIT_MESSAGE);
		}

		List<RecentRecord> recent = activityRecordRepository
				.findByUserIdAndHobbyIdOrderByRecordDateDescIdDesc(userId, hobbyId, PageRequest.of(0, RECENT_RECORDS))
				.stream().map(this::toRecent).toList();
		long total = activityRecordRepository.countByUserIdAndHobbyId(userId, hobbyId);
		RoadmapPayload payload = new RoadmapPayload(new RoadmapHobby(hobby.getName()), goal,
				targetDate == null ? null : targetDate.toString(), total, recent);
		return new Prepared(hobbyId, goal, targetDate, payload);
	}

	private RecentRecord toRecent(ActivityRecord record) {
		String content = record.getContent();
		if (content != null && content.length() > RECORD_CONTENT_MAX) {
			content = content.substring(0, RECORD_CONTENT_MAX);
		}
		return new RecentRecord(record.getRecordDate().toString(), content, record.getDifficulty());
	}

	private RoadmapResponse save(Long userId, Prepared prepared, List<ParsedStep> steps) {
		// AI를 기다리는 사이 다른 경로로 ACTIVE가 생겼을 수 있어 저장 직전에 한 번 더 확인한다.
		if (roadmapRepository.findFirstByHobbyIdAndUserIdAndState(prepared.hobbyId(), userId, RoadmapState.ACTIVE)
				.isPresent()) {
			throw new ApiException(ErrorCode.ROADMAP_ACTIVE, "이미 진행 중인 로드맵이 있어요");
		}
		Roadmap roadmap = roadmapRepository.save(
				new Roadmap(userId, prepared.hobbyId(), prepared.goal(), prepared.targetDate(), PROMPT_VERSION));
		for (int i = 0; i < steps.size(); i++) {
			ParsedStep parsed = steps.get(i);
			RoadmapStep step = stepRepository
					.save(new RoadmapStep(roadmap.getId(), i + 1, parsed.title(), parsed.periodHint()));
			for (int j = 0; j < parsed.items().size(); j++) {
				itemRepository.save(new RoadmapItem(step.getId(), j + 1, parsed.items().get(j), RoadmapItemOrigin.AI));
			}
		}
		return roadmapService.toResponse(roadmap);
	}

	/** 형식·개수·길이·금지어·중복·기간 힌트를 확인한다. 하나라도 어기면 InvalidRoadmap을 던진다. */
	List<ParsedStep> validate(String rawJson) {
		JsonNode node;
		try {
			node = aiResponseValidator.parse(rawJson);
		} catch (ApiException e) {
			throw new InvalidRoadmap("PARSE", "JSON 형식이 아님");
		}
		JsonNode stepsNode = node.path("steps");
		if (!stepsNode.isArray()) {
			throw new InvalidRoadmap("FORMAT", "steps 배열이 없음");
		}
		if (stepsNode.size() < MIN_STEPS || stepsNode.size() > MAX_STEPS) {
			throw new InvalidRoadmap("COUNT", "단계는 " + MIN_STEPS + "~" + MAX_STEPS + "개여야 함");
		}
		List<ParsedStep> result = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (JsonNode stepNode : stepsNode) {
			String title = text(stepNode.path("title"), 1, TITLE_MAX, "단계 title");
			checkWording(title);
			String periodHint = null;
			JsonNode hintNode = stepNode.path("periodHint");
			if (!hintNode.isMissingNode() && !hintNode.isNull()) {
				periodHint = hintNode.isTextual() ? hintNode.asText().trim() : "";
				if (!PERIOD_HINT.matcher(periodHint).matches()) {
					throw new InvalidRoadmap("PERIOD_HINT", "periodHint는 \"약 N주\" 또는 \"약 N~M주\"여야 함");
				}
			}
			JsonNode itemsNode = stepNode.path("items");
			if (!itemsNode.isArray() || itemsNode.size() < MIN_ITEMS || itemsNode.size() > MAX_ITEMS) {
				throw new InvalidRoadmap("COUNT", "단계마다 항목은 " + MIN_ITEMS + "~" + MAX_ITEMS + "개여야 함");
			}
			List<String> items = new ArrayList<>();
			for (JsonNode itemNode : itemsNode) {
				String item = text(itemNode, 1, ITEM_MAX, "항목");
				checkWording(item);
				if (!seen.add(item.replaceAll("\\s+", "").toLowerCase())) {
					throw new InvalidRoadmap("DUPLICATE", "같은 항목이 여러 번 나옴");
				}
				items.add(item);
			}
			result.add(new ParsedStep(title, periodHint, items));
		}
		return result;
	}

	private String text(JsonNode node, int min, int max, String label) {
		if (!node.isTextual()) {
			throw new InvalidRoadmap("FORMAT", label + "가 문자열이 아님");
		}
		String value = node.asText().trim();
		if (value.length() < min || value.length() > max) {
			throw new InvalidRoadmap("LENGTH", label + "는 " + min + "~" + max + "자여야 함");
		}
		return value;
	}

	private void checkWording(String value) {
		for (String word : FORBIDDEN_WORDS) {
			if (value.contains(word)) {
				throw new InvalidRoadmap("FORBIDDEN", "금지 표현(" + word + ")을 씀");
			}
		}
		for (Pattern pattern : DATE_PATTERNS) {
			if (pattern.matcher(value).find()) {
				throw new InvalidRoadmap("FORBIDDEN", "날짜·요일 표현을 씀");
			}
		}
	}

	private String loadPrompt() {
		try (InputStream in = new ClassPathResource(PROMPT_RESOURCE_NAME).getInputStream()) {
			return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
		} catch (IOException e) {
			log.warn("ROADMAP_FAILED reason=PROMPT_FILE");
			throw new ApiException(ErrorCode.VALIDATION_ERROR, FAIL_MESSAGE);
		}
	}

	private static String rawOf(String raw) {
		if (raw == null) {
			return "";
		}
		return raw.length() > RAW_LOG_MAX ? raw.substring(0, RAW_LOG_MAX) : raw;
	}

	record ParsedStep(String title, String periodHint, List<String> items) {
	}

	private record Prepared(Long hobbyId, String goal, LocalDate targetDate, RoadmapPayload payload) {
	}

	record RoadmapPayload(RoadmapHobby hobby, String goal, String targetDate, long totalRecordCount,
			List<RecentRecord> recentRecords) {
	}

	record RoadmapHobby(String name) {
	}

	record RecentRecord(String date, String content, String difficulty) {
	}

	private static final class InvalidRoadmap extends RuntimeException {
		private final String reason;

		InvalidRoadmap(String reason, String message) {
			super(message);
			this.reason = reason;
		}
	}
}
