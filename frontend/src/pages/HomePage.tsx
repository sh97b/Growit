import { useEffect, useState, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AiLabel } from '../components/AiLabel'
import { Band } from '../components/Card'
import { Button } from '../components/Button'
import { CoachActionCard } from '../components/CoachResultView'
import { Dropdown } from '../components/Dropdown'
import { HobbyInlineCreate } from '../components/HobbyInlineCreate'
import { HomeActivityCard } from '../components/HomeActivityCard'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
import { Split } from '../components/PageLayout'
import { RecentRecordsCard } from '../components/RecentRecordsCard'
import { RecentWeekStrip } from '../components/RecentWeekStrip'
import { TopicChips } from '../components/TopicChips'
import { ChevronRightIcon, PlusIcon } from '../components/icons'
import { useCurrentUser } from '../hooks/useCurrentUser'
import { DotCompletionHost } from '../components/DotCompletionHost'
import { useDotsCurrent } from '../hooks/useDots'
import { useHobbies } from '../hooks/useHobbies'
import { useActivityView } from '../hooks/useActivityView'
import { useIsDesktop, useMediaQuery } from '../hooks/useIsDesktop'
import { useOverflowHidePriority } from '../hooks/useOverflowHidePriority'
import { useShrinkToFit } from '../hooks/useShrinkToFit'
import { fetchLatestCoach, generateAutoCoach, type CoachAutoResponse, type CoachResult } from '../lib/ai'
import { reactionLine } from '../lib/coachReaction'
import { ApiError } from '../lib/api'
import {
  daysBetween,
  formatDurationMinutes,
  formatMonthDay,
  formatMonthDayWeekday,
  startOfWeekLocal,
  todayLocalDate,
} from '../lib/date'
import { takeDroppedOfflineMessages } from '../hooks/useOfflineSync'
import { createRecord, fetchRecords } from '../lib/records'
import { fetchActivityMap, fetchSummary, type StatsSummary } from '../lib/stats'

/** 홈 최근 기록 한 줄의 추정 높이(테두리+패딩+두 줄, rem). 남은 열 높이에 맞춰 0~3개만 보여준다. */

const primaryLinkClass =
  'inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-lg bg-base px-5 text-body-min font-medium text-white no-underline lg:w-fit'

function displayNameOf(user: { displayName: string | null; email: string | null } | undefined): string {
  if (!user) return ''
  return user.displayName || user.email?.split('@')[0] || 'Growit 회원'
}

const AUTO_COACH_DATE_KEY = 'growit_auto_coach_date'

/**
 * 홈에 들어올 때마다 서버에 자동 회고 조건(기록 5개 이상 쌓임, O1 E)을 확인한다. sessionStorage에
 * 오늘 날짜를 적어 두고 하루 한 번만 호출한다(서버도 같은 입력이면 멱등이라 이중 안전장치).
 * 응답(GENERATED/EXISTS/NOT_ELIGIBLE 등)을 돌려줘 사이드의 회고 카드가 "기록 n/5" 진행 표시에
 * 쓸 수 있게 한다 — 같은 날 두 번째 접속처럼 호출을 건너뛴 경우에는 null을 돌려주고, 그때는
 * latest 쿼리(저장된 결과)만으로 카드를 그린다(P1 5단계).
 */
function useAutoCoachOncePerDay(): CoachAutoResponse | null {
  const queryClient = useQueryClient()
  const [autoStatus, setAutoStatus] = useState<CoachAutoResponse | null>(null)

  useEffect(() => {
    const today = todayLocalDate()
    let lastCalled: string | null = null
    try {
      lastCalled = sessionStorage.getItem(AUTO_COACH_DATE_KEY)
    } catch {
      // 세션 저장소를 못 쓰면(사생활 보호 모드 등) 그냥 매번 호출한다 — 서버가 멱등이라 안전하다.
    }
    if (lastCalled === today) return

    generateAutoCoach()
      .then((data) => {
        try {
          sessionStorage.setItem(AUTO_COACH_DATE_KEY, today)
        } catch {
          // 저장 실패는 무시한다(다음 접속에서 다시 시도될 뿐).
        }
        setAutoStatus(data)
        queryClient.invalidateQueries({ queryKey: ['ai', 'coach', 'latest'] })
      })
      .catch(() => {
        // 실패해도 홈은 평소대로 보여준다. 다음 접속에서 다시 시도된다.
      })
  }, [])

  return autoStatus
}

