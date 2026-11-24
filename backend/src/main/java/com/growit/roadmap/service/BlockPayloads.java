package com.growit.roadmap.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.roadmap.entity.BlockKind;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * 블록 종류별 payload 검증과 정리. 알려진 칸만 새 객체에 옮겨 담아 모르는 칸이 저장되지 않게 하고, 직렬화한 크기가
 * 4KB를 넘으면 거절한다. 링크는 http/https만 허용하고(javascript·data 등 거절) 표시용 host를 서버가 계산해 저장한다.
 */
final class BlockPayloads {

	static final int MEMO_MAX = 500;
	static final int LINK_URL_MAX = 500;
	static final int LINK_TITLE_MAX = 60;
	static final int TIMER_MIN_MINUTES = 1;
	static final int TIMER_MAX_MINUTES = 120;
	static final int SEARCH_MAX = 40;
	static final int SUBLIST_MAX_ITEMS = 8;
	static final int SUBLIST_TEXT_MAX = 60;
	static final int PAYLOAD_MAX_BYTES = 4096;
	static final Set<String> LINK_CATEGORIES = Set.of("VIDEO", "SHEET", "MATERIAL", "OTHER");

	private BlockPayloads() {
	}

	/** 사용자가 직접 만드는 종류만 받는다. AI_HOWTO는 버튼(전용 API)으로만 만들고 고칠 수 없다. */
	static String normalize(BlockKind kind, JsonNode payload, ObjectMapper mapper) {
		if (kind == BlockKind.AI_HOWTO) {
			throw invalid("이 블록은 [해 보는 방법 만들기] 버튼으로 만들어요");
		}
		if (payload == null || !payload.isObject()) {
			throw invalid("블록 내용을 확인해 주세요");
		}
		ObjectNode result = switch (kind) {
			case MEMO -> memo(payload, mapper);
			case LINK -> link(payload, mapper);
			case TIMER -> timer(payload, mapper);
			case SEARCH -> search(payload, mapper);
			case SUBLIST -> sublist(payload, mapper);
			default -> throw invalid("지원하지 않는 블록이에요");
		};
		return serialize(result, mapper);
	}

	/** 서버가 만든 payload(AI_HOWTO)도 같은 크기 한도를 지킨다. */
	static String serialize(ObjectNode node, ObjectMapper mapper) {
		String json;
		try {
			json = mapper.writeValueAsString(node);
		} catch (JsonProcessingException e) {
			throw invalid("블록 내용을 확인해 주세요");
		}
		if (json.getBytes(StandardCharsets.UTF_8).length > PAYLOAD_MAX_BYTES) {
			throw invalid("블록 내용이 너무 길어요");
		}
		return json;
	}

	private static ObjectNode memo(JsonNode payload, ObjectMapper mapper) {
		String text = requireText(payload.path("text"), 1, MEMO_MAX, "메모는 1~" + MEMO_MAX + "자로 적어 주세요");
		ObjectNode node = mapper.createObjectNode();
		node.put("text", text);
		return node;
	}

	private static ObjectNode link(JsonNode payload, ObjectMapper mapper) {
		String rawUrl = requireText(payload.path("url"), 1, LINK_URL_MAX, "링크 주소는 1~" + LINK_URL_MAX + "자로 적어 주세요");
		URI uri = parseWebUri(rawUrl);
		String host = uri.getHost().toLowerCase(Locale.ROOT);
		if (host.startsWith("www.")) {
			host = host.substring(4);
		}
		String category = "OTHER";
		JsonNode categoryNode = payload.path("category");
		if (!categoryNode.isMissingNode() && !categoryNode.isNull()) {
			category = categoryNode.isTextual() ? categoryNode.asText().trim().toUpperCase(Locale.ROOT) : "";
			if (!LINK_CATEGORIES.contains(category)) {
				throw invalid("링크 구분은 영상·악보·자료·기타 중에서 골라 주세요");
			}
		}
		ObjectNode node = mapper.createObjectNode();
		JsonNode titleNode = payload.path("title");
		if (titleNode.isTextual() && !titleNode.asText().trim().isEmpty()) {
			String title = titleNode.asText().trim();
			if (title.length() > LINK_TITLE_MAX) {
				throw invalid("링크 제목은 " + LINK_TITLE_MAX + "자 이하로 적어 주세요");
			}
			node.put("title", title);
		} else if (!titleNode.isMissingNode() && !titleNode.isNull() && !titleNode.isTextual()) {
			throw invalid("링크 제목을 확인해 주세요");
		}
		node.put("url", uri.toString());
		node.put("host", host);
		node.put("category", category);
		return node;
	}

