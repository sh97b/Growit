package com.growit.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.ai.dto.CompareRecordCard;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.record.entity.ActivityRecord;
import com.growit.record.repository.ActivityRecordRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** WEEKLY·METRICS가 공유하는 캐시 열쇠 계산, 입력 변환, 응답 검증, 근거 조회 로직. */
final class ViewCacheKeys {

	private static final int CONTENT_MAX = 300;
	private static final int DIFFICULTY_MAX = 200;
	private static final int TEXT_MAX = 80;
	private static final int MAX_EVIDENCE = 2;
	private static final List<String> FORBIDDEN_WORDS =
			List.of("실력", "늘었", "향상", "잘했", "훌륭", "대단", "점수", "순위", "달성");

	private ViewCacheKeys() {
	}

	/** 기록이 수정되면 updated_at이 바뀌어 cache_key도 함께 바뀐다. */
	static String recordsFingerprint(List<ActivityRecord> records) {
		return records.stream()
				.sorted((a, b) -> Long.compare(a.getId(), b.getId()))
				.map(r -> r.getId() + ":" + r.getUpdatedAt())
				.collect(Collectors.joining(","));
	}

	static List<ViewPayloadRecord> toPayloadRecords(List<ActivityRecord> records) {
		return records.stream()
				.map(r -> new ViewPayloadRecord(r.getId(), r.getRecordDate().toString(),
						truncate(r.getContent(), CONTENT_MAX), truncate(r.getDifficulty(), DIFFICULTY_MAX)))
				.toList();
	}

	static String truncate(String text, int maxLength) {
		if (text == null) {
			return null;
		}
		return text.length() > maxLength ? text.substring(0, maxLength) : text;
	}

	static StoredViewResult validate(AiResponseValidator validator, String rawJson, List<Long> allowedIds) {
		JsonNode node = validator.parse(rawJson);
		validator.requireFields(node, "text", "evidenceIds");

		JsonNode textNode = node.path("text");
		if (!textNode.isTextual()) {
			throw new ApiException(ErrorCode.AI_FAILED, "지금은 AI를 쓸 수 없어요. 기록은 안전하게 저장돼 있어요.");
		}
		String text = textNode.asText().trim();
		if (text.isEmpty() || text.length() > TEXT_MAX || containsForbiddenWord(text)) {
			throw new ApiException(ErrorCode.AI_FAILED, "지금은 AI를 쓸 수 없어요. 기록은 안전하게 저장돼 있어요.");
		}

		Set<Long> allowed = Set.copyOf(allowedIds);
		List<Long> evidenceIds = new ArrayList<>();
		JsonNode idsNode = node.path("evidenceIds");
		if (idsNode.isArray()) {
			for (JsonNode idNode : idsNode) {
				if (idNode.canConvertToLong() && allowed.contains(idNode.asLong())
						&& evidenceIds.size() < MAX_EVIDENCE) {
					evidenceIds.add(idNode.asLong());
				}
			}
		}

		return new StoredViewResult(text, evidenceIds, null);
	}

	private static boolean containsForbiddenWord(String text) {
		return FORBIDDEN_WORDS.stream().anyMatch(text::contains);
	}

	static List<CompareRecordCard> loadEvidence(ActivityRecordRepository repository, ObjectMapper objectMapper,
			Long userId, List<Long> evidenceIds) {
		List<CompareRecordCard> cards = new ArrayList<>();
		for (Long id : evidenceIds) {
			repository.findByIdAndUserId(id, userId).ifPresent(r -> cards.add(new CompareRecordCard(r.getId(),
					r.getRecordDate(), r.getContent(), extractTags(objectMapper, r.getAiAnalysis()),
					r.getFeeling() == null ? null : r.getFeeling().name())));
		}
		return cards;
	}

	private static List<String> extractTags(ObjectMapper objectMapper, String aiAnalysisJson) {
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
