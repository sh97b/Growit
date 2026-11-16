package com.growit.record.service;

import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.ai.repository.AiLogRepository;
import com.growit.ai.service.AiCallService;
import com.growit.ai.service.AiResponseValidator;
import com.growit.ai.service.AiSafetyPrompt;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.record.repository.ActivityRecordRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * E1 AI 품질 평가 하네스의 핵심 실행 로직. 실제 네트워크 호출(AiQualityEvalTest)과 가짜 호출
 * (AiQualityEvalHarnessSmokeTest)이 이 클래스를 공유한다 — AiCaller 경계만 바꿔 끼운다.
 *
 * 프로덕션 재사용: 시스템 프롬프트는 SystemPromptLoader(record-analysis-system.txt)로 그대로 읽고,
 * 안전 프롬프트는 AiSafetyPrompt.DATA_NOT_INSTRUCTION을 AiCallService와 똑같이 뒤에 붙이고, 입력은
 * AnalysisPayload/AnalysisHobbyInput/AnalysisRecordInput/AnalysisHistoryItem을 그대로 쓰고, 검증은
 * AiResponseValidator.parse()와 RecordAnalysisService.validate()(이번에 package-private으로 완화함)를
 * 그대로 호출한다.
 */
final class AiQualityEvalRunner {

	private static final int RAW_SNIPPET_MAX = 80;
	private static final int ERROR_MESSAGE_MAX = 300;
	private static final long SLEEP_BETWEEN_CALLS_MS = 300;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final AiResponseValidator aiResponseValidator = new AiResponseValidator(objectMapper);
	private final RecordAnalysisService recordAnalysisService = new RecordAnalysisService(
			mock(ActivityRecordRepository.class), mock(HobbyRepository.class), mock(AiCallService.class),
			aiResponseValidator, mock(AiLogRepository.class), objectMapper, mock(PlatformTransactionManager.class));
	private final SystemPromptLoader promptLoader = new SystemPromptLoader("record-analysis-system.txt");

	private AiQualityEvalRunner() {
	}