/** 도트 카드에서 그림을 뺀 나머지(여백·토글·문구·도감 링크)의 대략적인 높이(px). 남는 높이에서 빼서 그림 높이를 정한다. */
const DOT_CARD_OVERHEAD_PX = 240
/** 잔디 보기 카드와 목표 없는 도트 카드의 대략적인 높이(px). 왼쪽 열이 넘치는지 가늠할 때 쓴다. */
const GRASS_CARD_PX = 280
const DOT_EMPTY_CARD_PX = 200
/** 웹 홈 왼쪽 열은 폭이 넓어서 도트 그림 높이 한도를 사이드(220px)보다 크게 쓴다. */
const LEFT_PICTURE_CAP_PX = 360

export function HomePage() {
  const { data: currentUser, isLoading: userLoading } = useCurrentUser()
  const summaryQuery = useQuery({ queryKey: ['stats', 'summary'], queryFn: fetchSummary })
  const [today] = useState(() => new Date())
  const [droppedMessages] = useState(() => takeDroppedOfflineMessages())
  const autoStatus = useAutoCoachOncePerDay()
  // 도트 보기를 열지 않아도 홈이 열릴 때 한 번 불러, 서버가 칠하기에 실패했던 날을 채워 두게 한다. 실패해도 홈에는 아무것도 보이지 않는다.

  // W4: 웹(폭 1024 이상)에서는 활동 지도 카드가 왼쪽 열의 "최근 기록" 아래에 있다. 왼쪽이 넘치면 최근 기록을
  // 3→2→1→0개로 먼저 줄이고, 활동 지도 카드는 마지막까지 남긴다(그림은 남는 높이에 맞추고, 칸이 6px 미만이면 요약만).
  const [activityView] = useActivityView()
  const dotsQuery = useDotsCurrent()
  const dotShown = dotsQuery.data?.goal ?? dotsQuery.data?.justCompleted ?? null
  const dotNeedPicture = activityView === 'dot' && dotShown ? dotShown.height * 8 : 0
  const leftOverhead = activityView === 'grass' ? GRASS_CARD_PX : dotShown ? DOT_CARD_OVERHEAD_PX : DOT_EMPTY_CARD_PX
  const {
    containerRef: leftFitRef,
    shrinkRef: recentRef,
    count: recentCount,
    pictureMax: leftPictureMax,
  } = useShrinkToFit({ max: 3, overhead: leftOverhead, needPicture: dotNeedPicture, pictureCap: LEFT_PICTURE_CAP_PX })

  // 아래 세 훅은 summary가 아직 로딩 중이어도(이 아래 early return보다 먼저) 매 렌더마다 똑같이
  // 호출돼야 한다(Hooks 규칙) — summary 로딩 전에는 잠정적으로 안전한 기본값으로 계산해 두고,
  // 실제 상태별 분기는 이 함수의 나머지 부분(이미 로딩이 끝난 뒤)에서 한다.
  const summaryPreview = summaryQuery.data
  const statePreview: 'first' | 'comeback' | 'done' | 'off' | null = !summaryPreview
    ? null
    : summaryPreview.recordedDays === 0
      ? 'first'
      : (summaryPreview.daysSinceLastRecord ?? 0) >= 7
        ? 'comeback'
        : summaryPreview.todayRecorded
          ? 'done'
          : 'off'

  const restedWeekQuery = useQuery({
    queryKey: ['stats', 'activity-map', 'week-check', startOfWeekLocal(today), todayLocalDate()],
    queryFn: () => fetchActivityMap({ from: startOfWeekLocal(today), to: todayLocalDate() }),
    enabled: statePreview === 'comeback',
  })

  const sideItemCount = statePreview == null || statePreview === 'first' ? 0 : 3
  const { containerRef: sideFitRef, itemRefs: sideItemRefs } = useOverflowHidePriority(sideItemCount, 1)
  // 낮은 화면(1366×768, 1280×720 같은)에서는 통계 3칸을 한 줄로 줄인다. 모바일(폭 1024 미만)은 이 값을 쓰지 않는다.
  const lowScreen = useMediaQuery('(max-height: 760px)')

  if (userLoading || summaryQuery.isLoading) {
    return <Loading />
  }

  if (summaryQuery.isError) {
    return (
      <div className="flex flex-col gap-3">
        <Note variant="sand">잠시 후 다시 시도해 주세요.</Note>
        <button
          type="button"
          onClick={() => summaryQuery.refetch()}
          className="inline-flex min-h-11 w-fit items-center text-sm font-medium text-base-ink no-underline rounded-full px-2 -mx-2 hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)] focus-visible:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]"
        >
          다시 불러오기
        </button>
      </div>
    )
  }

  if (!summaryQuery.data) {
    return <Loading />
  }

  const summary = summaryQuery.data
  const name = displayNameOf(currentUser)
  const state = statePreview!

  const showWeekStrip = state === 'done' || state === 'off'
  const restedWeek = state === 'comeback' && (restedWeekQuery.data ?? []).length === 0

  const greeting = (
    <div className="flex flex-col gap-1.5">
      <h1 className="font-serif text-[22px] font-semibold lg:text-[2rem]">
        {state === 'comeback' ? `다시 오셨네요, ${name}님` : state === 'first' ? `${name}님, 환영합니다` : `안녕하세요, ${name}님`}
      </h1>
      <span className="text-sm text-ink-2">{formatMonthDayWeekday(today)}</span>
      {state === 'first' ? (
        <p className="text-body-min text-ink-2">오늘 있었던 일을 한 줄만 적어도 첫 기록이 돼요.</p>
      ) : null}
      {/* P1 5단계: 쉬었다 돌아온 문구와 "이번 주는 쉬어 간 주예요" 안내를 한 줄로 합친다. */}
      {state === 'comeback' && summary.lastRecordedDate ? (
        <p className="text-body-min text-ink-2">
          마지막 기록은 {formatMonthDay(summary.lastRecordedDate)}이에요. 쌓아 둔 기록은 그대로 있어요.
          {restedWeek ? ' 이번 주는 쉬어 간 주예요 — 쉬는 시간도 흐름의 일부예요.' : ''}
        </p>
      ) : null}
    </div>
  )

  // P1 5단계: 사이드는 우선순위 순서(① 최근 회고 카드 ② 활동 지도 ③ 쌓인 기록 3칸 ④ 자주 하는
  // 주제)로 두고, 열이 실제로 넘칠 때만 아래 블록부터 숨긴다(useOverflowHidePriority). 최근 7일
  // 줄은 이 우선순위 목록에 없던 기존 블록이라(지시문 목록에 없음) 숨김 대상에서 빼고 그대로 맨
  // 위에 둔다 — 작아서 공간을 거의 안 차지하고, done/off 상태에서만 보이던 기존 규칙도 유지한다.
  // 배열 순서가 숨김 우선순위다(앞이 높음, 넘치면 맨 뒤부터 숨김): 최근 회고 → 통계 → 자주 하는 주제.
  // (활동 지도 카드는 W4부터 왼쪽 열로 옮겼다.)
  const sideItems: { key: string; order: number; node: ReactNode }[] =
    state === 'first'
      ? []
      : [
          { key: 'coach', order: 0, node: <HomeCoachCard comeback={state === 'comeback'} autoStatus={autoStatus} /> },
          { key: 'stats', order: 2, node: <StatsTiles summary={summary} compact={lowScreen} /> },
          { key: 'topics', order: 3, node: <TopicChips /> },
        ]

  const sideDesktop = (
    <>
      {showWeekStrip ? <RecentWeekStrip /> : null}
      <div ref={sideFitRef} className="flex flex-1 min-h-0 flex-col gap-7 overflow-hidden">
        {sideItems.map((item, index) => (
          <div
            key={item.key}
            style={{ order: item.order }}
            ref={(el) => {
              sideItemRefs.current[index] = el
            }}
          >
            {item.node}
          </div>
        ))}
      </div>
    </>
  )

  const main = (
    // 웹에서는 페이지 스크롤이 없다. 이 열의 안쪽 블록이 넘치면 아래의 숨김·줄이기 규칙이 처리하고, 열 자체는 스크롤하지 않는다.
    <div className="flex flex-col gap-5 lg:h-full lg:min-h-0 lg:overflow-hidden">
      {greeting}

      {droppedMessages.length > 0 ? (
        <Note variant="sand">
          연결이 안 될 때 기기에 저장해 뒀던 기록 {droppedMessages.length}개를 서버가 받아들이지 못해 지웠어요.
        </Note>
      ) : null}

      {/* Q1 1번: "쉬었다 돌아온" 상태에서만 보이던 한 줄 기록 입력칸을 기록이 1개 이상인 모든
          상태(기본·오늘 기록 완료·쉬었다 돌아온·AI 정리 꺼짐)에서 항상 보여준다. 기록이 0개인
          첫 사용 상태만 기존 [첫 기록 쓰기] 버튼 그대로 둔다. */}
      {state === 'first' ? <FirstUseBoard /> : <QuickRecordInput />}

      {/*
        S1 D-2단계: 모바일 순서는 인사말 → 한 줄 기록 → 최근 7일 → 최근 회고 카드 → 최근 기록
        2개 → (스크롤 아래) 쌓인 기록 3칸·활동 지도·자주 하는 주제. "한 화면에 핵심이 보이게"가
        목적이라 최근 7일은 상태와 무관하게(기존 showWeekStrip 게이트는 데스크톱 사이드 전용으로
        남겨 둔다) 항상 보여준다.
      */}
      {state !== 'first' ? (
        <>
          <div className="lg:hidden">
            <RecentWeekStrip />
          </div>
          <div className="lg:hidden">
            <HomeCoachCard comeback={state === 'comeback'} autoStatus={autoStatus} />
          </div>
          <div className="lg:hidden">
            <RecentRecordsCard limit={2} />
          </div>
          <div className="flex flex-col gap-7 lg:hidden">
            <HomeActivityCard />
            <StatsTiles summary={summary} />
            <TopicChips />
          </div>
        </>
      ) : null}

      {state !== 'first' ? (
        <div ref={leftFitRef} className="hidden lg:flex lg:min-h-0 lg:flex-1 lg:flex-col lg:gap-7 lg:overflow-hidden">
          <div ref={recentRef} className={recentCount > 0 ? 'flex-none' : 'hidden'}>
            {recentCount > 0 ? <RecentRecordsCard limit={recentCount} /> : null}
          </div>
          <div className="flex-none">
            <HomeActivityCard maxPictureHeight={leftPictureMax} />
          </div>
        </div>
      ) : null}
    </div>
  )

  return (
    <div className="flex flex-col gap-7 lg:min-h-0 lg:flex-1">
      <Split
        main={main}
        side={<div className="hidden lg:flex lg:h-full lg:min-h-0 lg:flex-col lg:gap-7">{sideDesktop}</div>}
        className="lg:min-h-0 lg:flex-1 lg:grid-rows-[minmax(0,1fr)]"
      />
      <DotCompletionHost />
    </div>
  )
}

