# Growit (그로잇)

> 쌓인 기록을 읽고, 다음 작은 행동을 찾는 AI 성장 노트

Growit은 새로운 걸 배우거나 취미를 키울 때, **눈에 띄는 변화가 생길 때까지 꾸준히 이어가도록** 돕는 AI 기반 성장 기록 서비스입니다.
매일 간단히 기록하면 활동의 흐름이 눈에 보이고, AI가 내 기록을 근거로 달라진 점을 설명하며, 다음에 할 수 있는 작은 행동을 제안합니다.

---

## 1. 왜 만들었나

새로운 걸 배우거나 취미를 키울 때는 눈에 띄는 변화가 생기기까지 시간이 걸립니다. 그동안 매일 꾸준히 이어가야 하는데, 바쁜 날 하루만 빠져도 흐름이 끊긴 것 같고, 쌓아 온 게 눈에 보이지 않으면 의욕이 떨어져 결국 포기하게 됩니다.

기존 습관 앱은 연속 일수와 달성률을 강조해서, 쉬는 날이 생기면 오히려 부담을 줍니다. Growit은 쉬어간 날도 이어 가는 과정의 일부로 보고, **기록의 내용**을 바탕으로 변화를 보여줍니다.

### 예상 사용자
악기, 운동, 코딩 등 새로운 걸 배우고 취미를 키워 가고 싶지만 꾸준함이 부족해 중간에 그만두곤 하는 사람들

---

## 2. 핵심 가치 (차별점)

| 차별점 | 일반적인 서비스 | Growit |
|---|---|---|
| 꾸준함을 눈에 보이게 한다 | 며칠 연속 활동했는지를 보여준다 | 기간에 걸쳐 어떤 활동을 이어왔는지를 활동 지도, 주간 통계, 타임라인으로 보여준다 |
| 변화를 실제 기록으로 설명한다 | 시간, 횟수, 달성률의 증가만 보여준다 | 과거와 현재의 기록에서 실제로 확인되는 변화를 보여주고, AI 해석에 근거 기록을 붙인다 |
| 다음 행동으로 이어준다 | 목표 달성을 축하하고 응원 한마디를 건넨다 | 지금까지의 기록을 돌아보고, 다음에 할 수 있는 작은 행동을 제안한다 |

### 설계 원칙
- 쉬어간 날은 실패로 표시하지 않는다. (빨강·X 표시 없음, 연속 일수를 강조하지 않음)
- AI 문장에는 항상 근거가 된 기록(날짜 칩)이 붙는다. "기록상"처럼 기록에 기댄 표현을 쓰고, 확인할 수 없는 실력 향상은 단정하지 않는다.
- 다음 행동은 5~10분 안에 끝나는 작은 일 하나만 제안한다.
- 내가 쓴 글이 주인공이다. (사용자 글은 세리프체로 크게, 숫자는 작고 조용하게)

---

## 3. 주요 기능

| 기능 | 설명 | 생성형 AI의 역할 |
|---|---|---|
| 활동 기록 | 날짜, 활동 분야, 한 일을 간단히 기록한다. 연습 시간, 어려웠던 점, 느낌, 의미 있는 순간 표시는 선택 항목이다 | 기록에서 주제와 어려웠던 점을 뽑아 정리한다 |
| 꾸준함 가시화 | 활동 지도, 주간 통계, 타임라인으로 쌓인 기록과 활동의 흐름을 보여준다 | 이번 주 이어온 활동을 한 줄로 요약한다 |
| 나의 변화 | 1주 전, 2주 전, 한 달 전, 몇 개월 전의 기록과 오늘의 기록을 나란히 비교한다 | 달라진 점을 근거 기록과 함께 해석한다 |
| AI 회고 | 선택한 기간을 돌아보고 반복되는 어려움을 찾아 다음 작은 행동을 제안한다. 오래 쉬었다면 부담 없는 복귀 행동을 제안한다 | 회고, 다음 작은 행동, 복귀 제안을 생성한다 |

### 1차 MVP 범위
- 활동 분야 등록, 활동 기록 등록·조회·수정·삭제
- 꾸준함 가시화 (활동 지도, 주간 통계, 타임라인)
- 기록 정리, 나의 변화(비교 + AI 해석), AI 회고
- AI 호출 한도 관리, 결과 저장과 재사용, 실패 처리
- 회원가입·로그인, 배포, 사용자 테스트와 설문

