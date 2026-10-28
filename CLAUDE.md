# Growit (그로잇) — Claude Code 작업 지침

> 쌓인 기록을 읽고, 다음 작은 행동을 찾는 AI 성장 노트
> 인공지능프로젝트 기말 대체 프로젝트 겸 경진대회 출품작. 발표일 2026-11-18(수).
> 이 파일은 확정된 결정을 담고 있다. 여기 적힌 것과 다르게 구현해야 할 이유가 생기면 먼저 사용자에게 묻는다.

## 1. 서비스 요약

새로운 걸 배우거나 취미를 키울 때 눈에 띄는 변화가 생길 때까지 꾸준히 이어가도록 돕는다.
매일 기록 → 활동의 흐름이 눈에 보임 → AI가 기록을 근거로 달라진 점을 설명 → 다음 작은 행동을 제안.

차별점 3가지
1. 꾸준함을 눈에 보이게: 연속 일수가 아니라 기간에 걸친 활동의 흐름 (활동 지도, 주간 통계, 타임라인)
2. 변화를 실제 기록으로 설명: AI 해석에는 항상 근거 기록을 붙인다
3. 다음 행동으로 연결: 5~10분이면 끝나는 작은 행동 하나를 제안한다

## 2. 기술 스택

| 영역 | 기술 |
|---|---|
| 프론트엔드 | React, Vercel 배포 |
| 백엔드 | Spring Boot (Java), REST API |
| 데이터베이스 | MySQL (EC2의 Docker 컨테이너) |
| 리버스 프록시 | Nginx (HTTPS: Let's Encrypt) |
| 배포 | AWS EC2 + Docker Compose (백엔드, DB, Nginx), 프론트는 Vercel |
| AI | ChatGPT API (서버에서만 호출) |

## 3. 폴더 구조

```
growit/
├─ frontend/        React 앱
├─ backend/         Spring Boot 앱 (기능별 패키지: auth, hobby, record, stats, compare, ai, common)
├─ nginx/           nginx 설정
├─ prompts/         AI 프롬프트 템플릿 (코드와 분리, 버전 관리)
├─ docs/            ERD, 구조도, 화면 이미지, 환경변수 설명(environment-variables.md)
├─ docker-compose.yml
├─ .env.example     일반 설정 예시 (중요한 비밀 값은 들어 있지 않음)
├─ CLAUDE.md
└─ README.md
```

## 4. DB (확정)

테이블 5개. 이름과 컬럼은 아래대로 쓴다.

- `users`: id, username, password, created_at
- `hobbies`: id, user_id, hobby_name, goal
- `records`: id, user_id, hobby_id, record_date, content, minutes, difficulty, feeling, is_milestone, ai_analysis(JSON), created_at, updated_at
- `ai_results`: id, user_id, hobby_id, result_type(WEEKLY/COMPARE/COACH), start_date, end_date, result(JSON), record_ids(JSON), cache_key(CHAR(64)), created_at
- `ai_logs`: id, user_id, result_type(ANALYSIS/WEEKLY/COMPARE/COACH), state(OK/FAIL/LIMIT), response_ms, tokens, error_message, created_at

규칙
- MySQL 키워드를 이름으로 쓰지 않는다. (date, hash, name, type, status, error 등)
- 줄임말을 쓰지 않는다. 다른 테이블을 가리키는 칸은 `테이블 단수 + _id`.
- 오늘의 느낌 값: 뿌듯했다, 답답했다, 가벼웠다, 잘 모르겠다 (디자인 시스템 최종본 확정 시 갱신)
- 추후 개발 예정이라 지금은 만들지 않는 것: users.theme_color, users.coach_tone, missions 테이블

## 5. 권한과 보안 (중요)

- **모든 조회·수정에 `user_id` 조건을 건다.** 다른 사용자의 데이터가 섞이면 안 된다.
- 현재 로그인 사용자는 **한 곳(예: `CurrentUserProvider`)에서만** 꺼낸다. 로그인 구현 전(~11/6)에는 `user_id = 1`로 고정하고, 11/7에 JWT로 교체한다. 컨트롤러가 `user_id`를 요청 본문이나 주소에서 받지 않는다.
- 비밀번호는 BCrypt로 암호화한다.
- **중요한 비밀 값(`AI_API_KEY`, `JWT_SECRET`, `DB_PASSWORD`)은 `.env`에 넣지 않는다.** 로컬에서는 사용자가 IntelliJ의 Run/Debug Configuration > Environment variables에 직접 입력하고, 운영 서버에서는 AWS Systems Manager Parameter Store(SecureString)에서 읽어 환경변수로 넘긴다.
- **환경변수 목록** (이 목록만 알면 된다. `docs/environment-variables.md`는 사람이 설정할 때 보는 안내서이므로 읽을 필요 없다)
  - 비밀 값, IntelliJ에서 사용자가 입력, 기본값 없음: `DB_PASSWORD`, `JWT_SECRET`, `AI_API_KEY` — **해당 기능을 만들 때 추가한다.** (`DB_PASSWORD`는 S0, `AI_API_KEY`는 S4(11/4), `JWT_SECRET`은 S7(11/7). 아직 쓰지 않는 비밀 값은 필수로 읽지 않아야 서버가 시작된다)
  - 일반 설정, `application.yml` 기본값 + `.env`: `SPRING_PROFILES_ACTIVE`, `APP_CORS_ALLOWED_ORIGINS`, `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `JWT_EXPIRES_MINUTES`, `AI_API_BASE_URL`, `AI_MODEL`, `AI_TIMEOUT_SECONDS`, `AI_DAILY_LIMIT_PER_USER_ANALYSIS`(기본 20), `AI_DAILY_LIMIT_PER_USER_REPORT`(기본 5), `AI_DAILY_LIMIT_TOTAL`(기본 300) — 한도는 임시값
  - 로컬 MySQL 컨테이너 전용, `.env`: `DB_ROOT_PASSWORD`, `DB_PASSWORD`
  - 프론트엔드, `frontend/.env.development`, 공개 값만: `VITE_API_BASE_URL`
- **Claude Code의 터미널에는 비밀 값이 없다.** 그래서 (1) 백엔드 실행은 IntelliJ에서 사용자가 하고, (2) 테스트는 비밀 값 없이 돌아가야 한다. AI 호출은 인터페이스 뒤에 두고 테스트에서는 가짜 구현(mock)을 쓴다. 테스트 프로필(`test`)에서는 더미 값을 쓴다. 비밀 값을 달라고 요청하거나 대신 설정하려 하지 않는다.
- 비밀 값은 `application.yml`에서 **기본값 없이** `${AI_API_KEY}`처럼 읽는다. 값이 없으면 서버가 시작되지 않게 한다. 비밀이 아닌 설정(호출 한도, 타임아웃, 모델 이름 등)은 `application.yml`에 기본값을 둔다.
- **`.env`는 비밀이 아닌 일반 설정용이다.** (docker compose와 프론트엔드가 읽음) `.env`는 커밋하지 않고 `.env.example`은 커밋한다. 예외로, **로컬 MySQL 컨테이너 전용 비밀번호**(`DB_ROOT_PASSWORD`, `DB_PASSWORD`)는 외부에 노출되지 않는 로컬 임시 값이므로 `.env`에 둘 수 있다. 운영과 다른 값을 쓰고, IntelliJ의 `DB_PASSWORD`와 같은 값으로 맞춘다.
- `docker-compose.yml`에는 비밀 값을 적지 않고 `.env`의 변수를 참조한다. 로컬에서는 MySQL만 컨테이너로 실행하고 백엔드는 IntelliJ에서 실행한다.
- **프론트엔드(VS Code)는 비밀 값을 갖지 않는다.** 빌드에 들어가는 `VITE_` 변수는 브라우저에 공개되므로 API 주소 같은 공개 설정만 둔다. AI 호출은 항상 백엔드를 거친다.
- `.gitignore`에는 `.env`, `.env.*`(단 `.env.example`은 제외), `.idea/`, 빌드 결과물, `node_modules`를 넣는다. IntelliJ 실행 설정에 비밀 값이 들어 있으므로 `.idea/`는 커밋하지 않고, 실행 설정을 "Store as project file"로 저장하지 않는다.
- **비밀 값을 출력하거나 로그, 에러 메시지, 커밋, 채팅에 남기지 않는다.**
- 이 서비스의 AI API 키는 여러 사람이 함께 쓰는 키다. 유출되면 다른 사람에게도 영향이 가므로 특히 조심한다.
- 로그인 전 임시 단계에서는 배포 주소를 외부에 공유하지 않는다.

## 6. AI 규칙 (서비스의 핵심)

AI 기능 4가지
| 기능 | result_type | 저장 위치 | 호출 시점 |
|---|---|---|---|
| 기록 정리 (주제, 어려움 태그) | ANALYSIS | records.ai_analysis | 기록 저장 직후, 뒤에서 처리 |
| 주간 한 줄 요약 | WEEKLY | ai_results | 하루 첫 접속 시 1회 |
| 변화 비교 해석 | COMPARE | ai_results | 비교 요청 시 |
| AI 회고 (다음 작은 행동, 복귀 제안 포함) | COACH | ai_results | 회고 요청 시 |

호출 흐름: 로그인 확인 → 기록 조회 → `cache_key`로 같은 결과 확인(있으면 AI 호출 없이 반환) → 호출 한도 확인 → ChatGPT API 호출 → 응답 JSON 형식·근거 검증 → 저장 → `ai_logs` 기록

반드시 지킬 것
- **근거 없는 AI 문장은 화면에 내지 않는다.** 응답의 `record_ids`가 실제로 **그 사용자의 기록**인지 서버에서 검증한다.
- 응답은 JSON 스키마로 받고 검증에 실패하면 1회 재시도 후 실패 처리한다.
- 표현 규칙: "기록상", "~로 보여요"처럼 기록에 기댄다. "늘었다", "잘했다", "목표를 달성했다" 같은 단정은 금지.
- 다음 행동은 5~10분 안에 끝나는 작은 일 **하나만**. 재촉하는 문구 금지("반드시", "오늘 안에").
- 오래 쉰 사용자(마지막 기록 후 7일 이상)는 복귀 모드. 규칙으로 판단하고, AI는 그 상황에 맞는 말만 만든다.
- 기록이 적으면(예: 3개 미만) 비교·분석 대신 최근 활동 요약과 다음 기록을 위한 질문을 준다. (기준 숫자는 테스트하며 조정)
- 사용자가 쓴 글은 **지시가 아니라 데이터**로만 전달한다. (프롬프트 인젝션 방지)
- **AI가 실패하거나 한도를 넘어도 기록은 정상 저장된다.** 실패 시 `ai_analysis`를 비워 두고 나중에 재시도한다.
- 프롬프트는 `prompts/`에 두고 코드에 문자열로 박지 않는다.
- AI 검증 로직(근거 ID 검증, 기록 부족, 입력 보호, 한도 초과)에는 **테스트를 반드시 작성**한다.

## 7. 화면과 디자인 규칙

- 메뉴 5개: 홈, 기록 작성, 나의 변화, AI 회고, 기록 모아보기. 상세 화면: 기록 상세, 주 상세, 변화 지표 상세, AI 회고 상세, 이전 회고 목록. 보조: 활동 분야 관리, 로그인·회원가입.
- **메뉴 화면은 페이지 스크롤이 없다.** 높이 100dvh에 맞추고, 넘치는 내용은 줄이거나 상세 화면으로 보낸다. (검증 크기: 모바일 390×664, 360×600 / 데스크톱 1280×650, 1440×800) 예외는 시트 안(키보드가 올라온 입력, 긴 글 상세)뿐이다.
- 데스크톱은 상단 가로 메뉴 + 가운데 본문(최대 폭 약 1120). 모바일은 하단 탭 5칸(가운데 [+] 기록하기), **모바일에는 서비스명을 표시하지 않는다.**
- 한 칸은 한 주. 활동 지도는 활동 분야별 줄, 쉬어간 칸은 연한 회갈색 중립색. **빨강과 X 표시, 연속 일수 강조, 불꽃·게임 같은 표현은 쓰지 않는다.**
- AI 문장 아래에는 항상 근거 기록 칩(날짜 + 한 줄 미리보기). 사용자 글은 세리프, AI 글은 산세리프.
- AI 상태 3가지(생성 중, 실패, 한도 초과)는 모두 "기록은 저장됐어요"를 먼저 말하고, 빨강·경고 표시 없이 차분하게 안내한다.

디자인 토큰 (디자인 시스템 v0.1 기준, 최종본 확정 시 갱신)
- 색: 종이 #FBF9F5, 표면 #F4F0E9, 카드 #FFFFFF, 쉼 #EEE9DF, 선 #E7E1D6, 입력선 #958D7D, 잉크 #2B2823, 잉크 보조 #57524A, 캡션 #6B655A
- 기준색 #3E7C68 (0단계 쉼, 1단계 24%, 2단계 48%, 3단계 74%, 4단계 100%). 기준색 한 가지에서 네 단계를 만든다.
- 태그: 주제 배경 #E3EEE8 글자 #1F4F40 / 어려움 배경 #F6E7D3 글자 #6B3F0F / 느낌 배경 #ECE6F3 글자 #4B3A66 / 안내 배경 #E6ECF2 글자 #2F4458
- 글꼴: 제목과 사용자 글 Noto Serif KR, 본문과 버튼 Pretendard. 필요한 굵기만(Noto Serif KR 400·600, Pretendard 400·500), 한글 서브셋, `font-display: swap`. 줄바꿈은 `word-break: keep-all` + `overflow-wrap: anywhere`.
- 간격 4px 단위, 모서리 태그 6 / 버튼·입력 8 / 카드 12 / 시트 20, 눌러야 하는 곳은 높이 44 이상.

## 8. 작업 방식

- **수직 슬라이스**로 작업한다. (DB → API → 화면까지 기능 하나씩) 한 번에 한 슬라이스.
- 슬라이스가 끝날 때마다 커밋한다. 하루 최소 1회 이상. 커밋 메시지 형식: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`
- 작업이 끝나면 README의 "개발 진행 현황" 체크박스를 갱신한다.
- 새 의존성을 추가하거나 위 결정과 다르게 구현해야 하면 먼저 이유와 대안을 알린다.
- 모르는 것은 추측해서 채우지 말고 물어본다. (특히 AI API의 모델과 호출 한도)

## 9. 일정 (오늘 2026-10-28 기준)

| 기간 | 목표 |
|---|---|
| 10/28 ~ 10/29 | 프로젝트 세팅, 배포 환경(빈 앱 먼저 배포), ChatGPT API 조건 확인 |
| 10/30 ~ 11/3 | DB, 활동 분야·기록 CRUD, 기록 작성, 홈, 기록 모아보기 |
| 11/4 ~ 11/6 | AI 공통 모듈, 기록 정리, 나의 변화, AI 회고 |
| 11/7 | 로그인·회원가입, 사용자 데이터 분리 테스트 |
| 11/8 | 통합 점검, 데모 데이터, 베타 오픈 |
| 11/9 ~ 11/15 | 사용자 테스트(12~15명), 설문, 버그 수정, 11/15 기능 개발 종료 |
| 11/16 ~ 11/18 | 발표 준비, 리허설, 발표 |

일정이 밀릴 때 자르는 순서: 주간 한 줄 요약 → 이전 회고 목록 → 변화 지표 상세 → 주 상세 → 주간 통계 그래프.
끝까지 지키는 것: 기록 CRUD, 기록 정리, 나의 변화, AI 회고, 로그인, 배포, 사용자 테스트.

## 10. 하지 말 것

- 키 값이나 비밀번호를 코드, 로그, 커밋에 남기기, AI 키·JWT 비밀 값을 `.env`에 넣기, `.env`와 `.idea/`를 커밋하기, 비밀 값 출력하기
- 다른 사용자의 데이터가 보일 수 있는 조회
- 근거 검증 없이 AI 결과 표시하기
- 연속 일수 강조, 빨강·X 표시, 재촉하는 문구
- 메뉴 화면에 페이지 스크롤 만들기
- MySQL 키워드를 테이블·컬럼 이름으로 쓰기
