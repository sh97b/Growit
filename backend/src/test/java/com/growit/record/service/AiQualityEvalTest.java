package com.growit.record.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * E1 AI 품질 평가 하네스(실제 호출). AI_API_KEY와 AI_EVAL=1 환경변수가 둘 다 있어야 돈다 —
 * 평소 ./gradlew test에서는 항상 건너뛴다. 실행 방법은 AiQualityEvalTest 클래스 Javadoc 끝에
 * 적어 둔 대로 IntelliJ의 Run/Debug Configuration > Environment variables에서 넣는다.
 *
 * 실행 순서(IntelliJ): Run/Debug Configuration을 이 클래스(AiQualityEvalTest)로 새로 만들고
 * Environment variables에 AI_API_KEY=(실제 키), AI_EVAL=1, 필요하면 EVAL_MODELS=모델1,모델2 를
 * 추가한 뒤 실행한다. 끝나면 docs/eval/results.csv·docs/eval/report.md가 만들어진다.
 */
class AiQualityEvalTest {

	@Test
	@EnabledIfEnvironmentVariable(named = "AI_API_KEY", matches = ".+")
	@EnabledIfEnvironmentVariable(named = "AI_EVAL", matches = "1")
	void runRealEval() throws IOException {
		String apiKey = System.getenv("AI_API_KEY");
		String baseUrl = envOrDefault("AI_BASE_URL", "https://api.openai.com/v1");
		int maxTokens = Integer.parseInt(envOrDefault("EVAL_MAX_TOKENS", "1200"));
		List<String> models = Arrays.stream(envOrDefault("EVAL_MODELS", "gpt-4.1-nano,gpt-4.1-mini,gpt-5.4-mini")
				.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.toList();

		AiCaller caller = new OpenAiRestCaller(baseUrl, apiKey, 20, maxTokens);
		List<EvalCase> cases = AiQualityEvalCases.buildAll();
		List<EvalResultRow> rows = AiQualityEvalRunner.run(models, cases, 2, caller, true);

		Path evalDir = resolveDocsEvalDir();
		AiQualityEvalRunner.writeCsv(rows, evalDir.resolve("results.csv"));
		AiQualityEvalRunner.writeReport(rows, models, evalDir.resolve("report.md"));
	}

	private static String envOrDefault(String name, String fallback) {
		String value = System.getenv(name);
		return value == null || value.isBlank() ? fallback : value;
	}

	/** docs/eval은 repo 루트에 있고 테스트 작업 디렉터리는 보통 backend/라, prompts/ 디렉터리를 기준 삼아 루트를 찾는다. */
	private static Path resolveDocsEvalDir() {
		Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		for (int i = 0; i < 5 && dir != null; i++) {
			if (Files.isDirectory(dir.resolve("prompts"))) {
				return dir.resolve("docs").resolve("eval");
			}
			dir = dir.getParent();
		}
		return Path.of("docs", "eval");
	}
}
