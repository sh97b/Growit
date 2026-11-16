package com.growit.record.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 키 없이도 평소 ./gradlew test에서 항상 도는 테스트. 가짜 AiCaller로 하네스(AiQualityEvalRunner)
 * 자체가 CSV·리포트를 올바른 모양으로 만들어내는지만 확인한다(실제 AI 호출 없음). docs/eval/을 덮어쓰지
 * 않도록 결과는 임시 디렉터리에 쓴다.
 */
class AiQualityEvalHarnessSmokeTest {

	@Test
	void run_withFakeCaller_producesCsvAndReport(@TempDir Path tempDir) throws IOException {
		List<String> models = List.of("fake-model-a", "fake-model-b");
		List<EvalCase> cases = AiQualityEvalCases.buildAll();
		int repeats = 2;
		AiCaller fakeCaller = (model, systemPrompt, userContent) ->
				new EvalAiResponse("{\"title\":\"오늘 연습 정리\",\"tags\":[\"연습\"],\"relatedRecordIds\":[],\"change\":null}",
						120, 40, 160, model, 5L);

		List<EvalResultRow> rows = AiQualityEvalRunner.run(models, cases, repeats, fakeCaller, false);

		assertThat(cases).hasSize(14);
		assertThat(rows).hasSize(models.size() * cases.size() * repeats);
		assertThat(rows).allSatisfy(row -> {
			assertThat(row.status()).isEqualTo("OK");
			assertThat(row.jsonValid()).isTrue();
			assertThat(row.schemaValid()).isTrue();
			assertThat(row.evidenceValid()).isTrue();
			assertThat(row.bannedExpression()).isFalse();
			assertThat(row.finalOk()).isTrue();
		});

		Path csvFile = tempDir.resolve("results.csv");
		Path reportFile = tempDir.resolve("report.md");
		AiQualityEvalRunner.writeCsv(rows, csvFile);
		AiQualityEvalRunner.writeReport(rows, models, reportFile);

		List<String> csvLines = Files.readAllLines(csvFile, StandardCharsets.UTF_8);
		assertThat(csvLines).hasSize(rows.size() + 1);
		assertThat(csvLines.get(0)).startsWith("model,case_id,label,category,injection_type,repeat,status");

		String report = Files.readString(reportFile, StandardCharsets.UTF_8);
		assertThat(report).contains("# AI 품질 평가 리포트");
		assertThat(report).contains("fake-model-a");
		assertThat(report).contains("fake-model-b");
		assertThat(report).contains("final_ok");
		assertThat(report).contains("원본 위반이 서비스에서 걸러진 비율");
	}
}
