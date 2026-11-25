import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AiLabel } from '../components/AiLabel'
import { Spinner } from '../components/AiProgress'
import { AiViewCard } from '../components/AiViewCard'
import { InfoTip } from '../components/InfoTip'
import { CompareInsights } from '../components/CompareInsights'
import { Button } from '../components/Button'
import { ChartIcon, ChevronRightIcon } from '../components/icons'
import { FloatingPanel } from '../components/FloatingPanel'
import { Dialog } from '../components/Dialog'
import { Dropdown } from '../components/Dropdown'
import { FeedbackRow } from '../components/FeedbackRow'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
import { ReviewHeaderRow } from '../components/ReviewHeaderRow'
import { ReviewTabs } from '../components/ReviewTabs'
import { ReviewToolbar } from '../components/ReviewToolbar'
import { Split } from '../components/PageLayout'
import { RecordPairCards } from '../components/RecordPairCards'
import { Tag } from '../components/Tag'
import { useCurrentUser } from '../hooks/useCurrentUser'
import { useHobbies } from '../hooks/useHobbies'
import { useIsDesktop } from '../hooks/useIsDesktop'
import { usePersistedOpen } from '../hooks/usePersistedOpen'
import { ApiError } from '../lib/api'
import {
  fetchAiUsage,
  fetchCompare,
  fetchCompareBasis,
  fetchMetricsInterpretation,
  submitAiResultFeedback,
  type AiViewResult,
  type CompareResult,
  isStructuredCompare,
  type CompareTo,
} from '../lib/ai'
import { addDays, formatDurationMinutes, formatMonthDay, startOfWeekLocal, todayLocalDate } from '../lib/date'
import { fetchRecords } from '../lib/records'
import { fetchWeeks, type WeekSummary } from '../lib/stats'

const PERIODS: { value: CompareTo; label: string }[] = [
  { value: '1W', label: '1주 전' },
  { value: '2W', label: '2주 전' },
  { value: '1M', label: '한 달 전' },
  { value: '3M', label: '3개월 전' },
]

const WEEK_COUNT = 12
/** x축 라벨 간격(주 수가 많을수록 넓게 건너뛰어 겹치지 않게 한다). 선택한 주는 항상 보인다. */
const LABEL_STEP = WEEK_COUNT > 8 ? 3 : WEEK_COUNT > 5 ? 2 : 1

type Metric = 'days' | 'minutes' | 'tags'

const METRIC_TABS: { value: Metric; label: string }[] = [
  { value: 'days', label: '기록한 날' },
  { value: 'minutes', label: '시간' },
  { value: 'tags', label: '태그' },
]

/** "M/D" 형식(지시문 4). formatMonthDay의 "8월 24일"과 달리 그래프 x축 전용으로 짧게 쓴다. */
function formatSlashDate(isoDate: string): string {
  const [, month, day] = isoDate.split('-')
  return `${Number(month)}/${Number(day)}`
}

/** AI 해석 문장의 첫 문장만 잘라 접힌 상태의 미리보기로 쓴다. */
function firstSentence(text: string | null): string {
  if (!text) return ''
  const idx = text.indexOf('. ')
  if (idx === -1) return text
  return text.slice(0, idx + 1)
}