/** 디자인에 없는 추가(U3): 연속 일수 같은 쉬면 줄어드는 지표 대신 쌓이기만 하는 지표 3개. */
function StatsTiles({ summary, compact = false }: { summary: StatsSummary; compact?: boolean }) {
  const daysSinceStart = summary.firstRecordedDate
    ? daysBetween(summary.firstRecordedDate, summary.today) + 1
    : null

  if (compact) {
    // 낮은 화면용 한 줄 카드: 값은 같고 높이와 글자만 줄인다
    return (
      <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-0.5 rounded-xl bg-surface px-3 py-2 text-caption-min text-ink-2">
        <span>
          기록한 날 <span className="font-serif text-ink">{summary.recordedDays}일</span>
        </span>
        <span>
          쌓인 시간{' '}
          <span className="font-serif text-ink">
            {summary.totalMinutes === 0 ? '기록 안 함' : formatDurationMinutes(summary.totalMinutes)}
          </span>
        </span>
        <span>
          시작한 지 <span className="font-serif text-ink">{daysSinceStart != null ? `${daysSinceStart}일` : '-'}</span>
        </span>
      </div>
    )
  }

  return (
    <div className="grid grid-cols-3 gap-3">
      <div className="flex flex-col gap-1 rounded-xl bg-surface p-4">
        <span className="text-sm text-ink-2">기록한 날</span>
        <span className="font-serif text-lg text-ink">{summary.recordedDays}일</span>
      </div>
      <div className="flex flex-col gap-1 rounded-xl bg-surface p-4">
        <span className="text-sm text-ink-2">쌓인 시간</span>
        <span className="font-serif text-lg text-ink">
          {summary.totalMinutes === 0 ? '기록 안 함' : formatDurationMinutes(summary.totalMinutes)}
        </span>
      </div>
      <div className="flex flex-col gap-1 rounded-xl bg-surface p-4">
        <span className="text-sm text-ink-2">시작한 지</span>
        <span className="font-serif text-lg text-ink">{daysSinceStart != null ? `${daysSinceStart}일` : '-'}</span>
      </div>
    </div>
  )
}