### 추후 개발 예정
- 화면 색상 직접 설정, 코치 말투 선택
- 목표 로드맵 생성, 월간 성장 리포트
- 내 기록과 대화
- 기록 공유와 응원

---

## 4. 생성형 AI 활용

| AI 기능 | 쓰이는 화면 | 입력 | 출력 | 저장 위치 | 호출 시점 |
|---|---|---|---|---|---|
| 기록 정리 | 기록 작성 | 한 일, 어려웠던 점 | 주제 태그, 어려움 태그 | `records.ai_analysis` | 기록 저장 직후 (뒤에서 처리) |
| 이 주의 한 줄 | 주 상세 | 그 주 기록(최대 300자로 자름) | 80자 이내 한 줄과 근거 기록(최대 2개) | `ai_results` (WEEKLY) | 주 상세 화면 진입 시 1회(기록 없는 주는 호출 안 함), 같은 기록이면 재사용 |
| 지표 해석 | 변화 지표 상세 | 기간의 주별 "기록한 날 수" 집계, 대표 기록(최대 6개, 최신순) | 80자 이내 한 줄과 근거 기록(최대 2개) | `ai_results` (METRICS) | 그래프 조회 시, 기록 있는 주가 2개 미만이면 LOCKED, 같은 입력이면 재사용 |
| 변화 비교 해석 | 나의 변화 | 그때와 오늘의 기록, 정리 결과, 느낌 | 달라진 점의 짧은 해석과 근거 기록 | `ai_results` (COMPARE) | 비교 요청 시, 같은 기록이면 재사용 |
| AI 회고 | AI 회고, 홈 | 선택 기간의 기록, 정리 결과, 목표, 마지막 기록 후 경과 일수 | 기간 요약, 반복되는 어려움, 다음 작은 행동, 복귀 제안, 근거 기록 | `ai_results` (COACH) | 회고 요청 시, 같은 기록이면 재사용 |
| 다음 행동 더 작게 바꾸기 | AI 회고, 홈 | 원래 다음 행동과 근거 기록(최대 5개) | 1~3분짜리 더 작은 행동 하나 | `ai_results.result`의 `simplifiedAction` (결과당 1회) | "너무 어려워요" 반응 시 (`POST /api/ai/results/{id}/simplify`) |
| 주간 자동 회고 | 홈 | 최근 14일 기록(전체 분야) | AI 회고와 동일(기간 요약~다음 작은 행동) | `ai_results` (COACH, `is_auto=true`) | 그 주에 처음 접속할 때 홈이 하루 1회 호출(`POST /api/ai/coach/auto`), 조건(AI 켜짐·기록 5개 이상·이번 주 결과 없음) 미충족 시 조용히 건너뜀 |

이 주의 한 줄·지표 해석은 "해석이 맞나요?" 피드백(`PUT /api/ai/results/{id}/feedback`에 `CONFIRMED`·`DISPUTED`)과
문장 고치기(`PATCH /api/ai/results/{id}/text`, 1~80자, 원문은 `editedText`와 별도로 보존)를 지원한다.
두 기능 모두 사용자별 호출 한도는 COMPARE·COACH와 별개인 `AI_VIEW_USER_DAILY_LIMIT`(기본 20, 합산)을 쓴다.
이 주의 한 줄(`WEEKLY`, promptVersion 2부터)은 "이어진 것"(continued, 필수)과 "아직 남은 어려움"(remaining,
없을 수 있음) 두 섹션으로 나뉜다. "고치기"는 continued 문장만 대상이다.
주간 자동 회고는 사용자별 호출 한도가 `AI_AUTO_USER_DAILY_LIMIT`(기본 1, COMPARE·COACH·WEEKLY·METRICS 한도와
별개)이고, 자동 생성된 결과를 사용자가 아직 안 열어봤으면 `GET /api/ai/results/latest?type=COACH` 응답의
`isNew`가 true다(열어보면 `PUT /api/ai/results/{id}/viewed`로 꺼진다).

### AI 호출 흐름과 안정성
1. 요청 → 로그인 확인 → 해당 기간 기록 조회
2. 같은 기록으로 만든 결과가 있으면 AI를 호출하지 않고 저장된 결과를 반환 (`cache_key`)
3. 사용자별·전체 호출 한도 확인
4. ChatGPT API 호출 (사용자가 쓴 글은 지시가 아닌 데이터로만 전달)
5. 응답 형식(JSON)과 근거 기록이 본인의 기록인지 서버에서 검증
6. 결과와 호출 기록 저장 후 반환

