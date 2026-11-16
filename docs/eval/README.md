# AI 품질 평가 하네스 (E1)

프로덕션의 지시문 로더(`SystemPromptLoader`)·안전 프롬프트(`AiSafetyPrompt`)·분석 입력 구성
(`AnalysisPayload` 등)·`AiResponseValidator`·`RecordAnalysisService.validate()`를 그대로 재사용해,
기록 정리(ANALYSIS) 기능의 AI 품질을 모델별로 숫자로 측정한다. 전부 직접 지어낸 가상 데이터이며
실제 사용자 글은 쓰지 않는다.

코드 위치: `backend/src/test/java/com/growit/record/service/AiQualityEval*.java` 외.
실제 네트워크 호출을 하는 `AiQualityEvalTest`는 `AI_API_KEY`·`AI_EVAL=1` 환경변수가 둘 다 있어야
돌고, 평소 `./gradlew test`에서는 건너뛴다. 가짜 클라이언트로 하네스 자체를 검증하는
`AiQualityEvalHarnessSmokeTest`는 항상 돈다(임시 디렉터리에만 씀, 이 폴더를 건드리지 않음).

## 실행 방법 (IntelliJ)

1. `AiQualityEvalTest`를 단일 클래스로 실행하는 Run/Debug Configuration을 새로 만든다.
2. Environment variables에 추가:
   - `AI_API_KEY=(실제 키)`
   - `AI_EVAL=1`
   - (선택) `EVAL_MODELS=gpt-4.1-nano,gpt-4.1-mini,gpt-5.4-mini` (기본값과 같으면 생략 가능)
   - (선택) `AI_BASE_URL`, `EVAL_MAX_TOKENS`(기본 1200)
3. 실행하면 이 폴더에 `results.csv`(호출 단위 전체 기록)와 `report.md`(모델별·범주별 요약)가
   만들어진다(덮어씀).

## 주의

- 호출 사이 300ms 간격을 둔다. 모델 수 × 14사례 × 2회 호출이라 공용 AI 키 한도를 같이 고려한다.
- 가격표는 코드에 없다. report.md는 토큰 수만 기록하고, 비용은 발표 전 OpenAI 가격 페이지로 직접
  확인한다.
- `RecordAnalysisService.validate()`와 `FORBIDDEN_WORDS`는 이 하네스가 같은 패키지의 테스트에서
  재사용할 수 있도록 접근 제한자를 `private`에서 package-private으로 완화했다(그 외 프로덕션 코드는
  수정하지 않음).