function FirstUseBoard() {
  return (
    <Band className="flex flex-col items-start gap-3.5">
      <h2 className="text-lg font-semibold">첫 기록을 남겨 볼까요?</h2>
      <Link to="/records/new" className={primaryLinkClass}>
        <PlusIcon width={18} height={18} />첫 기록 쓰기
      </Link>
    </Band>
  )
}

/**
 * 홈 사이드의 "최근 회고 카드"(P1 5단계). 전에는 '오늘 기록 완료' 상태에서만 본문 안에 보였는데,
 * 이제 상태와 무관하게 사이드 1순위 블록으로 항상 보인다(회고가 아예 없을 때는 진행 안내로 대체).
 * cardState가 EXPIRED여도 카드를 숨기지 않고 날짜·내용은 그대로 보여주되 반응 버튼만 숨긴다
 * (전에는 EXPIRED를 "회고 없음"과 똑같이 취급해 카드 전체가 "회고 받기" 안내로 바뀌었는데, 그
 * 회고 자체는 여전히 유효한 정보라 숨길 이유가 없다는 게 이번 지시의 핵심 — 서버 `latest()`는
 * 이미 EXPIRED여도 result를 그대로 돌려주고 있어 이 부분은 프론트 로직만 바꾸면 됐다).
 */