- API 키는 서버 환경변수로만 보관하며 저장소에 올리지 않습니다.
- AI가 실패하거나 한도를 넘어도 기록은 정상 저장되고, 차분한 안내 문구를 표시합니다.
- 기록이 적을 때는 억지로 분석하지 않고 안내합니다.

---

## 5. 화면 구성

- 메뉴 화면은 페이지 스크롤 없이 한 화면에 고정하고, 넘치는 내용은 상세 화면으로 나눕니다.
- 데스크톱은 상단 가로 메뉴, 모바일은 하단 탭입니다.

| 구분 | 화면 |
|---|---|
| 메뉴 5개 | 홈, 기록 작성, 나의 변화, AI 회고, 기록 모아보기 |
| 상세 화면 | 기록 상세, 주 상세, 변화 지표 상세(지표 탭: 기록한 날/시간/태그), AI 회고 상세, 이전 회고 목록 |
| 보조 화면 | 활동 분야 관리, 로그인·회원가입, 알림 설정, 개인정보 안내 |

데스크톱(폭 1024 이상)은 홈·주 상세·변화 지표 상세에서 본문 옆에 22.5rem(360px) 사이드(최근 7일, 선택한 주
요약 등)를 둡니다. 지난 기록·나의 변화·AI 회고("돌아보기" 탭 묶음)는 이 사이드 없이 컨트롤(분야·기간 선택)을
본문 위쪽에 두고, 변화·회고 탭의 보조 링크(변화 지표 상세 보기, 이전 회고 목록)는 탭 줄 오른쪽 끝에 둡니다. 다만
지난 기록(기록 탭)만 예외로, 1년 활동 지도를 본문 오른쪽의 고정 27.5rem(440px) 열(스크롤 시 고정)에 둡니다.

지난 기록은 분야별 펼침 대신 GitHub 잔디 모양의 1년 활동 지도(`ActivityHeatmap`)를 항상 보여 주고, 같은 부품을
홈(최근 12주)에도 씁니다. 홈에는 쌓인 기록 3칸(기록한 날·쌓인 시간·시작한 지 N일)과 자주 하는 주제 칩(최근 기록의
주제 태그 상위 4개, 누르면 기록 작성에 그 주제가 미리 채워짐)도 있습니다. 목록이 길어지는 화면(지난 기록 타임라인,
이전 회고 목록)은 서버의 page·size·totalCount를 쓰는 페이지네이션으로 나누되, 데스크톱에서는 고정 개수 대신
목록 영역의 실제 높이에 맞춰 개수를 계산합니다(`useFitPageSize`, 최소 2·최대 12). 홈의 아랫줄(쌓인 기록 3칸·주제
칩·활동 지도·최근 기록)도 같은 방식으로 높이가 남는 만큼만 우선순위 순서로 보여 줍니다(`useHomeLowerSectionFit`).

### 반응형 스케일

화면 크기가 달라도 비슷한 비율로 보이도록 루트 글자 크기를 화면 **폭**에 맞춰 `clamp()`로 늘리고 줄입니다
(데스크톱 1024px 이상: `clamp(15px, 1vw, 18px)` — 1280→15px, 1600→16px, 1920px 이상 18px 상한. 모바일
1024px 미만: 393px 폭에서 약 16px, 14~17px 범위). 새 화면을 만들 때는 다음을 지킵니다.

- 레이아웃·간격·폭·높이·모서리는 `rem` 단위를 쓴다(Tailwind 기본 단위가 `rem`이라 특별히 할 일은 없고, 브래킷
  임의값(`w-[300px]` 등)만 `rem`으로 쓰면 된다). 헤어라인 테두리·그림자는 `px` 그대로 둔다.
- 글자가 너무 작아지지 않게 하한을 지킨다: 보조 14px(`.text-caption-min`), 본문 16px(`.text-body-min`),
  카드 제목 17px(`.text-card-title`), 섹션 제목 20px(`.text-section-title`), 페이지 제목 28px
  (`.text-page-title`, 모바일·데스크톱 공통) — 전부 `max(rem값, px하한)`으로 정의돼 있다(`frontend/src/index.css`).
  활동 지도의 월 라벨만 예외로 12px 하한이다.
- 버튼은 `.btn-min`(모바일 44px·데스크톱 40px 하한), 그 밖의 눌러야 하는 곳(아이콘 버튼 등)은 `.tap-min`
  (모바일 44px·데스크톱 32px 하한)을 쓴다.