	/** http/https 절대 주소만 통과시킨다. javascript:, data:, 스킴 없는 주소, 사용자 정보(user@)가 있는 주소는 거절한다. */
	static URI parseWebUri(String raw) {
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (Character.isWhitespace(c) || Character.isISOControl(c)) {
				throw invalid("올바른 링크 주소가 아니에요");
			}
		}
		URI uri;
		try {
			uri = new URI(raw);
		} catch (URISyntaxException e) {
			throw invalid("올바른 링크 주소가 아니에요");
		}
		String scheme = uri.getScheme();
		if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
			throw invalid("링크는 http 또는 https로 시작해야 해요");
		}
		if (uri.getHost() == null || uri.getHost().isBlank() || uri.getUserInfo() != null) {
			throw invalid("올바른 링크 주소가 아니에요");
		}
		return uri;
	}

	private static ObjectNode timer(JsonNode payload, ObjectMapper mapper) {
		JsonNode minutes = payload.path("minutes");
		if (!minutes.isIntegralNumber() || !minutes.canConvertToInt() || minutes.asInt() < TIMER_MIN_MINUTES
				|| minutes.asInt() > TIMER_MAX_MINUTES) {
			throw invalid("타이머는 " + TIMER_MIN_MINUTES + "~" + TIMER_MAX_MINUTES + "분으로 정해 주세요");
		}
		ObjectNode node = mapper.createObjectNode();
		node.put("minutes", minutes.asInt());
		return node;
	}

	private static ObjectNode search(JsonNode payload, ObjectMapper mapper) {
		String query = requireText(payload.path("query"), 1, SEARCH_MAX, "검색어는 1~" + SEARCH_MAX + "자로 적어 주세요");
		ObjectNode node = mapper.createObjectNode();
		node.put("query", query);
		return node;
	}

	private static ObjectNode sublist(JsonNode payload, ObjectMapper mapper) {
		JsonNode items = payload.path("items");
		if (!items.isArray() || items.isEmpty() || items.size() > SUBLIST_MAX_ITEMS) {
			throw invalid("하위 체크는 1~" + SUBLIST_MAX_ITEMS + "개로 만들어 주세요");
		}
		ArrayNode out = mapper.createArrayNode();
		for (JsonNode item : items) {
			String text = requireText(item.path("text"), 1, SUBLIST_TEXT_MAX,
					"하위 체크는 1~" + SUBLIST_TEXT_MAX + "자로 적어 주세요");
			JsonNode done = item.path("done");
			ObjectNode entry = mapper.createObjectNode();
			entry.put("text", text);
			entry.put("done", done.isBoolean() && done.asBoolean());
			out.add(entry);
		}
		ObjectNode node = mapper.createObjectNode();
		node.set("items", out);
		return node;
	}

	private static String requireText(JsonNode node, int min, int max, String message) {
		if (!node.isTextual()) {
			throw invalid(message);
		}
		String value = node.asText().trim();
		if (value.length() < min || value.length() > max) {
			throw invalid(message);
		}
		return value;
	}

	private static ApiException invalid(String message) {
		return new ApiException(ErrorCode.VALIDATION_ERROR, message);
	}
}