function HomeCoachCard({ comeback, autoStatus }: { comeback: boolean; autoStatus: CoachAutoResponse | null }) {
  const queryClient = useQueryClient()
  const { data, isLoading } = useQuery({ queryKey: ['ai', 'coach', 'latest'], queryFn: fetchLatestCoach })

  if (isLoading) {
    return <Loading />
  }

  const action = data ? (comeback ? data.comebackAction : data.nextAction) : null

  // 회고가 아예 없으면: 오늘 접속 때 자동 생성 조건을 이미 확인했다면(autoStatus) 정확한 진행
  // 수치를, 아니면(같은 날 두 번째 접속 등으로 건너뛴 경우) 일반 안내만 보여준다.
  if (!data || !action) {
    if (autoStatus && autoStatus.status === 'NOT_ELIGIBLE' && !autoStatus.hasAnyCoach) {
      const ratio = Math.min(100, (autoStatus.newRecordsSinceLast / Math.max(1, autoStatus.requiredCount)) * 100)
      return (
        <div className="flex flex-col items-start gap-3 rounded-2xl bg-surface p-5">
          <h2 className="text-card-title">AI 회고</h2>
          <p className="text-caption-min text-ink-2">
            기록 {autoStatus.newRecordsSinceLast}/{autoStatus.requiredCount} · {autoStatus.requiredCount}개가 쌓이면
            회고가 자동으로 도착해요
          </p>
          <div className="h-2 w-full overflow-hidden rounded-full bg-rest">
            <div className="h-full rounded-full bg-base" style={{ width: `${ratio}%` }} />
          </div>
          <Link to="/records/new" className="inline-flex min-h-9 items-center text-sm font-medium text-base-ink no-underline rounded-full px-2 -mx-2 hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)] focus-visible:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]">
            기록하기
          </Link>
        </div>
      )
    }
    return (
      <div className="flex flex-col items-start gap-2 rounded-2xl bg-surface p-5">
        <h2 className="text-card-title">AI 회고</h2>
        <p className="text-caption-min text-ink-2">아직 받은 회고가 없어요.</p>
      </div>
    )
  }

  const expired = data.cardState === 'EXPIRED'

  const newBanner = data.isNew ? (
    <div className="flex items-center gap-1.5">
      <AiLabel>새로 도착</AiLabel>
    </div>
  ) : null

  // CELEBRATE·RESTING은 행동 카드 대신 짧은 응원 문구만 보여준다(기존 동작 유지).
  const body =
    !expired && (data.cardState === 'CELEBRATE' || data.cardState === 'RESTING') ? (
      <p className="text-body-min text-ink-2">{encouragementLine(data)}</p>
    ) : (
      <CoachActionCard
        result={data}
        comeback={comeback}
        hideReactions={expired}
        onResultChange={(updated) => queryClient.setQueryData(['ai', 'coach', 'latest'], updated)}
      />
    )

  return (
    <div className="flex flex-col gap-2.5 rounded-2xl bg-surface p-5">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-card-title">
          {data.startDate ? `${formatMonthDay(data.startDate)} 회고` : 'AI 회고'}
        </h2>
        {newBanner}
      </div>
      {data.periodSummary ? <p className="line-clamp-2 text-body-min text-ink-2">{data.periodSummary}</p> : null}
      {body}
      <Link
        to={data.id != null ? `/coach?r=${data.id}` : '/coach'}
        className="inline-flex min-h-9 w-fit items-center gap-0.5 text-sm font-medium text-base-ink no-underline"
      >
        회고 보기
        <ChevronRightIcon width={14} height={14} />
      </Link>
    </div>
  )
}