- 아이콘(`components/icons.tsx`의 공용 `Icon`)은 `width`/`height`를 주면 16px 기준 `rem`으로 환산해 스케일을
  따라가고, `className`의 Tailwind `h-/w-` 유틸리티로만 크기를 정하는 호출부는 그대로 둔다.
- **한계**: 이 `clamp`는 `html`의 글자 크기를 직접 정하므로, 사용자가 브라우저 기본 글자 크기를 바꿔도 이 값이
  그 설정을 덮어쓴다(접근성 트레이드오프).

### 공용 레이아웃 쉘(LAYOUT-V2)

헤더와 본문이 같은 좌우 끝선을 쓰도록 `.app-shell-container`(max-width 80rem, padding-inline 2rem, 그보다
큰 화면은 가운데 고정) 하나로 통일했습니다. 데스크톱(1024px 이상) 화면은 `components/PageLayout.tsx`의
12열 grid 배치 중 하나를 씁니다: `Split`(본문 7열:사이드 5열), `SplitWide`(5:7), `Full`(12열, 사이드 없음),
`SettingsShell`(메뉴 3열:내용 9열, 내용 최대 46rem). 사이드는 헤더 아래에서 sticky로 고정되고, 그리드 안의
카드는 내용 높이만큼만 차지한다(열 높이로 늘어나지 않음). 페이지 제목은 `PageHeader` 컴포넌트로 통일한다.
앱 껍데기는 데스크톱에서 `height: 100dvh`의 grid(헤더+본문)라 페이지 전체 세로 스크롤이 없고, 넘치는 내용은
그 영역 안에서만(`.scrollbar-thin-fade`) 스크롤된다. 설정 계열(`/settings`와 하위 화면)은 `SettingsLayout`이
감싸 데스크톱에서 메뉴와 내용을 항상 같이 보여준다(모바일은 메뉴 목록 ↔ 내용 화면 그대로).
홈(사이드=최근 7일·쌓인 기록 3칸·주제 칩·활동 지도)·지난 기록(사이드=1년 활동 지도+올해 요약)·변화·회고·
이전 회고 목록(카드 2열)·변화 지표 상세·주 상세·기록 작성/수정(사이드=주제 칩+최근 기록 3개)·기록
상세(사이드=AI 정리 영역 전체)·저장 직후(사이드=최근 7일)·개인정보 안내까지 모두 이 공용 컴포넌트로
옮겼다. 활동 지도(`ActivityHeatmap`)는 칸을 컨테이너 폭에 맞춰 줄이지 않고 rem 고정 크기(칸 0.7rem,
간격 0.2rem)로 그리며, 넘치면 카드 안에서만 가로 스크롤되고 지난 기록에는 [6개월|1년] 토글이 있다.

AI 결과에 대한 모든 피드백 줄("도움됐나요?", "맞나요?")은 공용 부품(`FeedbackRow`)을 써서 같은 방식으로 움직입니다:
맞다고 답하면 고마워요 문구 후 줄이 접히고, 아니라고 답하면(고칠 수 있는 결과는) 바로 고치기 입력이 열립니다.

로그인·가입 화면에는 카카오·구글 소셜 로그인 버튼이 있습니다(둘 다 설정값이 있을 때만 보임, 애플은 만들지 않음).

<!-- 화면 이미지: docs/home-desktop.png, docs/home-mobile.png 를 추가한 뒤 아래 주석을 해제 -->
<!-- ![홈 화면](docs/home-desktop.png) -->

---

## 6. 시스템 구조