export function ChangesPage() {
  const { data: currentUser, isLoading: userLoading } = useCurrentUser()
  const { data: activeHobbies, isLoading: hobbiesLoading } = useHobbies(false)
  const [hobbyId, setHobbyId] = useState<number | null>(null)
  // O1(B.1): 기본 기간은 1주 전("변화 보기" 버튼 없이 페이지 진입과 동시에 바로 비교를 불러온다).
  const [compareTo, setCompareTo] = useState<CompareTo>('1W')
  const [feedbackError, setFeedbackError] = useState<string | null>(null)
  const [metric, setMetric] = useState<Metric>('days')
  const [selectedWeekStart, setSelectedWeekStart] = useState<string | null>(null)
  const [metricsOpen, setMetricsOpen] = useState(false)
  // 웹: 긴 내용(변화 해석 전체·주 요약·지표 해석)은 인라인으로 펼치지 않고 Dialog로 보여 준다.
  const [dialog, setDialog] = useState<null | 'interpretation' | 'week' | 'metrics'>(null)
  // 주별 흐름은 기본 화면에서 빼고, 웹은 떠 있는 작은 패널(열림·위치를 기억), 모바일은 아래에서 올라오는 시트로 보여 준다.
  const [flowPanelOpen, setFlowPanelOpen] = usePersistedOpen('growit.changes.weeklyFlow')
  const [flowSheetOpen, setFlowSheetOpen] = useState(false)
  const isDesktop = useIsDesktop()
  const queryClient = useQueryClient()

  // 기본 분야 = 가장 최근에 기록을 쓴 분야(지시문 B.1). 사용자가 직접 고르면 그 선택을 우선한다.
  const recentRecordQuery = useQuery({ queryKey: ['records', 'recent'], queryFn: () => fetchRecords({ size: 1 }) })
  const recentHobbyId = recentRecordQuery.data?.items[0]?.hobbyId ?? null
  const hobby =
    activeHobbies?.find((h) => h.id === hobbyId) ??
    activeHobbies?.find((h) => h.id === recentHobbyId) ??
    activeHobbies?.[0] ??
    null
  const effectiveHobbyId = hobby?.id ?? null

  const compareKey = ['ai', 'compare', effectiveHobbyId, compareTo]
  const compareQuery = useQuery({
    queryKey: compareKey,
    queryFn: () => fetchCompare({ hobbyId: effectiveHobbyId!, compareTo }),
    enabled: effectiveHobbyId != null,
    retry: false,
  })
  const result = compareQuery.data ?? null

  // 이전·최근 카드와 숫자 칩은 AI와 무관한 기본 데이터라 해석(AI)보다 먼저, 따로 불러 즉시 그린다.
  const basisQuery = useQuery({
    queryKey: ['compare', 'basis', effectiveHobbyId, compareTo],
    queryFn: () => fetchCompareBasis({ hobbyId: effectiveHobbyId!, compareTo }),
    enabled: effectiveHobbyId != null,
    retry: false,
  })
  const basis = basisQuery.data && !basisQuery.data.locked ? basisQuery.data : null

  const feedbackMutation = useMutation({
    mutationFn: ({ id, value }: { id: number; value: 'HELPFUL' | 'NOT_HELPFUL' }) =>
      submitAiResultFeedback<CompareResult>(id, value),
  })

  const usageQuery = useQuery({ queryKey: ['ai', 'usage'], queryFn: fetchAiUsage })

  const currentWeekStart = startOfWeekLocal(new Date())
  const weeksFrom = addDays(currentWeekStart, -7 * (WEEK_COUNT - 1))
  const weeksTo = todayLocalDate()

  const weeksQuery = useQuery({
    queryKey: ['stats', 'weeks', weeksFrom, weeksTo, effectiveHobbyId],
    queryFn: () => fetchWeeks({ from: weeksFrom, to: weeksTo, hobbyId: effectiveHobbyId ?? undefined }),
    enabled: effectiveHobbyId != null,
  })

  const weeks = weeksQuery.data ?? []
  const metricsKey = ['ai', 'metrics', weeksFrom, weeksTo, effectiveHobbyId]
  const metricsQuery = useQuery({
    queryKey: metricsKey,
    queryFn: () => fetchMetricsInterpretation({ hobbyId: effectiveHobbyId ?? undefined, from: weeksFrom, to: weeksTo }),
    enabled: weeksQuery.isSuccess,
    retry: false,
  })

  const maxMinutes = Math.max(1, ...weeks.map((w) => w.totalMinutes))
  const topTags = useMemo(() => {
    const counts = new Map<string, number>()
    for (const week of weeks) {
      for (const tag of week.tags) {
        counts.set(tag.tag, (counts.get(tag.tag) ?? 0) + tag.count)
      }
    }
    return [...counts.entries()].sort((a, b) => b[1] - a[1])
  }, [weeks])
  // 기본 선택 = 기록이 있는 가장 최근 주(기록이 하나도 없으면 이번 주).
  const defaultWeekStart = useMemo(() => {
    for (let i = weeks.length - 1; i >= 0; i -= 1) {
      if (weeks[i].recordedDays > 0) return weeks[i].weekStart
    }
    return weeks[weeks.length - 1]?.weekStart
  }, [weeks])
  const effectiveWeekStart = selectedWeekStart ?? defaultWeekStart
  const selectedWeek: WeekSummary | undefined = useMemo(
    () => weeks.find((w) => w.weekStart === effectiveWeekStart),
    [weeks, effectiveWeekStart],
  )
  const selectedWeekIndex = weeks.findIndex((w) => w.weekStart === effectiveWeekStart)
  // O1(B.2): 기록이 없는 주는 아래 글자 목록에서 아예 빼고, 그래프 막대만 기준선으로 보여준다.
  const recordedWeeks = useMemo(() => weeks.filter((w) => w.recordedDays > 0), [weeks])

  useEffect(() => {
    setSelectedWeekStart(null)
  }, [effectiveHobbyId])

  if (userLoading || hobbiesLoading) {
    return <Loading />
  }

  if (!currentUser?.aiEnabled) {
    return (
      <div className="flex flex-col gap-4">
        <ReviewHeaderRow title="나의 변화" />
        <Note variant="calm">
          <span>
            AI 정리가 꺼져 있어요. 켜면 기록을 비교해 달라진 점을 보여드려요.{' '}
            <Link to="/settings/ai" className="font-medium text-base-ink no-underline rounded-full px-2 -mx-2 hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)] focus-visible:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]">
              켜기
            </Link>
          </span>
        </Note>
      </div>
    )
  }

  if (!activeHobbies || activeHobbies.length === 0) {
    return (
      <div className="flex flex-col gap-4">
        <ReviewHeaderRow title="나의 변화" />
        <Note variant="calm">활동 분야를 먼저 만들어 주세요.</Note>
      </div>
    )
  }

  function handleFeedback(value: 'HELPFUL' | 'NOT_HELPFUL') {
    if (!result?.id || feedbackMutation.isPending) return
    setFeedbackError(null)
    const previous = result
    queryClient.setQueryData<CompareResult>(compareKey, (prev) => (prev ? { ...prev, feedback: value } : prev))
    feedbackMutation.mutate(
      { id: result.id, value },
      {
        onError: (mutationError) => {
          queryClient.setQueryData<CompareResult>(compareKey, previous)
          setFeedbackError(mutationError instanceof ApiError ? mutationError.message : '잠시 후 다시 시도해 주세요')
        },
      },
    )
  }

  const hobbyOptions = activeHobbies.map((h) => ({ value: String(h.id), label: h.name }))
  const periodOptions = PERIODS.map((p) => ({ value: p.value, label: p.label }))

  // P1 3단계: 2행 = 왼쪽 안내 글자(결과가 있으면 기준일 안내, 없으면 비움) + 오른쪽 분야·기간
  // 드롭다운(기록·회고 탭과 같은 위치·모양). 남은 횟수는 1행 오른쪽(머리줄 액션)으로 옮긴다.
  const infoLine =
    basis && basis.anchoredToLatest
      ? `마지막 기록(${basis.currentDate ? formatMonthDay(basis.currentDate) : ''})을 기준으로 비교했어요`
      : basis && basis.pastIsOldest
        ? `${PERIODS.find((p) => p.value === compareTo)?.label ?? '이전'} 기록이 아직 없어서 가장 오래된 기록${basis.pastDate ? ` (${formatMonthDay(basis.pastDate)})` : ''}과 비교했어요`
        : ''

  const toolbarRow = (
    <ReviewToolbar
      left={
        <>
          <Dropdown
            aria-label="분야"
            value={String(effectiveHobbyId ?? activeHobbies[0]?.id ?? '')}
            options={hobbyOptions}
            onChange={(value) => setHobbyId(Number(value))}
          />
          <Dropdown
            aria-label="비교 기간"
            value={compareTo}
            options={periodOptions}
            onChange={(value) => setCompareTo(value)}
          />
        </>
      }
      right=""
    />
  )

  const headerAction = usageQuery.data ? (
    <span className="text-caption-min text-ink-2">오늘 {usageQuery.data.remaining}회 남음</span>
  ) : null

  const showResult = !compareQuery.isFetching && !!result && !result.locked

  // ① 이전·최근 두 장. 결과를 기다리는 동안에는 같은 자리에 같은 크기의 자리표시를 둔다.
  const pairRow =
    basis ? (
      <RecordPairCards
        left={
          basis.previous
            ? { id: basis.previous.id, label: `${basis.pastDate ? formatMonthDay(basis.pastDate) : ''} 전후`, content: basis.previous.content }
            : null
        }
        right={
          basis.latest
            ? { id: basis.latest.id, label: `최근 · ${basis.currentDate ? formatMonthDay(basis.currentDate) : ''}`, content: basis.latest.content }
            : null
        }
      />
    ) : (
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2" aria-hidden="true">
        <div className="h-[132px] rounded-xl bg-surface motion-safe:animate-pulse" />
        <div className="h-[132px] rounded-xl bg-surface motion-safe:animate-pulse" />
      </div>
    )

  // ③ 숫자 비교 칩(서버가 계산한 값만 쓴다). 값은 줄바꿈하고 말줄임으로 자르지 않는다.
  const chip = (label: string, body: ReactNode) => (
    <div className="flex min-w-0 flex-col justify-center gap-1 rounded-xl bg-surface p-3">
      <span className="t-sub text-ink-2">{label}</span>
      {body}
    </div>
  )
  const metricChips = (() => {
    if (basisQuery.isLoading) {
      return (
        <div className="grid grid-cols-1 gap-2 lg:grid-cols-3" aria-hidden="true">
          {[0, 1, 2].map((index) => (
            <div key={index} className="h-[84px] rounded-xl bg-surface motion-safe:animate-pulse" />
          ))}
        </div>
      )
    }
    if (!basis?.numbers) return null
    const m = basis.numbers
    const minutesText =
      m.totalMinutes.past === 0 && m.totalMinutes.current === 0
        ? '기록 안 함'
        : `${m.totalMinutes.past === 0 ? '0분' : formatDurationMinutes(m.totalMinutes.past)} → ${m.totalMinutes.current === 0 ? '0분' : formatDurationMinutes(m.totalMinutes.current)}`
    return (
      <div className="grid grid-cols-1 gap-2 lg:grid-cols-3" aria-label="비교 숫자">
        {chip('기록한 날', <span className="t-key break-words text-ink">{m.recordedDays.past}일 → {m.recordedDays.current}일</span>)}
        {chip('쌓인 시간', <span className="t-key break-words text-ink">{minutesText}</span>)}
        {chip(
          '자주 나온 주제',
          m.topTags && m.topTags.length > 0 ? (
            <div className="flex flex-wrap gap-1">
              {m.topTags.slice(0, 2).map((topic) => (
                <Tag key={topic.tag} variant="topic">
                  {topic.tag} · {topic.count}
                </Tag>
              ))}
            </div>
          ) : (
            <span className="t-body text-ink-2">없어요</span>
          ),
        )}
      </div>
    )
  })()

  // ② 변화 해석 카드(웹·모바일 공용). 오른쪽 위에는 비교 기준을 알려 주는 ⓘ만 둔다.
  const interpretationCard = (
    <section
      aria-label="변화 해석"
      className="relative flex min-h-0 min-w-0 flex-col gap-3 overflow-hidden rounded-2xl border border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_9%,white)] p-4 lg:flex-[0_1_auto] lg:p-5"
    >
      <div className="flex flex-none items-center justify-between gap-2">
        <AiLabel>변화 해석</AiLabel>
        <div className="flex items-center gap-1">
          {isDesktop && showResult ? (
            <button
              type="button"
              onClick={() => setDialog('interpretation')}
              className="t-sub inline-flex h-8 flex-none items-center rounded-full px-2 font-medium text-base-ink hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]"
            >
              전체 보기
            </button>
          ) : null}
          {infoLine ? <InfoTip text={infoLine} /> : null}
        </div>
      </div>

      {compareQuery.isFetching ? (
        <div role="status" aria-live="polite" className="flex flex-col gap-3 lg:min-h-[200px]">
          <p className="t-body-lg flex items-center gap-2.5 font-medium text-ink">
            <Spinner className="text-base-ink" />
            AI가 기록을 읽고 있어요
          </p>
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
            {[0, 1, 2].map((index) => (
              <div key={index} className="flex flex-col gap-2.5 rounded-xl bg-white/60 p-3">
                <div className="h-4 w-1/2 rounded-full bg-rest motion-safe:animate-pulse" />
                <div className="h-4 w-full rounded-full bg-rest motion-safe:animate-pulse" />
                <div className="h-4 w-4/5 rounded-full bg-rest motion-safe:animate-pulse" />
              </div>
            ))}
          </div>
        </div>
      ) : null}

      {showResult && result ? (
        <>
          <div className={isDesktop ? 'ch-fade-auto' : ''}>
            {isStructuredCompare(result) ? (
              <CompareInsights result={result} columns={isDesktop} />
            ) : (
              <p className="t-body-lg max-w-[70ch] break-words font-serif text-ink">{result.interpretation}</p>
            )}
          </div>
          <div className="flex-none">
            <FeedbackRow
              variant="text"
              question="이 해석이 맞나요?"
              status={result.feedback === 'HELPFUL' ? 'positive' : result.feedback === 'NOT_HELPFUL' ? 'negative' : null}
              pending={feedbackMutation.isPending}
              onPositive={() => handleFeedback('HELPFUL')}
              onNegative={() => handleFeedback('NOT_HELPFUL')}
              errorMessage={feedbackError}
            />
          </div>
        </>
      ) : null}
    </section>
  )

  // O1(B.1): [변화 보기] 버튼 없이 페이지 진입·분야·기간 변경 즉시 자동으로 부른다. 불러오는 동안은
  // 카드 모양 스켈레톤을 보여주고(isFetching, 초기 로딩·분야/기간 변경 둘 다 포함) 왼쪽을 비워 두지 않는다.
  const comparison = (
    <div className="flex min-w-0 flex-col gap-5 lg:gap-2">
      {!compareQuery.isFetching && compareQuery.isError ? (
        <div className="flex flex-col gap-3">
          <Note variant="sand">
            {compareQuery.error instanceof ApiError ? compareQuery.error.message : '잠시 후 다시 시도해 주세요'}
          </Note>
          <button
            type="button"
            onClick={() => compareQuery.refetch()}
            className="inline-flex min-h-11 w-fit items-center t-sub font-medium text-base-ink no-underline rounded-full px-2 -mx-2 hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)] focus-visible:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]"
          >
            다시 시도
          </button>
        </div>
      ) : null}

      {!compareQuery.isFetching && result?.locked && result.lockReason !== 'NO_PAST_RECORD' ? (
        <div className="flex flex-col items-start gap-3 rounded-2xl bg-surface p-7">
          <p className="text-ink">기록이 3개 쌓이면 비교가 열려요</p>
          <p className="t-sub font-medium text-ink-2">{result.currentCount ?? 0}/{result.requiredCount ?? 3}</p>
          <Link to="/records/new" className="inline-flex min-h-11 items-center t-sub font-medium text-base-ink no-underline rounded-full px-2 -mx-2 hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)] focus-visible:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]">
            기록하기
          </Link>
        </div>
      ) : null}

      {!compareQuery.isFetching && result?.locked && result.lockReason === 'NO_PAST_RECORD' ? (
        <div className="flex flex-col items-start gap-3 rounded-2xl bg-surface p-7">
          <p className="text-ink">비교할 이전 기록이 아직 없어요. 기록이 며칠 더 쌓이면 비교가 열려요</p>
          <p className="t-sub text-ink-2">
            {result.oldestRecordDate ? `가장 오래된 기록 ${formatMonthDay(result.oldestRecordDate)}` : ''}
            {result.oldestRecordDate && result.latestRecordDate ? ' · ' : ''}
            {result.latestRecordDate ? `최근 기록 ${formatMonthDay(result.latestRecordDate)}` : ''}
          </p>
          <Link to="/records/new" className="inline-flex min-h-11 items-center t-sub font-medium text-base-ink no-underline rounded-full px-2 -mx-2 hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)] focus-visible:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]">
            기록하기
          </Link>
        </div>
      ) : null}

      {!isDesktop && (compareQuery.isFetching || showResult || basis) ? (
        <div className="flex min-w-0 flex-col gap-4">
          {basisQuery.isLoading || basis ? pairRow : null}
          {interpretationCard}
          {metricChips}
        </div>
      ) : null}
    </div>
  )

  const selectedWeekSummary = selectedWeek ? (
    <div className="flex flex-col gap-1.5 rounded-2xl bg-surface p-4">
      <span className="t-sub font-semibold text-ink">
        {/* O1(B.2): 한 줄로 쓰고 "주 0일" 같은 붙임 표기를 쓰지 않는다. */}
        {formatMonthDay(selectedWeek.weekStart)} – {formatMonthDay(addDays(selectedWeek.weekStart, 6))} ·{' '}
        {selectedWeek.recordedDays > 0 ? `${selectedWeek.recordedDays}일` : '기록 없음'}
      </span>
      {selectedWeek.recordedDays > 0 ? (
        <span className="tabular-nums t-sub text-ink-2">{formatDurationMinutes(selectedWeek.totalMinutes)}</span>
      ) : null}
      <Link to={`/weeks/${selectedWeek.weekStart}`} className="inline-flex min-h-9 w-fit items-center gap-1 t-sub font-medium text-base-ink no-underline rounded-full px-2 -mx-2 hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)] focus-visible:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]">
        주 상세 보기
        <ChevronRightIcon width={16} height={16} />
      </Link>
    </div>
  ) : null

  const metricsInterpretation = (() => {
    if (metricsQuery.isLoading || metricsQuery.isError || !metricsQuery.data || metricsQuery.data.locked) {
      return (
        <AiViewCard
          label="지표 해석"
          question="해석이 맞나요?"
          isLoading={metricsQuery.isLoading}
          isError={metricsQuery.isError}
          error={metricsQuery.error}
          data={metricsQuery.data}
          onRetry={() => metricsQuery.refetch()}
          onChange={(updated: AiViewResult) => queryClient.setQueryData(metricsKey, updated)}
          lockedNote={`기록이 있는 주가 ${metricsQuery.data?.requiredWeeks ?? 2}주 모이면 해석이 열려요 (${metricsQuery.data?.currentWeeks ?? 0}/${metricsQuery.data?.requiredWeeks ?? 2})`}
        />
      )
    }
    if (metricsQuery.data.empty) {
      return <Note variant="calm">이번 주는 쉬어 간 주예요.</Note>
    }
    // 지표 해석은 기본 접힘(지시문 3) — 1440×900 기준으로 스크롤이 없게 하기 위해서다.
    if (!metricsOpen) {
      return (
        <button
          type="button"
          onClick={() => setMetricsOpen(true)}
          className="flex w-full min-w-0 flex-col items-start gap-1 rounded-xl bg-surface p-4 text-left"
        >
          <span className="t-sub font-medium text-ink">지표 해석 보기 ▾</span>
          <span className="w-full min-w-0 truncate t-sub text-ink-2">{firstSentence(metricsQuery.data.displayText)}</span>
        </button>
      )
    }
    return (
      <div className="flex flex-col gap-2">
        <button
          type="button"
          onClick={() => setMetricsOpen(false)}
          className="inline-flex min-h-9 w-fit items-center t-sub font-medium text-ink-2"
        >
          지표 해석 접기 ▴
        </button>
        <AiViewCard
          label="지표 해석"
          question="해석이 맞나요?"
          isLoading={metricsQuery.isLoading}
          isError={metricsQuery.isError}
          error={metricsQuery.error}
          data={metricsQuery.data}
          onRetry={() => metricsQuery.refetch()}
          onChange={(updated: AiViewResult) => queryClient.setQueryData(metricsKey, updated)}
        />
      </div>
    )
  })()

  // 모바일: 주 요약과 지표 해석은 비교 카드 아래에 두고, 주별 흐름 차트는 시트로 본다.
  const mobileExtras = (
    <div className="flex flex-col gap-4">
      {selectedWeekSummary}
      {metricsInterpretation}
    </div>
  )

  // ---- 웹(폭 1024 이상): 탭 줄·분야·기간·남은 횟수를 한 줄로 합치고, 두 열은 스크롤 없이 밀도로 맞춘다 ----
  const desktopHeader = (
    <div className="flex flex-none items-center gap-3">
      <h1 className="sr-only">나의 변화</h1>
      <ReviewTabs />
      <Dropdown
        aria-label="분야"
        value={String(effectiveHobbyId ?? activeHobbies[0]?.id ?? '')}
        options={hobbyOptions}
        onChange={(value) => setHobbyId(Number(value))}
      />
      <Dropdown aria-label="비교 기간" value={compareTo} options={periodOptions} onChange={(value) => setCompareTo(value)} />
      <div className="ml-auto flex flex-none items-center gap-3">
        <button
          type="button"
          onClick={() => setFlowPanelOpen(!flowPanelOpen)}
          aria-expanded={flowPanelOpen}
          aria-label="주별 흐름 패널 열기·닫기"
          title="주별 흐름"
          className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-line-2 bg-white px-3 t-sub font-medium text-ink"
        >
          <ChartIcon width={16} height={16} />
          주별 흐름
        </button>
        {usageQuery.data ? (
          <span className="whitespace-nowrap t-sub text-ink-2">오늘 {usageQuery.data.remaining}회 남음</span>
        ) : null}
      </div>
    </div>
  )

  const weekSummaryLine = selectedWeek
    ? `${formatMonthDay(selectedWeek.weekStart)} – ${formatMonthDay(addDays(selectedWeek.weekStart, 6))} · ${
        selectedWeek.recordedDays > 0 ? `${selectedWeek.recordedDays}일 · ${formatDurationMinutes(selectedWeek.totalMinutes)}` : '기록 없음'
      }`
    : ''

  /** 카드 머리줄: 제목(또는 라벨) 왼쪽, [전체 보기] 오른쪽. */
  const cardHead = (title: ReactNode, onOpen?: () => void) => (
    <div className="flex flex-none items-center justify-between gap-2">
      {title}
      {onOpen ? (
        <button
          type="button"
          onClick={onOpen}
          className="t-sub inline-flex h-8 flex-none items-center rounded-full px-2 font-medium text-base-ink hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]"
        >
          전체 보기
        </button>
      ) : null}
    </div>
  )

  // 주 요약 카드: 제목, 기간·일수·시간 한 줄, 그 주의 주제 칩(2줄 말줄임). 전체는 Dialog.
  const weekCardDesktop = selectedWeek ? (
    <section aria-label="주 요약" className="flex min-w-0 flex-1 flex-col gap-1.5 overflow-hidden rounded-2xl bg-surface p-4">
      {cardHead(<h3 className="text-card-title">주 요약</h3>, () => setDialog('week'))}
      <div className="flex flex-col gap-1.5">
      <p className="t-body line-clamp-3 break-words text-ink">{weekSummaryLine}</p>
      {selectedWeek.tags.length > 0 ? (
        <div className="ch-week-tags flex flex-wrap gap-1.5">
          {selectedWeek.tags.slice(0, 6).map((entry) => (
            <Tag key={entry.tag} variant="topic">
              {entry.tag} · {entry.count}
            </Tag>
          ))}
        </div>
      ) : (
        <p className="t-sub text-ink-2">이 주에는 주제로 묶인 기록이 없어요.</p>
      )}
      </div>
    </section>
  ) : null

  // 지표 해석 카드: AI 글이라 산세리프가 아니라 해석 카드와 같은 세리프 본문색(text-ink), 3줄 말줄임 + [전체 보기].
  const metricsCardDesktop = (() => {
    const shell = (body: ReactNode, onOpen?: () => void) => (
      <section aria-label="지표 해석" className="flex min-w-0 flex-1 flex-col gap-1.5 overflow-hidden rounded-2xl bg-surface p-4">
        {cardHead(<h3 className="text-card-title">지표 해석</h3>, onOpen)}
        {body}
      </section>
    )
    if (metricsQuery.isLoading || weeksQuery.isLoading) {
      return shell(<p className="t-sub text-ink-2">기록을 천천히 읽고 있어요</p>)
    }
    if (metricsQuery.isError) {
      return shell(
        <div className="flex items-center gap-3 t-sub text-ink-2">
          <span>지금은 불러오지 못했어요</span>
          <button type="button" onClick={() => metricsQuery.refetch()} className="font-medium text-base-ink">
            다시 받기
          </button>
        </div>,
      )
    }
    if (!metricsQuery.data) return null
    if (metricsQuery.data.locked) {
      return shell(
        <p className="t-sub text-ink-2">
          기록이 있는 주가 {metricsQuery.data.requiredWeeks ?? 2}주 모이면 해석이 열려요 ({metricsQuery.data.currentWeeks ?? 0}/
          {metricsQuery.data.requiredWeeks ?? 2})
        </p>,
      )
    }
    if (metricsQuery.data.empty) return shell(<p className="t-sub text-ink-2">이번 주는 쉬어 간 주예요.</p>)
    return shell(
      <p className="t-body line-clamp-3 break-words font-serif text-ink">{metricsQuery.data.displayText}</p>,
      () => setDialog('metrics'),
    )
  })()

  // 차트 y축은 데이터 최댓값 기준(최소 4)이고, 높이는 180~240px로 제한한다. 값 라벨은 각 막대 바로 위에 붙는다.
  const axisMax = Math.max(4, ...weeks.map((w) => (metric === 'days' ? w.recordedDays : 0)))

  /** 주별 흐름 본문(떠 있는 패널·모바일 시트 공용): [기록한 날|시간|태그] 토글, 차트, 성장 링크. fill이면 패널 높이에 맞춰 차트가 늘어난다. */
  const flowBody = (fill: boolean) => (
    <>
      <div className="flex flex-none items-center justify-between gap-3">
        <span className="t-sub text-ink-2">최근 {WEEK_COUNT}주</span>
        <div className="inline-flex rounded-lg border border-line-2 bg-white p-[0.1875rem]" role="group" aria-label="지표">
          {METRIC_TABS.map((tab) => (
            <button
              key={tab.value}
              type="button"
              aria-pressed={metric === tab.value}
              onClick={() => setMetric(tab.value)}
              className={[
                'h-8 whitespace-nowrap rounded-md px-3 t-sub font-medium',
                metric === tab.value ? 'bg-ink text-white' : 'bg-transparent text-ink-2',
              ].join(' ')}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {weeksQuery.isError ? (
        <Note variant="sand">
          {weeksQuery.error instanceof ApiError ? weeksQuery.error.message : '잠시 후 다시 시도해 주세요'}
        </Note>
      ) : null}
      {weeksQuery.isLoading ? <Loading /> : null}

      {weeks.length > 0 ? (
        metric === 'tags' ? (
          <div className={['flex min-h-0 flex-col gap-2 overflow-hidden', fill ? 'flex-1' : ''].join(' ')}>
            {topTags.length === 0 ? (
              <p className="t-sub text-ink-2">아직 모을 수 있는 태그가 없어요.</p>
            ) : (
              <div className="flex flex-wrap content-start gap-2 overflow-hidden">
                {topTags.slice(0, 12).map(([tag, count]) => (
                  <Tag key={tag} variant="topic">
                    {tag} · {count}
                  </Tag>
                ))}
              </div>
            )}
          </div>
        ) : (
          <div className={['flex min-h-0 flex-col gap-1.5', fill ? 'flex-1' : ''].join(' ')}>
            <div
              role="img"
              aria-label={`최근 ${WEEK_COUNT}주 ${metric === 'days' ? '한 주에 기록한 날 수' : '한 주에 쌓인 시간'}`}
              className={['flex min-w-0 items-end gap-1.5', fill ? 'min-h-[80px] flex-1' : ''].join(' ')}
              style={fill ? undefined : { height: 170 }}
            >
              {weeks.map((week) => {
                const value = metric === 'days' ? week.recordedDays : week.totalMinutes
                const ratio = metric === 'days' ? week.recordedDays / axisMax : week.totalMinutes / maxMinutes
                const isSelected = week.weekStart === effectiveWeekStart
                const valueLabel = metric === 'days' ? String(week.recordedDays) : formatDurationMinutes(week.totalMinutes)
                const accessibleLabel = value > 0 ? valueLabel : '기록 없음'
                return (
                  <button
                    key={week.weekStart}
                    type="button"
                    onClick={() => setSelectedWeekStart(week.weekStart)}
                    aria-label={`${formatMonthDay(week.weekStart)} 주, ${accessibleLabel}`}
                    aria-pressed={isSelected}
                    className="flex h-full min-w-0 flex-1 flex-col items-center justify-end bg-transparent p-0"
                  >
                    {value > 0 ? (
                      <>
                        <b className="mb-0.5 h-[18px] flex-none whitespace-nowrap font-sans t-cap font-semibold leading-[18px] text-ink">
                          {valueLabel}
                        </b>
                        <i
                          style={{ height: `calc((100% - 20px) * ${Math.min(1, ratio)})`, minHeight: 3 }}
                          className={['block w-full flex-none rounded-t', isSelected ? 'bg-base' : 'bg-[color-mix(in_srgb,var(--color-base)_58%,var(--color-paper))]'].join(' ')}
                        />
                      </>
                    ) : (
                      <i className="block h-px w-full flex-none bg-line-2" />
                    )}
                  </button>
                )
              })}
            </div>
            <div className="flex h-5 flex-none gap-1.5">
              {weeks.map((week, index) => {
                const isSelected = index === selectedWeekIndex
                const showLabel = isSelected || (weeks.length - 1 - index) % LABEL_STEP === 0
                return (
                  <span key={week.weekStart} className="min-w-0 flex-1 text-center t-cap text-ink-2">
                    {showLabel ? formatSlashDate(week.weekStart) : ''}
                  </span>
                )
              })}
            </div>
          </div>
        )
      ) : null}

      <Link
        to="/growth"
        className="inline-flex h-9 w-fit flex-none items-center gap-1 rounded-full px-2 t-sub font-medium text-base-ink no-underline hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]"
      >
        성장에서 더 보기
        <ChevronRightIcon width={16} height={16} />
      </Link>
    </>
  )


  const dialogs =
    dialog === 'interpretation' && result ? (
      <Dialog onClose={() => setDialog(null)} labelledBy="changes-dialog-title">
        <h2 id="changes-dialog-title" className="font-serif t-section font-semibold">
          변화 해석
        </h2>
        <div className="mt-3">
          {isStructuredCompare(result) ? (
            <CompareInsights result={result} />
          ) : (
            <p className="t-body-lg max-w-[70ch] break-words font-serif text-ink">{result.interpretation}</p>
          )}
        </div>
        <div className="mt-5 flex justify-end">
          <Button type="button" variant="secondary" onClick={() => setDialog(null)}>
            닫기
          </Button>
        </div>
      </Dialog>
    ) : dialog === 'week' && selectedWeek ? (
      <Dialog onClose={() => setDialog(null)} labelledBy="changes-dialog-title">
        <h2 id="changes-dialog-title" className="font-serif t-section font-semibold">
          주 요약
        </h2>
        <div className="mt-3 flex flex-col gap-3">
          {selectedWeekSummary}
          {recordedWeeks.length > 0 ? (
            <div className="flex flex-col">
              {[...recordedWeeks].reverse().map((week) => (
                <div key={week.weekStart} className="flex items-center justify-between gap-3 border-t border-line py-2.5 last:border-b">
                  <span>{formatMonthDay(week.weekStart)} 주</span>
                  <span className="tabular-nums font-semibold">
                    {metric === 'minutes' ? formatDurationMinutes(week.totalMinutes) : `${week.recordedDays}일`}
                  </span>
                </div>
              ))}
            </div>
          ) : null}
        </div>
        <div className="mt-5 flex justify-end">
          <Button type="button" variant="secondary" onClick={() => setDialog(null)}>
            닫기
          </Button>
        </div>
      </Dialog>
    ) : dialog === 'metrics' && metricsQuery.data ? (
      <Dialog onClose={() => setDialog(null)} labelledBy="changes-dialog-title">
        <h2 id="changes-dialog-title" className="sr-only">
          지표 해석
        </h2>
        <AiViewCard
          label="지표 해석"
          question="해석이 맞나요?"
          isLoading={metricsQuery.isLoading}
          isError={metricsQuery.isError}
          error={metricsQuery.error}
          data={metricsQuery.data}
          onRetry={() => metricsQuery.refetch()}
          onChange={(updated: AiViewResult) => queryClient.setQueryData(metricsKey, updated)}
        />
        <div className="mt-4 flex justify-end">
          <Button type="button" variant="secondary" onClick={() => setDialog(null)}>
            닫기
          </Button>
        </div>
      </Dialog>
    ) : null

  if (isDesktop) {
    return (
      <div className="flex min-h-0 flex-1 flex-col gap-4">
        {desktopHeader}
        {/* 위에서부터: ① 이전·최근 두 장(내용 높이) → ② 변화 해석(남는 높이를 먹고 모자라면 줄어듦) → ③ 숫자 칩·주 요약·지표 해석.
            글자는 토큰으로 고정이고 카드 높이만 달라진다. 레이아웃이 결과 도착 시 움직이지 않게 ①·②에는 같은 크기의 자리표시를 둔다. */}
        <div className="ch-body flex min-h-0 flex-1 flex-col gap-4 overflow-hidden whitespace-normal break-words">
          {comparison}
          {basisQuery.isLoading || basis ? pairRow : null}
          {compareQuery.isFetching || showResult ? interpretationCard : null}
          <div className="grid flex-none grid-cols-12 gap-4">
            <div className="col-span-6 min-w-0">{metricChips}</div>
            <div className="col-span-3 flex min-w-0">{weekCardDesktop}</div>
            <div className="col-span-3 flex min-w-0">{metricsCardDesktop}</div>
          </div>
        </div>
        {flowPanelOpen ? (
          <FloatingPanel title="주별 흐름" storageKey="growit.changes.weeklyFlow" onClose={() => setFlowPanelOpen(false)}>
            {flowBody(true)}
          </FloatingPanel>
        ) : null}
        {dialogs}
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <ReviewHeaderRow
        title="나의 변화"
        action={
          <>
            <button
              type="button"
              onClick={() => setFlowSheetOpen(true)}
              aria-label="주별 흐름 열기"
              className="inline-flex min-h-11 items-center gap-1.5 rounded-lg border border-line-2 bg-white px-3 t-sub font-medium text-ink"
            >
              <ChartIcon width={16} height={16} />
              주별 흐름
            </button>
            {headerAction}
          </>
        }
      />
      {toolbarRow}
      <Split main={comparison} side={mobileExtras} />
      {flowSheetOpen ? (
        <FloatingPanel title="주별 흐름" storageKey="growit.changes.weeklyFlow" mobile onClose={() => setFlowSheetOpen(false)}>
          {flowBody(false)}
        </FloatingPanel>
      ) : null}
    </div>
  )
}
