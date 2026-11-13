import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ActivityHeatmap } from '../components/ActivityHeatmap'
import { AiLabel } from '../components/AiLabel'
import { Band } from '../components/Card'
import { CoachActionCard } from '../components/CoachResultView'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
import { WeekStrip, type WeekStripDay } from '../components/WeekStrip'
import { PlusIcon } from '../components/icons'
import { useCurrentUser } from '../hooks/useCurrentUser'
import { useHobbies } from '../hooks/useHobbies'
import { useIsDesktop } from '../hooks/useIsDesktop'
import { fetchLatestCoach, generateAutoCoach, type CoachResult } from '../lib/ai'
import { reactionLine } from '../lib/coachReaction'
import { ApiError } from '../lib/api'
import {
  addDays,
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

// 홈 아랫줄 영역의 추정 최소 높이(rem) — F3. 실제 내용을 재는 대신(useFitPageSize와 같은 방식)
// 각 영역의 대략적인 높이를 추정해 우선순위대로 넣다가 모자라면 뒤에서부터 뺀다.
const STATS_TILES_HEIGHT_REM = 5.5
const TOPIC_CHIPS_HEIGHT_REM = 4.5
const ACTIVITY_HEATMAP_HEIGHT_REM = 9
const RECENT_RECORDS_BASE_HEIGHT_REM = 6
const RECENT_RECORDS_ITEM_HEIGHT_REM = 4.5
const SECTION_GAP_REM = 1.75

/**
 * 디자인에 없는 추가(F3): 홈 아랫줄(쌓인 기록 3칸·주제 칩·활동 지도·최근 기록)을 우선순위 순으로
 * 넣다가 남은 높이가 모자라면 뒤에서부터(최근 기록 → 활동 지도 → 주제 칩 → 쌓인 기록 3칸 순으로) 뺀다.
 * 데스크톱에서만 동작하고(모바일은 페이지 스크롤을 유지), 최근 기록은 통째로 숨기지 않고 3→2→1개로
 * 먼저 줄어든다.
 */
function useHomeLowerSectionFit(enabled: boolean) {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const [plan, setPlan] = useState({ showStats: true, showTopics: true, showHeatmap: true, recentCount: 3 })

  useEffect(() => {
    if (!enabled) {
      setPlan({ showStats: true, showTopics: true, showHeatmap: true, recentCount: 3 })
      return
    }
    const el = containerRef.current
    if (!el) return
    let timeoutId: ReturnType<typeof setTimeout> | null = null

    function measure() {
      const el2 = containerRef.current
      if (!el2) return
      const rootPx = parseFloat(getComputedStyle(document.documentElement).fontSize) || 16
      const gapPx = SECTION_GAP_REM * rootPx
      const available = el2.clientHeight
      if (available <= 0) return

      let used = 0
      const showStats = available >= STATS_TILES_HEIGHT_REM * rootPx
      if (showStats) used += STATS_TILES_HEIGHT_REM * rootPx + gapPx

      const showTopics = showStats && available - used >= TOPIC_CHIPS_HEIGHT_REM * rootPx
      if (showTopics) used += TOPIC_CHIPS_HEIGHT_REM * rootPx + gapPx

      const showHeatmap = showTopics && available - used >= ACTIVITY_HEATMAP_HEIGHT_REM * rootPx
      if (showHeatmap) used += ACTIVITY_HEATMAP_HEIGHT_REM * rootPx + gapPx

      let recentCount = 0
      if (showHeatmap) {
        const remaining = available - used - RECENT_RECORDS_BASE_HEIGHT_REM * rootPx
        recentCount = Math.max(0, Math.min(3, Math.floor(remaining / (RECENT_RECORDS_ITEM_HEIGHT_REM * rootPx)) + (remaining >= 0 ? 1 : 0)))
        if (remaining < 0) recentCount = 0
      }

      setPlan({ showStats, showTopics, showHeatmap, recentCount })
    }

    const observer = new ResizeObserver(() => {
      if (timeoutId) clearTimeout(timeoutId)
      timeoutId = setTimeout(measure, 150)
    })
    observer.observe(el)
    measure()

    return () => {
      observer.disconnect()
      if (timeoutId) clearTimeout(timeoutId)
    }
  }, [enabled])

  return { containerRef, plan }
}

export function HomePage() {
  const { data: currentUser, isLoading: userLoading } = useCurrentUser()
  const summaryQuery = useQuery({ queryKey: ['stats', 'summary'], queryFn: fetchSummary })
  const [today] = useState(() => new Date())
  const [droppedMessages] = useState(() => takeDroppedOfflineMessages())
  const isDesktop = useIsDesktop()
  const { containerRef: lowerSectionRef, plan: lowerSectionPlan } = useHomeLowerSectionFit(isDesktop)
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

  return (
    <div className="flex flex-col gap-7 lg:min-h-0 lg:flex-1">
      <div className="lg:grid lg:grid-cols-[minmax(0,1fr)_22.5rem] lg:items-start lg:gap-12">
        <div className="flex flex-col gap-7">
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

          {droppedMessages.length > 0 ? (
            <Note variant="sand">
              연결이 안 될 때 기기에 저장해 뒀던 기록 {droppedMessages.length}개를 서버가 받아들이지 못해 지웠어요.
            </Note>
          ) : null}

          {state === 'first' ? <FirstUseBoard /> : null}
          {state === 'comeback' ? <ComebackBoard /> : null}
          {state === 'done' ? <DoneBoard /> : null}
          {state === 'off' ? <AiOffBoard /> : null}

          {showWeekStrip ? (
            <div className="lg:hidden">
              <RecentWeekStrip />
            </div>
          ) : null}
        </div>

        {showWeekStrip ? (
          <aside className="hidden lg:block">
            <RecentWeekStrip />
          </aside>
        ) : null}
      </div>

      {state !== 'first' ? (
        <div ref={lowerSectionRef} className="flex flex-col gap-7 lg:min-h-0 lg:flex-1 lg:overflow-hidden">
          {lowerSectionPlan.showStats ? <StatsTiles summary={summary} /> : null}
          {lowerSectionPlan.showTopics ? <TopicChips /> : null}
          {lowerSectionPlan.showHeatmap ? (
            <div className="rounded-2xl bg-surface p-5">
              <ActivityHeatmap weeks={12} />
            </div>
          ) : null}
          {lowerSectionPlan.showHeatmap && lowerSectionPlan.recentCount > 0 ? (
            <RecentRecords limit={lowerSectionPlan.recentCount} />
          ) : null}
        </div>
      ) : null}
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

/** 디자인에 없는 추가(U3): 최근 기록 30개의 주제 태그 상위 4개를 칩으로 보여 준다. */
function TopicChips() {
  const query = useQuery({ queryKey: ['records', 'topic-chips'], queryFn: () => fetchRecords({ size: 30 }) })

  if (query.isLoading || !query.data) return null

  const counts = new Map<string, number>()
  for (const record of query.data.items) {
    const tag = record.analysis?.tags[0]
    if (tag) counts.set(tag, (counts.get(tag) ?? 0) + 1)
  }
  const topTags = [...counts.entries()].sort((a, b) => b[1] - a[1]).slice(0, 4)

  if (topTags.length === 0) return null

  return (
    <div className="flex flex-col gap-2">
      <h2 className="text-card-title">자주 하는 주제</h2>
      <div className="flex flex-wrap gap-2">
        {topTags.map(([tag]) => (
          <Link
            key={tag}
            to={`/records/new?topic=${encodeURIComponent(tag)}`}
            className="inline-flex h-9 items-center rounded-md bg-tag-topic-bg px-3 text-sm font-medium text-tag-topic-fg no-underline"
          >
            {tag}
          </Link>
        ))}
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

function RecentRecords({ limit = 3 }: { limit?: number }) {
  const listQuery = useQuery({ queryKey: ['records', 'home-recent'], queryFn: () => fetchRecords({ size: 3 }) })
  const { data: hobbies } = useHobbies(true)

  if (listQuery.isLoading) {
    return <Loading />
  }

  if (listQuery.isError || !listQuery.data || listQuery.data.items.length === 0) {
    return null
  }

  const hobbyNameById = new Map((hobbies ?? []).map((hobby) => [hobby.id, hobby.name]))
  const today = todayLocalDate()
  const visibleItems = listQuery.data.items.slice(0, limit)

  return (
    <div className="flex flex-col gap-3">
      <h2 className="text-card-title">최근 기록</h2>
      <div className="flex flex-col">
        {visibleItems.map((record) => {
          const firstTag = record.analysis?.tags[0]
          const isToday = record.recordDate === today
          return (
            <Link
              key={record.id}
              to={`/records/${record.id}`}
              className="flex flex-col gap-1.5 border-t border-line py-4 text-ink no-underline last:border-b"
            >
              <span className="flex items-center justify-between gap-2">
                <span className="text-caption-min font-semibold text-ink-2">
                  {isToday ? '오늘' : formatMonthDay(record.recordDate)} · {hobbyNameById.get(record.hobbyId) ?? ''}
                </span>
                {firstTag ? (
                  <span className="inline-flex h-6 flex-none items-center rounded-md bg-tag-topic-bg px-2 text-caption-min font-medium text-tag-topic-fg">
                    {firstTag}
                  </span>
                ) : null}
              </span>
              <p className="truncate font-serif text-lg text-ink">{record.content}</p>
            </Link>
          )
        })}
      </div>
      <Link
        to="/records"
        className="inline-flex min-h-11 w-fit items-center text-sm font-medium text-base-ink underline underline-offset-4"
      >
        지난 기록 모두 보기
      </Link>
    </div>
  )
}

function RecentWeekStrip() {
  const today = todayLocalDate()
  const from = addDays(today, -6)
  const query = useQuery({
    queryKey: ['stats', 'activity-map', from, today],
    queryFn: () => fetchActivityMap({ from, to: today }),
  })

  if (query.isLoading) {
    return <Loading />
  }

  const datesWithRecord = new Set((query.data ?? []).map((entry) => entry.date))
  const days: WeekStripDay[] = Array.from({ length: 7 }, (_, index) => {
    const date = addDays(from, index)
    return { date, isToday: date === today, hasRecord: datesWithRecord.has(date) }
  })

  return <WeekStrip days={days} />
}