| 구분 | 기술 | 역할 |
|---|---|---|
| 프론트엔드 | React (Vercel 배포) | 기록 입력, 꾸준함 가시화, 나의 변화, AI 회고 화면 |
| 리버스 프록시 | Nginx (HTTPS: Let's Encrypt) | HTTPS 연결 처리, 요청 전달, AI 요청 속도 제한 |
| 백엔드 | Spring Boot (Java), REST API | 인증, 기록 처리와 집계, AI 호출 관리, 결과 저장 |
| 데이터베이스 | MySQL | 사용자, 활동 분야, 기록, AI 결과, AI 호출 기록 |
| 생성형 AI | ChatGPT API | 기록 정리, 변화 해석, 한 줄 요약, 회고 |
| 배포 | Docker Compose, AWS EC2 | Nginx, 백엔드, 데이터베이스 컨테이너 실행 |

---

## 7. 데이터 설계

| 테이블 | 설명 |
|---|---|
| `users` | 회원 정보. `token_version`을 비밀번호를 바꿀 때마다 올려, 그 전에 발급된 토큰(다른 기기 포함)을 무효화한다. `email`·`password_hash`는 소셜 전용 계정이면 둘 다 NULL |
| `social_accounts` | 카카오·구글 로그인 연결 정보(`provider`, `provider_user_id`). 이메일은 저장하지 않는다 |
| `hobbies` | 활동 분야와 목표 한 줄 |
| `records` | 활동 기록 (한 일, 시간, 어려웠던 점, 느낌, 의미 있는 순간, AI 정리 결과) |
| `ai_results` | AI 결과 (WEEKLY, METRICS, COMPARE, COACH). 같은 입력이면 재사용(`cache_key`), 어떤 지시문 버전·모델이 만들었는지(`prompt_version`, `model`)와 사용자 피드백·반응(`feedback`, `reaction`)도 함께 저장. COACH는 "너무 어려워요"로 더 작게 바꾼 행동을 결과 JSON의 `simplifiedAction`에 보관(원래 행동은 그대로 둠)하고, 홈 카드를 어떻게 보여줄지는 `reaction`·`reacted_at`·생성일로 서버가 매번 계산(`cardState`: ACTIVE·CELEBRATE·RESTING·EXPIRED). `is_auto`·`viewed_at`은 주간 자동 회고와 홈 "새 회고 도착" 표시용 |
| `ai_logs` | AI 호출 기록 (한도 계산, 성공률·응답 속도 측정) |
| `notification_settings` | 사용자별 알림 시각·요일·새 회고 도착 알림 여부, 마지막 발송 날짜 |
| `push_subscriptions` | 웹 푸시 구독(기기별 endpoint·키) |

---

## 8. 개발 일정

| 기간 | 내용 |
|---|---|
| 10/28 ~ 11/3 | 기반 구축: 프로젝트 세팅, 배포 환경, 기록 CRUD, 홈, 기록 모아보기 |
| 11/4 ~ 11/8 | AI 핵심 기능(기록 정리, 나의 변화, AI 회고), 로그인, 베타 오픈 |
| 11/9 ~ 11/15 | 사용자 테스트(목표 12~15명), 설문조사, 버그 수정, 기능 개발 종료 |
| 11/16 ~ 11/18 | 발표 자료, 리허설, 최종 발표 |

### 개발 진행 현황
- [x] 프로젝트 세팅과 배포 환경 (빈 앱 배포)
- [x] 활동 분야·기록 CRUD
- [x] 홈, 기록 작성, 기록 모아보기
- [x] AI 공통 모듈 (호출, 한도, 로그, 검증)
- [x] 기록 정리
- [x] 나의 변화 (/changes, `POST /api/ai/compare`)
- [x] AI 회고 (/coach, /coach/history, `POST /api/ai/coach`), 홈 AI 카드, 주간 자동 회고
- [x] 로그인·회원가입, 계정 보안(비밀번호 정책·로그인 제한·계정 삭제)
- [x] 주 상세·변화 지표 상세(그래프), 지난 기록 활동 지도 펼침
- [x] 데스크톱 레이아웃(사이드 구성), 반응형 점검
- [x] 알림 설정·웹 푸시, 오프라인 저장, 음성 입력
- [x] 화면 다듬기(돌아보기 레이아웃, 1년 활동 지도, 페이지네이션, 홈 보완, 피드백 동작 통일)
- [x] 카카오·구글 소셜 로그인
- [ ] 베타 오픈, 사용자 테스트, 설문
- [ ] 최종 발표

---

## 9. 실행 방법

로컬 개발은 Docker 없이 진행한다. (`docker-compose.yml`은 서버 배포(EC2) 전용이다) 중요한 비밀 값(AI 키, JWT 비밀 값, DB 비밀번호)은 .env가 아니라 IntelliJ 실행 설정의 환경변수로 설정하고, 일반 설정은 .env.example을 복사해 .env로 만든다. 자세한 내용은 docs/environment-variables.md를 참고한다. (.env와 비밀 값은 저장소에 올리지 않는다)

### 1) DB (MySQL 8.0 이상, 로컬에 직접 설치)

1. 로컬에 MySQL 8.0 이상을 설치하고 실행한다.
2. `growit` 데이터베이스와 계정을 만든다. (예: `CREATE DATABASE growit CHARACTER SET utf8mb4;`, 계정 생성 후 권한 부여)
3. `cp .env.example .env` 로 복사하고 `DB_USER`, `DB_PASSWORD` 등을 로컬 MySQL 계정에 맞게 채운다.

### 2) 백엔드 (IntelliJ)

1. IntelliJ에서 `backend/` 폴더를 연다.
2. Run/Debug Configuration의 Environment variables에 `DB_PASSWORD`(위 .env와 같은 값), `JWT_SECRET`(32바이트 이상 임의 문자열, 예: `openssl rand -hex 32`), `SWAGGER_ENABLED=true`를 입력한다. `JWT_SECRET`이 없거나 짧으면 서버가 시작되지 않는다.
3. `GrowitApplication`을 실행한다. (Flyway가 DB 표를 자동으로 만든다)
4. 확인: `GET http://localhost:8080/api/health` → `{"status":"ok"}`
5. 로컬에서 http://localhost:8080/swagger-ui/index.html 에서 API를 시험할 수 있다. `SWAGGER_ENABLED`는 기본 false이고, 서버(prod)에서도 이 값으로만 켜고 끈다(기본은 꺼짐).
   `POST /api/auth/signup`으로 가입하고 `POST /api/auth/login`으로 받은 `accessToken`을 화면 오른쪽 위 **Authorize** 버튼에 `Bearer {토큰}` 형식으로 입력하면 로그인이 필요한 API도 시험할 수 있다.

### 3) 테스트 (백엔드)

`cd backend && ./gradlew test` — 테스트는 H2 기반이라 MySQL 스키마 검증은 로컬 실행으로 확인한다.

### 4) 프론트엔드

```bash
cd frontend
npm install
npm run dev
```

확인: `http://localhost:5173/login` 에서 로그인·회원가입 화면이 보인다. 로그인하지 않은 상태로 다른 주소에 들어가면 자동으로 `/login`으로 이동한다. 가입·로그인 후에는 공통 레이아웃(헤더·하단 탭바)과 함께 각 화면이 "준비 중" 안내로 보인다.

환경변수는 `frontend/.env.development`의 `VITE_API_BASE_URL` 하나뿐이며(공개 값만 둔다), 로컬 기본값은 비워 두고 Vite 프록시로 `/api` 요청을 백엔드(`http://localhost:8080`)로 전달한다. 로그인 토큰은 브라우저 `localStorage`(`growit_token`)에 저장하고 요청마다 `Authorization: Bearer` 헤더로 보낸다.

### 5) AI 설정

`AI_API_KEY`, `AI_MODEL`이 비어 있으면 AI 기능은 꺼진 상태로 서버가 정상 시작한다(호출 시 `AI_FAILED`). IntelliJ 실행 설정에 `AI_API_KEY`, `AI_MODEL`을 입력하면 켜진다. 나머지 설정(`AI_BASE_URL`, `AI_TIMEOUT_SECONDS`, `AI_MAX_OUTPUT_TOKENS`, `AI_MAX_CONCURRENCY`, `AI_USER_DAILY_LIMIT`, `AI_GLOBAL_DAILY_LIMIT`, `AI_ANALYSIS_USER_DAILY_LIMIT`, `AI_SIMPLIFY_USER_DAILY_LIMIT`, `AI_VIEW_USER_DAILY_LIMIT`, `AI_AUTO_USER_DAILY_LIMIT`)은 `.env.example`을 참고한다. 여러 명이 같이 쓰는 공용 키라 호출 한도는 낮게 시작하고, 실제 한도(과금 계획, rate limit)를 확인한 뒤 조정한다. 로그인 후 `GET /api/ai/usage`로 내 사용량을 확인할 수 있다.

### 6) 계정 보안

- **비밀번호 정책**(ASCII 문자만 허용): 8~20자 → 허용 문자(영문·숫자·`! @ # $ % ^ & * ( ) - _ = + . , ?`)만 → 영문 포함 → 숫자 포함 → 특수문자 포함, 이 순서대로 검사해 가장 먼저 걸린 이유만 안내한다. 가입(`POST /api/auth/signup`)과 비밀번호 변경(`PATCH /api/users/me/password`)이 같은 규칙(`PasswordPolicy`)을 쓰고, 로그인과 비밀번호 변경의 "현재 비밀번호" 입력은 이 규칙을 검사하지 않아 옛 정책(8~72자, 한글 허용)으로 가입한 기존 계정도 그대로 로그인·비밀번호 변경을 할 수 있다. 프론트(`lib/passwordRules.ts`)는 같은 규칙을 실시간 체크 목록으로 보여주되 입력 자체를 막지는 않는다.
- **BCrypt 비용**: `PASSWORD_BCRYPT_COST`(기본 10). 올리면 다음 로그인부터 기존 사용자의 해시도 새 비용으로 자동으로 다시 저장된다.
- **로그인 시도 제한**: 같은 이메일로 10분 안에 5번, 같은 IP로 10분 안에 30번 실패하면 5분 동안 막는다(메모리 기반, 서버 재시작하면 초기화됨). 막히면 `429 LOGIN_LOCKED`와 `Retry-After` 헤더를 돌려준다. 서버 배포에서는 Nginx도 `/api/auth/` 요청을 IP당 분당 10건(버스트 10)으로 따로 제한한다.
- **계정 삭제**: `DELETE /api/users/me {password}` — 비밀번호 확인 후 내 AI 결과·기록·활동 분야·계정을 한 번에 지운다(되돌릴 수 없음).
- 설정 메뉴(`/settings`)에 "비밀번호 바꾸기"(`/settings/password`), "계정 삭제"(`/settings/delete`), "개인정보 안내"(`/privacy`)가 있다. 가입 화면의 AI 전송 안내 문구에도 `/privacy`로 가는 "자세히 보기" 링크(새 탭)가 있다. 모든 화면의 하단 푸터는 삭제했다.
- **계정 전환 시 데이터 정리**: 로그인·가입·로그아웃·계정 삭제·TOKEN_INVALID(토큰 없음·위조·만료) 자동 로그아웃(비밀번호 변경은 제외, 비밀번호 틀림 등 그 밖의 401/403은 로그인 상태를 유지한 채 안내만 한다)마다 `lib/sessionReset.ts`가 TanStack Query 캐시를 모두 지우고, `sessionStorage`의 `growit_` 키 전부와 `localStorage`의 `growit_draft_*` 중 새 사용자 것이 아닌 항목을 지운다. 온보딩 임시 상태(`growit_onboarding_draft`)에는 사용자 id를 같이 저장해, 다른 사용자 것이면 버린다(이전에는 사용자 id 없이 저장돼 계정 전환 시 온보딩 이름칸에 이전 사용자 이름이 남는 문제가 있었다).

### 7) 알림·웹 푸시

- `WEB_PUSH_PUBLIC_KEY`·`WEB_PUSH_PRIVATE_KEY`·`WEB_PUSH_SUBJECT`(mailto:) 중 하나라도 비어 있으면 푸시 기능은
  꺼진 상태로 서버가 정상 시작한다(`GET /api/push/public-key`가 `enabled=false`). 서버 배포 시
  `scripts/generate-vapid-keys.sh`로 키를 만들어 서버 `.env`에 넣는다(이미 있으면 덮어쓰지 않음, 개인키는 화면에
  출력하지 않음). 로컬 개발에서는 보통 비워 두고 꺼진 채로 둔다.
- 1분마다(Asia/Seoul) 알림 설정이 켜진 사용자 중 지금 시각·요일이 맞고 오늘 기록이 없는 사람에게 하루 한 번 보낸다.
  단일 서버 전제(여러 인스턴스면 중복 발송 가능).
- 알림 설정 화면은 `/settings/notifications`. "알림 받기"는 사용자 클릭 안에서만 브라우저 권한을 요청한다.
- 서비스 워커(`frontend/public/sw.js`)는 `push`·`notificationclick`만 처리하고 fetch를 가로채거나 캐시를 만들지
  않는다(Vercel 배포 뒤 낡은 화면이 남지 않게).

### 8) 소셜 로그인

- `KAKAO_CLIENT_ID`·`KAKAO_CLIENT_SECRET`, `GOOGLE_CLIENT_ID`·`GOOGLE_CLIENT_SECRET` — 제공자별로 둘 다 있어야 그
  제공자가 켜진다(하나라도 비면 그 제공자는 꺼진 상태로 서버가 정상 시작, `GET /api/auth/oauth/providers`가
  `enabled=false`). 카카오는 [Kakao Developers](https://developers.kakao.com)에서 앱을 만들고 "카카오 로그인"을
  켠 뒤 Redirect URI에 `{OAUTH_REDIRECT_BASE_URL}/api/auth/oauth/kakao/callback`을 등록, REST API 키를
  `KAKAO_CLIENT_ID`로 쓴다(보안 설정에서 Client Secret도 켜서 `KAKAO_CLIENT_SECRET`에 넣는다). 구글은
  [Google Cloud Console](https://console.cloud.google.com)에서 OAuth 클라이언트(웹 애플리케이션)를 만들고 승인된
  리디렉션 URI에 `{OAUTH_REDIRECT_BASE_URL}/api/auth/oauth/google/callback`을 등록한다.
- `OAUTH_REDIRECT_BASE_URL`(서버가 외부에서 접근 가능한 공개 주소, 기본 `http://localhost:8080`)과
  `FRONTEND_BASE_URL`(로그인 성공·실패 후 되돌아갈 프론트 주소, 기본 `http://localhost:5173`)도 함께 설정한다.
- 로그인·가입 화면의 소셜 버튼은 켜진 제공자만 보인다. 애플 로그인은 만들지 않았다.
- 소셜 전용 계정(비밀번호 없음)은 설정 메뉴에 "비밀번호 바꾸기"가 보이지 않고, 계정 삭제는 비밀번호 대신
  "계정 삭제"라는 확인 문구를 입력하는 방식이다.

## 10. 폴더 구조

```text
growit/
├─ frontend/        React + Vite + TypeScript 앱 (Tailwind CSS, React Router, TanStack Query)
├─ backend/         Spring Boot 앱 (도메인별 패키지: auth, user, hobby, record, stats, ai, common. 도메인 안에서 controller/dto/entity/repository/service 등 계층별로 나눔)
├─ docs/            ERD, 구조도, 화면 이미지, 설계 문서
├─ docker-compose.yml   서버 배포(EC2)용 mysql·backend·nginx 컨테이너
├─ .env.example
├─ CLAUDE.md
└─ README.md
```

## 11. 기술 선택 이유

> 작성 예정 (Nginx, 컨테이너 MySQL, 서버에서만 AI 호출 등)

---

## 12. 서버 배포 (EC2)

> 작성 예정

- 로컬 개발은 Docker 없이 진행하고, Docker(Compose)는 AWS EC2 배포 환경에서만 사용한다.
- 구성: `mysql`(내부 전용), `backend`(내부 전용, `backend/Dockerfile`로 빌드), `nginx`(80·443 공개, `nginx/templates/default.conf.template` 기반 HTTPS 리버스 프록시).
- 서버에서 `.env.example`을 참고해 `.env`를 만들고(`DOMAIN` 포함), Let's Encrypt 인증서를 `/etc/letsencrypt`에 준비한 뒤 `docker compose up -d --build`로 띄운다.
- 확인: `https://grow-it.duckdns.org/api/health` → `{"status":"ok"}`
- 웹 푸시를 켜려면 서버에서 `./scripts/generate-vapid-keys.sh`를 실행해 `.env`에 `WEB_PUSH_PUBLIC_KEY`·
  `WEB_PUSH_PRIVATE_KEY`를 추가하고, `WEB_PUSH_SUBJECT`(mailto:)를 직접 적은 뒤 `docker compose up -d`로 backend를
  다시 띄운다.
- Swagger는 서버 `.env`의 `SWAGGER_ENABLED`로 켜고 끈다(기본 false). 켜려면 서버 `.env`에서 `SWAGGER_ENABLED=true`로 바꾸고 `docker compose up -d`로 backend를 다시 띄운다.
  켜진 상태에서 `https://grow-it.duckdns.org/swagger-ui/index.html`(화면), `https://grow-it.duckdns.org/v3/api-docs`(API 문서 JSON)로 확인한다. 꺼져 있으면 두 주소 모두 404여야 한다.

---

## 13. 백업과 복원

> 작성 예정

- `scripts/backup-db.sh`: MySQL을 mysqldump로 덤프해 gzip으로 압축하고 `~/backups/`에 저장하며, 7일이 지난 백업 파일은 자동으로 지운다.
- `scripts/restore-db.sh <백업파일.sql.gz>`: 지정한 백업 파일로 데이터베이스를 덮어쓴다. 실행 전 확인(`yes` 입력)을 거친다.
- 주기적 백업(cron 등록 등)은 추후 정리한다.

---

## 작성자

- (인공지능프로젝트[YD] / 반서현 / 20230898)