function encouragementLine(data: CoachResult): string {
  if (data.cardState === 'CELEBRATE') return reactionLine('TRIED', data.id ?? 0)
  if (data.reaction === 'REST_TODAY') return reactionLine('REST_TODAY', data.id ?? 0)
  return '오늘은 여기까지면 충분해요.'
}

/**
 * Q1 1번: 모든 상태(기본·오늘 기록 완료·쉬었다 돌아온·AI 정리 꺼짐)에서 인사말 아래에 항상 보이는
 * 한 줄 기록 입력칸(이전엔 "쉬었다 돌아온" 상태에만 있던 ComebackBoard를 그대로 일반화한 것).
 * 분야 기본값은 가장 최근 기록의 분야(보관됐으면 첫 활성 분야), 분야가 둘 이상이면 입력칸 왼쪽에
 * 작은 분야 드롭다운을 둔다. 기록이 0개인 첫 사용 상태는 이 컴포넌트 대신 FirstUseBoard를 쓴다.
 */
function QuickRecordInput() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const isDesktop = useIsDesktop()
  const { data: activeHobbies, isLoading: hobbiesLoading } = useHobbies(false)
  const recentQuery = useQuery({ queryKey: ['records', 'recent'], queryFn: () => fetchRecords({ size: 1 }) })

  const [selectedHobbyId, setSelectedHobbyId] = useState<number | null>(null)
  const [content, setContent] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [addingHobby, setAddingHobby] = useState(false)

  if (hobbiesLoading || recentQuery.isLoading) {
    return <Loading />
  }

  const activeIds = new Set((activeHobbies ?? []).map((hobby) => hobby.id))
  const recentHobbyId = recentQuery.data?.items[0]?.hobbyId ?? null
  const defaultHobbyId =
    recentHobbyId != null && activeIds.has(recentHobbyId) ? recentHobbyId : (activeHobbies?.[0]?.id ?? null)
  const hobbyId = selectedHobbyId != null && activeIds.has(selectedHobbyId) ? selectedHobbyId : defaultHobbyId

  async function handleSave() {
    if (submitting || hobbyId == null) return
    const trimmed = content.trim()
    if (!trimmed) {
      setError('한 일을 입력해 주세요')
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      const created = await createRecord({ hobbyId, recordDate: todayLocalDate(), content: trimmed })
      setContent('')
      queryClient.invalidateQueries({ queryKey: ['stats'] })
      queryClient.invalidateQueries({ queryKey: ['records'] })
      queryClient.invalidateQueries({ queryKey: ['hobbies'] })
      navigate(`/records/${created.id}/saved`, { replace: true })
    } catch (saveError) {
      setError(saveError instanceof ApiError ? saveError.message : '저장하지 못했어요. 잠시 후 다시 시도해 주세요.')
    } finally {
      setSubmitting(false)
    }
  }

  // T3 2번: 분야가 1개뿐이어도 드롭다운을 항상 보여준다("분야는 ○○예요" 고정 문구는 없앤다) —
  // 목록 끝에 "+ 새 분야 추가"를 두고, 기록 작성의 HobbyInlineCreate와 같은 API(POST /api/hobbies,
  // useCreateHobby)를 그대로 재사용한다.
  const hobbyDropdown =
    hobbyId != null ? (
      <Dropdown
        pill
        aria-label="분야 선택"
        value={String(hobbyId)}
        options={(activeHobbies ?? []).map((hobby) => ({ value: String(hobby.id), label: hobby.name }))}
        onChange={(value) => setSelectedHobbyId(Number(value))}
        extra={{
          show: true,
          content: (close) =>
            addingHobby ? (
              // 팝오버 기본 min-w(9.5rem)로는 입력칸이 거의 안 보일 만큼 눌려서 폭을 넓힌다.
              <div className="min-w-[14rem]">
                <HobbyInlineCreate
                  autoFocus
                  onCreated={(hobby) => {
                    setSelectedHobbyId(hobby.id)
                    setAddingHobby(false)
                    close()
                  }}
                />
              </div>
            ) : (
              <button
                type="button"
                onClick={() => setAddingHobby(true)}
                className="flex min-h-10 w-full items-center gap-1.5 rounded-md px-3 text-left text-sm font-medium text-base-ink"
              >
                <PlusIcon width={14} height={14} className="flex-none" />
                새 분야 추가
              </button>
            ),
        }}
      />
    ) : null

  const saveButton = (
    <Button type="button" size="md" disabled={submitting} onClick={handleSave}>
      저장
    </Button>
  )

  // FIX 홈 입력칸: 모바일에서 h-14(56px) 유틸리티 클래스만으로는 화면에서 약 34px로 작게
  // 보이는 사례가 있었다(배포 지연·서비스워커 캐시로 구버전 CSS가 남았을 가능성이 유력하지만,
  // 어느 경우든 실제로 적용되게 하려고 모바일에서만 인라인 style로 높이를 직접 못 박는다).
  const inputField = (
    <input
      value={content}
      onChange={(event) => setContent(event.target.value)}
      placeholder="오늘은 무엇을 했나요?"
      aria-label="오늘 한 줄 기록"
      className="w-full min-w-0 flex-1 rounded-lg border border-input bg-white font-serif text-body-min text-ink outline-none focus:border-base lg:h-12 lg:px-3.5"
      style={
        isDesktop
          ? undefined
          : {
              height: '56px',
              minHeight: '56px',
              padding: '0 14px',
              fontSize: '16px',
              boxSizing: 'border-box',
              width: '100%',
            }
      }
    />
  )

  return (
    <>
      {hobbyId == null ? (
        <Link to="/records/new" className={primaryLinkClass}>
          <PlusIcon width={18} height={18} />
          기록하기
        </Link>
      ) : (
        <div className="flex flex-col gap-2 rounded-xl bg-surface px-3.5 py-3.5 lg:px-[1.125rem]">
          <span className="text-sm text-ink-2">오늘 한 줄만 남겨도 충분해요</span>

          {/* R1 3번: 모바일(폭 1024 미만)은 위 줄 = 입력칸 전체 폭, 아래 줄 = 분야 알약(왼쪽)과
              저장(오른쪽)으로 세로 쌓기 — 한 줄에 셋을 다 넣으면 폭 360px에서 저장 버튼 글자가
              세로로 줄바꿈되던 문제라 분리했다. 데스크톱은 기존처럼 한 줄. 입력칸을 검색창보다
              눈에 띄게 하려고 모바일에서만 높이를 h-14(56px)로 키웠다(가장자리까지 늘리면 카드
              모서리와 부딪혀 어색해져서 폭은 카드 안쪽 여백을 그대로 둔다). */}
          <div className="flex flex-col gap-2 lg:hidden">
            {inputField}
            <div className="flex items-center justify-between gap-2">
              {hobbyDropdown ?? <span />}
              {saveButton}
            </div>
          </div>
          <div className="hidden items-center gap-2 lg:flex">
            {hobbyDropdown}
            {inputField}
            {saveButton}
          </div>

          {error ? <span className="text-caption-min text-[#6B3F0F]">{error}</span> : null}
        </div>
      )}
    </>
  )
}
