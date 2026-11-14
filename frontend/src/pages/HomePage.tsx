import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ActivityHeatmap } from '../components/ActivityHeatmap'
import { AiLabel } from '../components/AiLabel'
import { Band } from '../components/Card'
import { CoachActionCard } from '../components/CoachResultView'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
import { Split } from '../components/PageLayout'
import { RecentRecordsCard } from '../components/RecentRecordsCard'
import { RecentWeekStrip } from '../components/RecentWeekStrip'
import { TopicChips } from '../components/TopicChips'
import { PlusIcon } from '../components/icons'
import { useCurrentUser } from '../hooks/useCurrentUser'
import { useHobbies } from '../hooks/useHobbies'
import { fetchLatestCoach, generateAutoCoach, type CoachResult } from '../lib/ai'
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

const primaryLinkClass =
  'inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-lg bg-base px-5 text-body-min font-medium text-white no-underline lg:w-fit'

function displayNameOf(user: { displayName: string | null; email: string | null } | undefined): string {
  if (!user) return ''
  return user.displayName || user.email?.split('@')[0] || 'Growit 회원'
}

const AUTO_COACH_DATE_KEY = 'growit_auto_coach_date'

/**
 * 그 주에 처음 접속할 때 서버가 조건에 맞으면 자동 회고를 만든다. sessionStorage에 오늘 날짜를
 * 적어 두고 하루 한 번만 호출한다(서버도 같은 주에는 멱등이라 이중 안전장치).
 */
function useAutoCoachOncePerDay() {
  const queryClient = useQueryClient()

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
      .then(() => {
        try {
          sessionStorage.setItem(AUTO_COACH_DATE_KEY, today)
        } catch {
          // 저장 실패는 무시한다(다음 접속에서 다시 시도될 뿐).
        }
        queryClient.invalidateQueries({ queryKey: ['ai', 'coach', 'latest'] })
      })
      .catch(() => {
        // 실패해도 홈은 평소대로 보여준다. 다음 접속에서 다시 시도된다.
      })
  }, [])
}

export function HomePage() {
  const { data: currentUser, isLoading: userLoading } = useCurrentUser()
  const summaryQuery = useQuery({ queryKey: ['stats', 'summary'], queryFn: fetchSummary })
  const [today] = useState(() => new Date())
  const [droppedMessages] = useState(() => takeDroppedOfflineMessages())
  useAutoCoachOncePerDay()

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
          className="inline-flex min-h-11 w-fit items-center text-sm font-medium text-base-ink underline underline-offset-4"
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

  const state: 'first' | 'comeback' | 'done' | 'off' =
    summary.recordedDays === 0
      ? 'first'
      : (summary.daysSinceLastRecord ?? 0) >= 7
        ? 'comeback'
        : summary.todayRecorded
          ? 'done'
          : 'off'

  const showWeekStrip = state === 'done' || state === 'off'

  const greeting = (
    <div className="flex flex-col gap-1.5">
      <h1 className="font-serif text-2xl font-semibold lg:text-[2rem]">
        {state === 'comeback' ? `다시 오셨네요, ${name}님` : state === 'first' ? `${name}님, 환영합니다` : `안녕하세요, ${name}님`}
      </h1>
      <span className="text-sm text-ink-2">{formatMonthDayWeekday(today)}</span>
      {state === 'first' ? (
        <p className="text-body-min text-ink-2">오늘 있었던 일을 한 줄만 적어도 첫 기록이 돼요.</p>
      ) : null}
      {state === 'comeback' && summary.lastRecordedDate ? (
        <p className="text-body-min text-ink-2">
          마지막 기록은 {formatMonthDay(summary.lastRecordedDate)}이에요. 쌓아 둔 기록은 그대로 있어요.
        </p>
      ) : null}
    </div>
  )

  // 사이드(데스크톱 Split.side)와 모바일 둘 다에서 쓰는 같은 블록 — 최근 7일·쌓인 기록 3칸·
  // 자주 하는 주제 칩·최근 26주 활동 지도(L3: 사이드 카드 폭을 채우려고 12→26으로 늘림, 디자인과
  // 다르게 한 곳). 전부 폭이 좁아도 자연스러운 크기라 항상 보여준다
  // (예전 F3의 ResizeObserver 기반 우선순위 숨김은 사이드 칼럼으로 옮기며 더 필요하지 않게 됐다).
  const sideBlocks = (
    <>
      {showWeekStrip ? <RecentWeekStrip /> : null}
      {state !== 'first' ? <StatsTiles summary={summary} /> : null}
      {state !== 'first' ? <TopicChips /> : null}
      {state !== 'first' ? (
        <div className="rounded-2xl bg-surface p-5">
          <ActivityHeatmap weeks={26} />
        </div>
      ) : null}
    </>
  )

  const main = (
    <>
      {greeting}

      {droppedMessages.length > 0 ? (
        <Note variant="sand">
          연결이 안 될 때 기기에 저장해 뒀던 기록 {droppedMessages.length}개를 서버가 받아들이지 못해 지웠어요.
        </Note>
      ) : null}

      {state === 'first' ? <FirstUseBoard /> : null}
      {state === 'comeback' ? <ComebackBoard /> : null}
      {state === 'done' ? <DoneBoard /> : null}
      {state === 'off' ? <AiOffBoard /> : null}

      {/* 사이드에 들어가는 최근 7일·쌓인 기록·주제 칩·활동 지도는 모바일에서 여기 순서대로 보인다. */}
      <div className="flex flex-col gap-7 lg:hidden">{sideBlocks}</div>

      {state !== 'first' ? <RecentRecordsCard limit={3} /> : null}
    </>
  )

  return (
    <div className="flex flex-col gap-7 lg:min-h-0 lg:flex-1">
      <Split main={main} side={<div className="hidden lg:flex lg:flex-col lg:gap-7">{sideBlocks}</div>} />
    </div>
  )
}

