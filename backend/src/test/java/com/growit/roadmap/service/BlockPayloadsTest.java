package com.growit.roadmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.roadmap.entity.BlockKind;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class BlockPayloadsTest {

	private final ObjectMapper mapper = new ObjectMapper();

	private JsonNode json(String text) throws Exception {
		return mapper.readTree(text);
	}

	private void assertInvalid(BlockKind kind, String payload) {
		assertThatThrownBy(() -> BlockPayloads.normalize(kind, json(payload), mapper))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void memo_trimsAndKeepsOnlyKnownFields() throws Exception {
		String saved = BlockPayloads.normalize(BlockKind.MEMO, json("{\"text\":\"  천천히  \",\"x\":1}"), mapper);

		assertThat(json(saved).path("text").asText()).isEqualTo("천천히");
		assertThat(json(saved).has("x")).isFalse();
	}

	@Test
	void memo_emptyOrTooLong_isRejected() {
		assertInvalid(BlockKind.MEMO, "{\"text\":\"   \"}");
		assertInvalid(BlockKind.MEMO, "{\"text\":\"" + "가".repeat(501) + "\"}");
		assertInvalid(BlockKind.MEMO, "{\"text\":5}");
	}

	@Test
	void memo_exactlyAtLimit_isAccepted() throws Exception {
		String saved = BlockPayloads.normalize(BlockKind.MEMO, json("{\"text\":\"" + "가".repeat(500) + "\"}"), mapper);

		assertThat(json(saved).path("text").asText()).hasSize(500);
	}

	@Test
	void link_httpsAndHttp_areAccepted_andHostIsDerived() throws Exception {
		String https = BlockPayloads.normalize(BlockKind.LINK,
				json("{\"url\":\"https://www.YouTube.com/watch?v=abc\",\"title\":\"강의\",\"category\":\"video\"}"), mapper);
		String http = BlockPayloads.normalize(BlockKind.LINK, json("{\"url\":\"http://example.com/a\"}"), mapper);

		assertThat(json(https).path("host").asText()).isEqualTo("youtube.com");
		assertThat(json(https).path("category").asText()).isEqualTo("VIDEO");
		assertThat(json(https).path("title").asText()).isEqualTo("강의");
		assertThat(json(http).path("category").asText()).isEqualTo("OTHER");
		assertThat(json(http).has("title")).isFalse();
	}

	static Stream<String> badUrls() {
		return Stream.of(
				"javascript:alert(1)",
				"JavaScript:alert(1)",
				"data:text/html,<script>alert(1)</script>",
				"ftp://example.com/file",
				"file:///etc/passwd",
				"//example.com/path",
				"example.com/path",
				"https://",
				"https:///path",
				"https://user:pass@example.com/",
				"https://exa mple.com/",
				"https://exa" + (char) 0 + "mple.com/",
				"http://");
	}

	@ParameterizedTest
	@MethodSource("badUrls")
	void link_badSchemeOrShape_isRejected(String url) throws Exception {
		String payload = mapper.writeValueAsString(mapper.createObjectNode().put("url", url));

		assertInvalid(BlockKind.LINK, payload);
	}

	@Test
	void link_tooLongUrlOrTitleOrUnknownCategory_isRejected() throws Exception {
		assertInvalid(BlockKind.LINK, "{\"url\":\"https://example.com/" + "a".repeat(500) + "\"}");
		assertInvalid(BlockKind.LINK, "{\"url\":\"https://example.com\",\"title\":\"" + "가".repeat(61) + "\"}");
		assertInvalid(BlockKind.LINK, "{\"url\":\"https://example.com\",\"category\":\"GAME\"}");
	}

	@Test
	void timer_rangeIsOneTo120() throws Exception {
		assertThat(json(BlockPayloads.normalize(BlockKind.TIMER, json("{\"minutes\":1}"), mapper)).path("minutes").asInt())
				.isEqualTo(1);
		assertThat(json(BlockPayloads.normalize(BlockKind.TIMER, json("{\"minutes\":120}"), mapper)).path("minutes").asInt())
				.isEqualTo(120);
		assertInvalid(BlockKind.TIMER, "{\"minutes\":0}");
		assertInvalid(BlockKind.TIMER, "{\"minutes\":121}");
		assertInvalid(BlockKind.TIMER, "{\"minutes\":2.5}");
		assertInvalid(BlockKind.TIMER, "{\"minutes\":\"10\"}");
		assertInvalid(BlockKind.TIMER, "{}");
	}

	@Test
	void search_lengthIsOneTo40() {
		assertInvalid(BlockKind.SEARCH, "{\"query\":\"\"}");
		assertInvalid(BlockKind.SEARCH, "{\"query\":\"" + "가".repeat(41) + "\"}");
	}

	@Test
	void sublist_oneToEightItems_withDoneFlags() throws Exception {
		String saved = BlockPayloads.normalize(BlockKind.SUBLIST,
				json("{\"items\":[{\"text\":\"하나\",\"done\":true},{\"text\":\"둘\"}]}"), mapper);

		assertThat(json(saved).path("items")).hasSize(2);
		assertThat(json(saved).path("items").get(0).path("done").asBoolean()).isTrue();
		assertThat(json(saved).path("items").get(1).path("done").asBoolean()).isFalse();
		assertInvalid(BlockKind.SUBLIST, "{\"items\":[]}");
		StringBuilder nine = new StringBuilder("{\"items\":[");
		for (int i = 0; i < 9; i++) {
			nine.append(i == 0 ? "" : ",").append("{\"text\":\"항목\"}");
		}
		assertInvalid(BlockKind.SUBLIST, nine.append("]}").toString());
	}

	@Test
	void aiHowto_cannotBeCreatedOrEditedByHand() {
		assertInvalid(BlockKind.AI_HOWTO, "{\"steps\":[\"a\"]}");
	}

	@Test
	void nonObjectPayload_isRejected() throws Exception {
		assertThatThrownBy(() -> BlockPayloads.normalize(BlockKind.MEMO, json("[1]"), mapper))
				.isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> BlockPayloads.normalize(BlockKind.MEMO, null, mapper))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void serialize_over4KbIsRejected() {
		com.fasterxml.jackson.databind.node.ObjectNode node = mapper.createObjectNode();
		node.put("text", "가".repeat(1400));

		assertThatThrownBy(() -> BlockPayloads.serialize(node, mapper)).isInstanceOf(ApiException.class);
	}
}