	static List<EvalResultRow> run(List<String> models, List<EvalCase> cases, int repeats, AiCaller caller,
			boolean sleepBetweenCalls) {
		AiQualityEvalRunner runner = new AiQualityEvalRunner();
		String systemPrompt;
		try {
			systemPrompt = runner.promptLoader.load();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		String safeSystemPrompt = systemPrompt + "\n\n" + AiSafetyPrompt.DATA_NOT_INSTRUCTION;

		List<EvalResultRow> rows = new ArrayList<>();
		for (String model : models) {
			for (EvalCase evalCase : cases) {
				String userContent;
				try {
					userContent = runner.objectMapper.writeValueAsString(evalCase.payload());
				} catch (Exception e) {
					throw new IllegalStateException("평가 입력을 JSON으로 바꾸지 못함: " + evalCase.label(), e);
				}
				Set<String> inputDigits = AiQualityEvalMetrics.extractDigitTokens(userContent);
				for (int repeat = 0; repeat < repeats; repeat++) {
					rows.add(runner.runOne(model, evalCase, safeSystemPrompt, systemPrompt, userContent,
							inputDigits, caller, repeat));
					if (sleepBetweenCalls) {
						sleepQuietly();
					}
				}
			}
		}
		return rows;
	}

	private EvalResultRow runOne(String model, EvalCase evalCase, String safeSystemPrompt, String rawSystemPrompt,
			String userContent, Set<String> inputDigits, AiCaller caller, int repeat) {
		EvalAiResponse response;
		try {
			response = caller.call(model, safeSystemPrompt, userContent);
		} catch (Exception e) {
			return failRow(model, evalCase, repeat, truncate(String.valueOf(e.getMessage()), ERROR_MESSAGE_MAX));
		}

		String rawJson = response.content();
		JsonNode node;
		boolean jsonValid;
		try {
			node = aiResponseValidator.parse(rawJson);
			jsonValid = true;
		} catch (RuntimeException e) {
			node = null;
			jsonValid = false;
		}

		Boolean schemaValid = null;
		Boolean evidenceValid = null;
		Boolean bannedExpression = null;
		Boolean toneViolation = null;
		Boolean numericHallucination = null;
		if (jsonValid) {
			schemaValid = AiQualityEvalMetrics.schemaValid(node);
			evidenceValid = AiQualityEvalMetrics.evidenceValid(node, evalCase.allowedHistoryIds());
			bannedExpression = AiQualityEvalMetrics.bannedExpression(node, RecordAnalysisService.FORBIDDEN_WORDS);
			toneViolation = AiQualityEvalMetrics.toneViolation(node);
			numericHallucination = AiQualityEvalMetrics.numericHallucination(node, inputDigits);
		}
		boolean injectionFollowed =
				AiQualityEvalMetrics.injectionFollowed(rawJson, evalCase.injectionType(), rawSystemPrompt);

		boolean finalOk;
		try {
			StoredAnalysis stored =
					recordAnalysisService.validate(rawJson, evalCase.allowedHistoryIds(), model);
			finalOk = stored.title() != null && !stored.title().isBlank();
		} catch (RuntimeException e) {
			finalOk = false;
		}

		return new EvalResultRow(model, evalCase.id(), evalCase.label(), evalCase.category(),
				evalCase.injectionType(), repeat, "OK", jsonValid, schemaValid, evidenceValid, bannedExpression,
				toneViolation, numericHallucination, evalCase.injectionType() == null ? null : injectionFollowed,
				finalOk, response.latencyMs(), response.promptTokens(), response.completionTokens(),
				response.totalTokens(), null, truncate(rawJson, RAW_SNIPPET_MAX));
	}

	private EvalResultRow failRow(String model, EvalCase evalCase, int repeat, String errorMessage) {
		return new EvalResultRow(model, evalCase.id(), evalCase.label(), evalCase.category(),
				evalCase.injectionType(), repeat, "FAIL", null, null, null, null, null, null, null, false, null,
				null, null, null, errorMessage, null);
	}

	private static void sleepQuietly() {
		try {
			Thread.sleep(SLEEP_BETWEEN_CALLS_MS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static String truncate(String text, int max) {
		if (text == null) {
			return null;
		}
		return text.length() > max ? text.substring(0, max) : text;
	}

	static void writeCsv(List<EvalResultRow> rows, Path file) throws IOException {
		Files.createDirectories(file.getParent());
		StringBuilder sb = new StringBuilder();
		sb.append("model,case_id,label,category,injection_type,repeat,status,json_valid,schema_valid,")
				.append("evidence_valid,banned_expression,tone_violation,numeric_hallucination,injection_followed,")
				.append("final_ok,latency_ms,prompt_tokens,completion_tokens,total_tokens,error_message,raw_snippet\n");
		for (EvalResultRow row : rows) {
			sb.append(csv(row.model())).append(',')
					.append(row.caseId()).append(',')
					.append(csv(row.label())).append(',')
					.append(csv(row.category())).append(',')
					.append(csv(row.injectionType())).append(',')
					.append(row.repeatIndex()).append(',')
					.append(csv(row.status())).append(',')
					.append(csv(row.jsonValid())).append(',')
					.append(csv(row.schemaValid())).append(',')
					.append(csv(row.evidenceValid())).append(',')
					.append(csv(row.bannedExpression())).append(',')
					.append(csv(row.toneViolation())).append(',')
					.append(csv(row.numericHallucination())).append(',')
					.append(csv(row.injectionFollowed())).append(',')
					.append(csv(row.finalOk())).append(',')
					.append(csv(row.latencyMs())).append(',')
					.append(csv(row.promptTokens())).append(',')
					.append(csv(row.completionTokens())).append(',')
					.append(csv(row.totalTokens())).append(',')
					.append(csv(row.errorMessage())).append(',')
					.append(csv(row.rawSnippet())).append('\n');
		}
		Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
	}

	private static String csv(Object value) {
		if (value == null) {
			return "";
		}
		String text = String.valueOf(value);
		if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
			return "\"" + text.replace("\"", "\"\"") + "\"";
		}
		return text;
	}

	static void writeReport(List<EvalResultRow> rows, List<String> models, Path file) throws IOException {
		Files.createDirectories(file.getParent());
		StringBuilder md = new StringBuilder();
		md.append("# AI 품질 평가 리포트\n\n");
		md.append("- 생성 시각: ").append(LocalDate.now()).append('\n');
		md.append("- 모델: ").append(String.join(", ", models)).append('\n');
		md.append("- 사례 수: 14개 × 반복 2회 × 모델 ").append(models.size()).append("개 = 호출 ")
				.append(rows.size()).append("건\n");
		md.append("- 한계: 표본이 작음(사례당 2회), 모델 응답은 비결정적이라 재실행 시 수치가 달라질 수 있음, ")
				.append("공용 AI 키를 여럿이 함께 쓰므로 반복 실행은 비용·한도를 함께 고려해야 함, ")
				.append("비용은 토큰 수만 기록하고 가격은 발표 전 OpenAI 가격 페이지로 직접 확인한다.\n\n");

		md.append("## 모델별 요약\n\n");
		md.append("| 모델 | 건수 | json_valid | schema_valid | evidence_valid | banned_expression | ")
				.append("tone_violation | numeric_hallucination | final_ok | 평균 지연(ms) | 평균 토큰 |\n");
		md.append("|---|---|---|---|---|---|---|---|---|---|---|\n");
		for (String model : models) {
			List<EvalResultRow> modelRows = rows.stream().filter(r -> r.model().equals(model)).toList();
			md.append("| ").append(model).append(" | ").append(modelRows.size()).append(" | ")
					.append(rate(modelRows, EvalResultRow::jsonValid)).append(" | ")
					.append(rate(modelRows, EvalResultRow::schemaValid)).append(" | ")
					.append(rate(modelRows, EvalResultRow::evidenceValid)).append(" | ")
					.append(rate(modelRows, EvalResultRow::bannedExpression)).append(" | ")
					.append(rate(modelRows, EvalResultRow::toneViolation)).append(" | ")
					.append(rate(modelRows, EvalResultRow::numericHallucination)).append(" | ")
					.append(rate(modelRows, EvalResultRow::finalOk)).append(" | ")
					.append(average(modelRows, EvalResultRow::latencyMs)).append(" | ")
					.append(average(modelRows, EvalResultRow::totalTokens)).append(" |\n");
		}

		md.append("\n## 범주별 요약\n\n");
		md.append("| 범주 | 건수 | final_ok | injection_followed |\n|---|---|---|---|\n");
		for (String category : List.of("NORMAL", "FIRST_RECORD", "RESTED", "NO_COMPARE", "INJECTION", "LONG_TEXT",
				"EMOJI_MIX")) {
			List<EvalResultRow> categoryRows = rows.stream().filter(r -> r.category().equals(category)).toList();
			if (categoryRows.isEmpty()) {
				continue;
			}
			md.append("| ").append(category).append(" | ").append(categoryRows.size()).append(" | ")
					.append(rate(categoryRows, EvalResultRow::finalOk)).append(" | ")
					.append(rate(categoryRows, EvalResultRow::injectionFollowed)).append(" |\n");
		}

		List<EvalResultRow> withBanned = rows.stream().filter(r -> Boolean.TRUE.equals(r.bannedExpression())).toList();
		List<EvalResultRow> withInjection =
				rows.stream().filter(r -> Boolean.TRUE.equals(r.injectionFollowed())).toList();
		md.append("\n## 원본 위반이 서비스에서 걸러진 비율\n\n");
		md.append("- 금지 표현이 원본에 있었던 ").append(withBanned.size()).append("건 중 final_ok=false(걸러짐)인 건: ")
				.append(withBanned.stream().filter(r -> Boolean.FALSE.equals(r.finalOk())).count()).append("건\n");
		md.append("- 주입 공격이 원본에서 성공한 ").append(withInjection.size()).append("건 중 final_ok=false(걸러짐)인 건: ")
				.append(withInjection.stream().filter(r -> Boolean.FALSE.equals(r.finalOk())).count()).append("건\n");
		md.append("  (금지 표현 목록은 칭찬·점수류 단어뿐이라, 주입된 임의 문자열은 길이 제한에 걸리지 않는 한 ")
				.append("final_ok를 통과할 수 있다 — 서비스의 주입 방어가 구조적 한계임을 보여주는 지표)\n");

		md.append("\n## 실패·특이 사례\n\n");
		rows.stream()
				.filter(r -> "FAIL".equals(r.status()) || Boolean.TRUE.equals(r.injectionFollowed())
						|| Boolean.TRUE.equals(r.bannedExpression()))
				.limit(3)
				.forEach(r -> md.append("- [").append(r.model()).append("] ").append(r.label()).append(": ")
						.append(r.rawSnippet() != null ? r.rawSnippet() : r.errorMessage()).append('\n'));

		Files.writeString(file, md.toString(), StandardCharsets.UTF_8);
	}

	private static String rate(List<EvalResultRow> rows, java.util.function.Function<EvalResultRow, Boolean> field) {
		List<Boolean> values = rows.stream().map(field).filter(java.util.Objects::nonNull).toList();
		if (values.isEmpty()) {
			return "N/A";
		}
		long trueCount = values.stream().filter(Boolean::booleanValue).count();
		return String.format(Locale.KOREA, "%.0f%%", trueCount * 100.0 / values.size());
	}

	private static String average(List<EvalResultRow> rows, java.util.function.Function<EvalResultRow, Number> field) {
		List<Number> values = rows.stream().<Number>map(field).filter(java.util.Objects::nonNull).toList();
		if (values.isEmpty()) {
			return "N/A";
		}
		double avg = values.stream().mapToDouble(Number::doubleValue).average().orElse(0);
		return String.format(Locale.KOREA, "%.0f", avg);
	}
}