/** 디자인에 없는 추가(U3): 연속 일수 같은 쉬면 줄어드는 지표 대신 쌓이기만 하는 지표 3개. */
function StatsTiles({ summary }: { summary: StatsSummary }) {
  const daysSinceStart = summary.firstRecordedDate
    ? daysBetween(summary.firstRecordedDate, summary.today) + 1
    : null

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

function AiOffBoard() {
  return (
    <Band className="flex flex-col items-start gap-3.5">
      <h2 className="text-lg font-semibold">오늘 한 줄 남겨 볼까요?</h2>
      <Link to="/records/new" className={primaryLinkClass}>
        <PlusIcon width={18} height={18} />기록하기
      </Link>
    </Band>
  )
}

function DoneBoard() {
  return <HomeAiCard comeback={false} />
}

/**
 * 최신 COACH 결과가 있으면 "다음 작은 행동"(쉬었다 돌아온 경우 복귀 행동) 카드를, 없으면 회고 받기
 * 버튼만 보여준다. 홈에서는 비용 보호를 위해 AI를 새로 부르지 않고 저장된 최신 결과만 읽는다.
 */
function HomeAiCard({ comeback }: { comeback: boolean }) {
  const queryClient = useQueryClient()
  const { data, isLoading } = useQuery({ queryKey: ['ai', 'coach', 'latest'], queryFn: fetchLatestCoach })

  if (isLoading) {
    return <Loading />
  }

  const action = data ? (comeback ? data.comebackAction : data.nextAction) : null
  const newBanner = data?.isNew ? (
    <div className="flex items-center justify-between gap-3 rounded-xl bg-[color-mix(in_srgb,var(--color-base)_9%,white)] px-4 py-2.5">
      <AiLabel>새 회고가 도착했어요</AiLabel>
      <Link
        to="/coach"
        className="inline-flex min-h-9 items-center justify-center rounded-lg border border-line-2 bg-white px-4 text-sm font-medium text-ink no-underline"
      >
        열어 보기
      </Link>
    </div>
  ) : null

  // EXPIRED(7일 지남 또는 "해봤어요"가 어제 이전)는 새 회고를 받도록 안내한다.
  if (!data || !action || data.cardState === 'EXPIRED') {
    return (
      <>
        {newBanner}
        <Band className="flex flex-col items-start gap-3.5">
          <h2 className="text-lg font-semibold">AI 회고</h2>
          <Link to="/coach" className={primaryLinkClass}>
            회고 받기
          </Link>
        </Band>
      </>
    )
  }

  // CELEBRATE·RESTING은 카드와 반응 버튼을 접고 짧은 응원 문구만 보여준다.
  if (data.cardState === 'CELEBRATE' || data.cardState === 'RESTING') {
    return (
      <>
        {newBanner}
        <p className="text-body-min text-ink-2">{encouragementLine(data)}</p>
      </>
    )
  }

  return (
    <div className="flex flex-col gap-2">
      {newBanner}
      {!comeback && data.periodSummary ? <p className="text-sm text-ink-2">{data.periodSummary}</p> : null}
      <CoachActionCard
        result={data}
        comeback={comeback}
        onResultChange={(updated) => queryClient.setQueryData(['ai', 'coach', 'latest'], updated)}
      />
    </div>
  )
}

function encouragementLine(data: CoachResult): string {
  if (data.cardState === 'CELEBRATE') return reactionLine('TRIED', data.id ?? 0)
  if (data.reaction === 'REST_TODAY') return reactionLine('REST_TODAY', data.id ?? 0)
  return '오늘은 여기까지면 충분해요.'
}

function ComebackBoard() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: activeHobbies, isLoading: hobbiesLoading } = useHobbies(false)
  const recentQuery = useQuery({ queryKey: ['records', 'recent'], queryFn: () => fetchRecords({ size: 1 }) })

  const [content, setContent] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [now] = useState(() => new Date())

  const today = todayLocalDate()
  const weekStart = startOfWeekLocal(now)
  const weekCheckQuery = useQuery({
    queryKey: ['stats', 'activity-map', 'week-check', weekStart, today],
    queryFn: () => fetchActivityMap({ from: weekStart, to: today }),
  })

  if (hobbiesLoading || recentQuery.isLoading) {
    return <Loading />
  }

  const activeIds = new Set((activeHobbies ?? []).map((hobby) => hobby.id))
  const recentHobbyId = recentQuery.data?.items[0]?.hobbyId ?? null
  const hobbyId =
    recentHobbyId != null && activeIds.has(recentHobbyId) ? recentHobbyId : (activeHobbies?.[0]?.id ?? null)
  const hobbyName = activeHobbies?.find((hobby) => hobby.id === hobbyId)?.name ?? ''
  const restedWeek = (weekCheckQuery.data ?? []).length === 0

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

  return (
    <>
      {hobbyId == null ? (
        <Link to="/records/new" className={primaryLinkClass}>
          <PlusIcon width={18} height={18} />
          기록하기
        </Link>
      ) : (
        <div className="flex flex-col gap-2 rounded-xl bg-surface px-[1.125rem] py-3.5">
          <label htmlFor="quick-save" className="text-sm text-ink-2">
            오늘 한 줄만 남겨도 충분해요 · 분야는 {hobbyName}예요
          </label>
          <div className="flex gap-2">
            <input
              id="quick-save"
              value={content}
              onChange={(event) => setContent(event.target.value)}
              placeholder="오늘은 무엇을 했나요?"
              className="h-12 flex-1 rounded-lg border border-input bg-white px-3.5 font-serif text-body-min text-ink outline-none focus:border-base"
            />
            <button
              type="button"
              disabled={submitting}
              onClick={handleSave}
              className="inline-flex min-h-11 items-center justify-center rounded-lg bg-base px-5 text-body-min font-medium text-white disabled:bg-line disabled:text-[#5F594F]"
            >
              저장
            </button>
          </div>
          {error ? <span className="text-caption-min text-[#6B3F0F]">{error}</span> : null}
        </div>
      )}

      <HomeAiCard comeback />

      {restedWeek ? <Note variant="calm">이번 주는 쉬어 간 주예요. 쉬는 시간도 흐름의 일부예요.</Note> : null}
    </>
  )
}
