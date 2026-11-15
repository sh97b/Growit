import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AiLabel } from '../components/AiLabel'
import { AiViewCard } from '../components/AiViewCard'
import { ChevronRightIcon } from '../components/icons'
import { FeedbackRow } from '../components/FeedbackRow'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
import { ReviewHeaderRow } from '../components/ReviewHeaderRow'
import { Split } from '../components/PageLayout'
import { RecordPairCards } from '../components/RecordPairCards'
import { Tag } from '../components/Tag'
import { useCurrentUser } from '../hooks/useCurrentUser'
import { useHobbies } from '../hooks/useHobbies'
import { ApiError } from '../lib/api'
import {
  fetchCompare,
  fetchMetricsInterpretation,
  submitAiResultFeedback,
  type AiViewResult,
  type CompareResult,
  type CompareTo,
} from '../lib/ai'
import { addDays, formatDurationMinutes, formatMonthDay, startOfWeekLocal, todayLocalDate } from '../lib/date'
import { pickClass } from '../lib/recordForm'
import { fetchWeeks, type WeekSummary } from '../lib/stats'

const PERIODS: { value: CompareTo; label: string }[] = [
  { value: '1W', label: '1주 전' },
  { value: '2W', label: '2주 전' },
  { value: '1M', label: '한 달 전' },
  { value: '3M', label: '3개월 전' },
]

const WEEK_COUNT = 12
const CHART_HEIGHT = 112
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
  const [compareTo, setCompareTo] = useState<CompareTo>('1M')
  const [result, setResult] = useState<CompareResult | null>(null)
  const [feedbackError, setFeedbackError] = useState<string | null>(null)
  const [metric, setMetric] = useState<Metric>('days')
  const [selectedWeekStart, setSelectedWeekStart] = useState<string | null>(null)
  const [metricsOpen, setMetricsOpen] = useState(false)
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: () => fetchCompare({ hobbyId: effectiveHobbyId!, compareTo }),
    onSuccess: (data) => setResult(data),
  })

  const feedbackMutation = useMutation({
    mutationFn: ({ id, value }: { id: number; value: 'HELPFUL' | 'NOT_HELPFUL' }) =>
      submitAiResultFeedback<CompareResult>(id, value),
  })

  // hobbyId가 null이거나(처음 진입) 더 이상 활성 분야 목록에 없으면(분야가 보관되는 등) 첫 활성 분야로
  // 보정한다. 화면에 보이는 선택과 요청에 실제로 쓰이는 값이 반드시 같은 값에서 나오게 한다.
  const hobby = activeHobbies?.find((h) => h.id === hobbyId) ?? activeHobbies?.[0] ?? null
  const effectiveHobbyId = hobby?.id ?? null

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

  const maxDays = 7
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
  // 기본 선택 = 기록이 있는 가장 최근 주(기록이 하나도 없으면 이번 주). 전에는 배열의 마지막 주(이번 주)를
  // 그대로 썼는데, 이번 주가 아직 비어 있으면(0일) 기본값부터 "쉬어 간 주"로 보여 혼란스러웠다(지시문 4).
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
            <Link to="/settings/ai" className="font-medium text-base-ink underline underline-offset-4">
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
    setResult((prev) => (prev ? { ...prev, feedback: value } : prev))
    feedbackMutation.mutate(
      { id: result.id, value },
      {
        onError: (mutationError) => {
          setResult((prev) => (prev ? { ...prev, feedback: result.feedback } : prev))
          setFeedbackError(mutationError instanceof ApiError ? mutationError.message : '잠시 후 다시 시도해 주세요')
        },
      },
    )
  }

  const controlsRow = (
    <div className="flex flex-col gap-3 lg:flex-row lg:flex-wrap lg:items-center">
      <div className="flex flex-wrap gap-2">
        {activeHobbies.map((h) => (
          <button
            key={h.id}
            type="button"
            aria-pressed={effectiveHobbyId === h.id}
            onClick={() => {
              setHobbyId(h.id)
              setResult(null)
            }}
            className={pickClass(effectiveHobbyId === h.id)}
          >
            {h.name}
          </button>
        ))}
      </div>

      <div className="flex flex-wrap gap-2">
        {PERIODS.map((period) => (
          <button
            key={period.value}
            type="button"
            aria-pressed={compareTo === period.value}
            onClick={() => {
              setCompareTo(period.value)
              setResult(null)
            }}
            className={pickClass(compareTo === period.value)}
          >
            {period.label}
          </button>
        ))}
      </div>
    </div>
  )

  const compareAction = !result ? (
    <button
      type="button"
      disabled={mutation.isPending || effectiveHobbyId == null}
      onClick={() => mutation.mutate()}
      className="inline-flex min-h-10 w-fit items-center justify-center rounded-lg bg-base px-5 text-body-min font-medium text-white disabled:bg-line disabled:text-[#5F594F]"
    >
      변화 보기
    </button>
  ) : null

  // 한 줄 지표 카드: 기록한 날·쌓인 시간·자주 나온 주제를 컴팩트한 카드 하나에 모은다(지시문 3).
  const metricsCard = result?.metrics ? (() => {
    const m = result.metrics!
    const bothZeroMinutes = m.totalMinutes.past === 0 && m.totalMinutes.current === 0
    const minutesLabel = bothZeroMinutes
      ? null
      : m.totalMinutes.past === m.totalMinutes.current
        ? `${formatDurationMinutes(m.totalMinutes.current)} (변동 없음)`
        : `${m.totalMinutes.past === 0 ? '기록 안 함' : formatDurationMinutes(m.totalMinutes.past)} → ${m.totalMinutes.current === 0 ? '기록 안 함' : formatDurationMinutes(m.totalMinutes.current)}`
    return (
      <div className="flex flex-col gap-2 rounded-xl bg-surface p-4">
        <div className="flex items-center justify-between gap-3 text-sm">
          <span className="text-ink-2">기록한 날</span>
          <span className="font-medium text-ink">{m.recordedDays.past}일 → {m.recordedDays.current}일</span>
        </div>
        {minutesLabel ? (
          <div className="flex items-center justify-between gap-3 text-sm">
            <span className="text-ink-2">쌓인 시간</span>
            <span className="font-medium text-ink">{minutesLabel}</span>
          </div>
        ) : null}
        {m.topTags && m.topTags.length > 0 ? (
          <div className="flex flex-wrap gap-1.5 pt-1">
            {m.topTags.slice(0, 3).map((topic) => (
              <Tag key={topic.tag} variant="topic">
                {topic.tag} · {topic.count}
              </Tag>
            ))}
          </div>
        ) : null}
      </div>
    )
  })() : null

  const comparison = (
    <div className="flex flex-col gap-5">
      {mutation.isPending ? (
        <div className="flex flex-col gap-3.5 rounded-2xl bg-surface p-7">
          <AiLabel>기록을 천천히 읽고 있어요</AiLabel>
          <div className="h-3.5 w-[80%] animate-pulse rounded-full bg-rest" />
          <div className="h-3.5 w-full animate-pulse rounded-full bg-rest" />
        </div>
      ) : null}

      {mutation.isError ? (
        <div className="flex flex-col gap-3">
          <Note variant="sand">
            {mutation.error instanceof ApiError ? mutation.error.message : '잠시 후 다시 시도해 주세요'}
          </Note>
          <button
            type="button"
            onClick={() => mutation.mutate()}
            className="inline-flex min-h-11 w-fit items-center text-sm font-medium text-base-ink underline underline-offset-4"
          >
            다시 받기
          </button>
        </div>
      ) : null}

      {result?.locked && result.lockReason !== 'NO_PAST_RECORD' ? (
        <div className="flex flex-col items-start gap-3 rounded-2xl bg-surface p-7">
          <p className="text-ink">기록이 3개 쌓이면 비교가 열려요</p>
          <p className="text-sm font-medium text-ink-2">{result.currentCount ?? 0}/{result.requiredCount ?? 3}</p>
          <Link to="/records/new" className="inline-flex min-h-11 items-center text-sm font-medium text-base-ink underline underline-offset-4">
            기록하기
          </Link>
        </div>
      ) : null}

      {result?.locked && result.lockReason === 'NO_PAST_RECORD' ? (
        <div className="flex flex-col items-start gap-3 rounded-2xl bg-surface p-7">
          <p className="text-ink">비교할 이전 기록이 아직 없어요. 기록이 며칠 더 쌓이면 비교가 열려요</p>
          <p className="text-sm text-ink-2">
            {result.oldestRecordDate ? `가장 오래된 기록 ${formatMonthDay(result.oldestRecordDate)}` : ''}
            {result.oldestRecordDate && result.latestRecordDate ? ' · ' : ''}
            {result.latestRecordDate ? `최근 기록 ${formatMonthDay(result.latestRecordDate)}` : ''}
          </p>
          <Link to="/records/new" className="inline-flex min-h-11 items-center text-sm font-medium text-base-ink underline underline-offset-4">
            기록하기
          </Link>
        </div>
      ) : null}

      {result && !result.locked ? (
        <div className="flex flex-col gap-5">
          {result.anchoredToLatest ? (
            <p className="text-sm text-ink-2">
              마지막 기록({result.currentDate ? formatMonthDay(result.currentDate) : ''})을 기준으로 비교했어요
            </p>
          ) : null}
          {result.pastIsOldest ? (
            <p className="text-sm text-ink-2">
              {PERIODS.find((p) => p.value === compareTo)?.label ?? '이전'} 기록이 아직 없어서 가장 오래된 기록
              {result.pastDate ? ` (${formatMonthDay(result.pastDate)})` : ''}과 비교했어요
            </p>
          ) : null}
          <RecordPairCards
            left={
              result.pastRecord
                ? { id: result.pastRecord.id, label: `${result.pastDate ? formatMonthDay(result.pastDate) : ''} 전후`, content: result.pastRecord.content }
                : null
            }
            right={
              result.currentRecord
                ? { id: result.currentRecord.id, label: `최근 · ${result.currentDate ? formatMonthDay(result.currentDate) : ''}`, content: result.currentRecord.content }
                : null
            }
          />

          <div className="flex flex-col gap-3.5 rounded-2xl border border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_9%,white)] p-7">
            <AiLabel>변화 해석</AiLabel>
            <p className="font-serif text-lg leading-[1.55]">{result.interpretation}</p>
            {/* 근거 칩은 원래 두지 않는다 — compare의 근거는 항상 pastRecord·currentRecord뿐이라
                바로 위 RecordPairCards와 완전히 같은 기록이라서(지시문 3), 중복으로 다시 보여주지 않는다. */}
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

          {metricsCard}

          <button
            type="button"
            onClick={() => setResult(null)}
            className="inline-flex min-h-11 w-fit items-center text-sm font-medium text-base-ink underline underline-offset-4"
          >
            다시 비교하기
          </button>
        </div>
      ) : null}
    </div>
  )

  const selectedWeekSummary = selectedWeek ? (
    <div className="flex flex-col gap-1.5 rounded-2xl bg-surface p-4">
      <span className="text-sm font-semibold text-ink">
        선택한 주 · {formatMonthDay(selectedWeek.weekStart)} – {formatMonthDay(addDays(selectedWeek.weekStart, 6))}
      </span>
      <span className="tabular-nums text-sm text-ink-2">
        기록한 날 {selectedWeek.recordedDays}일 · {formatDurationMinutes(selectedWeek.totalMinutes)}
      </span>
      <Link to={`/weeks/${selectedWeek.weekStart}`} className="inline-flex min-h-9 w-fit items-center gap-1 text-sm font-medium text-base-ink no-underline underline underline-offset-4">
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
          className="flex w-full flex-col items-start gap-1 rounded-xl bg-surface p-4 text-left"
        >
          <span className="text-sm font-medium text-ink">지표 해석 보기 ▾</span>
          <span className="truncate text-sm text-ink-2">{firstSentence(metricsQuery.data.displayText)}</span>
        </button>
      )
    }
    return (
      <div className="flex flex-col gap-2">
        <button
          type="button"
          onClick={() => setMetricsOpen(false)}
          className="inline-flex min-h-9 w-fit items-center text-sm font-medium text-ink-2"
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

  const weeklyFlow = (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-card-title">주별 흐름</h2>
        <div className="inline-flex rounded-lg border border-line-2 bg-white p-[0.1875rem]" role="group" aria-label="지표">
          {METRIC_TABS.map((tab) => (
            <button
              key={tab.value}
              type="button"
              aria-pressed={metric === tab.value}
              onClick={() => setMetric(tab.value)}
              className={[
                'min-h-9 whitespace-nowrap rounded-md px-3.5 text-sm font-medium',
                metric === tab.value ? 'bg-ink text-white' : 'bg-transparent text-ink-2',
              ].join(' ')}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {weeksQuery.isLoading ? <Loading /> : null}
      {weeksQuery.isError ? (
        <Note variant="sand">
          {weeksQuery.error instanceof ApiError ? weeksQuery.error.message : '잠시 후 다시 시도해 주세요'}
        </Note>
      ) : null}

      {weeks.length > 0 ? (
        <>
          {metric === 'tags' ? (
            <div className="flex flex-col gap-2">
              <span className="text-sm text-ink-2">최근 {WEEK_COUNT}주 자주 나온 주제</span>
              {topTags.length === 0 ? (
                <p className="py-4 text-sm text-ink-2">아직 모을 수 있는 태그가 없어요.</p>
              ) : (
                <div className="flex flex-col">
                  {topTags.map(([tag, count]) => (
                    <div key={tag} className="flex items-center justify-between gap-3 border-t border-line py-3 last:border-b">
                      <Tag variant="topic">{tag}</Tag>
                      <span className="tabular-nums font-semibold">{count}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          ) : (
            <div className="flex flex-col gap-1.5">
              <span className="text-sm text-ink-2">
                {metric === 'days' ? '한 주에 기록한 날 수 (0–7일)' : '한 주에 쌓인 시간'}
              </span>
              {/* 그래프 영역의 높이를 고정하고, 값 라벨은 막대 위(overflow-visible)로 살짝 올라가며,
                  x축 라벨은 별도 행에 둬서 서로·막대와 겹치지 않는다(지시문 4). */}
              <div
                role="img"
                aria-label={`최근 ${WEEK_COUNT}주 ${metric === 'days' ? '한 주에 기록한 날 수' : '한 주에 쌓인 시간'}`}
                className="flex items-end gap-1.5 overflow-visible pt-5"
                style={{ height: CHART_HEIGHT }}
              >
                {weeks.map((week) => {
                  const value = metric === 'days' ? week.recordedDays : week.totalMinutes
                  const ratio = metric === 'days' ? week.recordedDays / maxDays : week.totalMinutes / maxMinutes
                  const barHeight = Math.max(3, Math.round(ratio * CHART_HEIGHT))
                  const isSelected = week.weekStart === effectiveWeekStart
                  const valueLabel = metric === 'days' ? String(week.recordedDays) : formatDurationMinutes(week.totalMinutes)
                  return (
                    <button
                      key={week.weekStart}
                      type="button"
                      onClick={() => setSelectedWeekStart(week.weekStart)}
                      aria-label={`${formatMonthDay(week.weekStart)} 주, ${valueLabel}`}
                      aria-pressed={isSelected}
                      className="relative flex h-full flex-1 flex-col items-center justify-end bg-transparent p-0"
                    >
                      {value > 0 ? (
                        <b className="absolute -top-5 whitespace-nowrap font-sans text-caption-min font-semibold text-ink">
                          {valueLabel}
                        </b>
                      ) : null}
                      {value > 0 ? (
                        <i
                          style={{ height: barHeight }}
                          className={['block w-full rounded-t', isSelected ? 'bg-base' : 'bg-[color-mix(in_srgb,var(--color-base)_58%,var(--color-paper))]'].join(' ')}
                        />
                      ) : (
                        <i className="block h-px w-full bg-line-2" />
                      )}
                    </button>
                  )
                })}
              </div>
              <div className="flex gap-1.5" style={{ height: '1.5rem' }}>
                {weeks.map((week, index) => {
                  const isSelected = index === selectedWeekIndex
                  const showLabel = isSelected || (weeks.length - 1 - index) % LABEL_STEP === 0
                  return (
                    <span
                      key={week.weekStart}
                      className="flex-1 text-center text-caption-min text-ink-2"
                      style={{ fontSize: '12px' }}
                    >
                      {showLabel ? formatSlashDate(week.weekStart) : ''}
                    </span>
                  )
                })}
              </div>
            </div>
          )}

          <div className="mt-1 flex flex-col gap-4">
            {selectedWeekSummary}
            {metricsInterpretation}
          </div>

          {metric === 'tags' ? null : (
            <div className="flex flex-col">
              {[...weeks].reverse().map((week) => (
                <div key={week.weekStart} className="flex items-center justify-between gap-3 border-t border-line py-3 last:border-b">
                  <span>{formatMonthDay(week.weekStart)} 주</span>
                  <span className="tabular-nums font-semibold">
                    {metric === 'days' ? `${week.recordedDays}일` : formatDurationMinutes(week.totalMinutes)}
                  </span>
                </div>
              ))}
            </div>
          )}
        </>
      ) : null}
    </div>
  )

  return (
    <div className="flex flex-col gap-4 lg:min-h-0 lg:flex-1">
      <ReviewHeaderRow title="나의 변화" action={compareAction} />
      {controlsRow}
      <Split main={comparison} side={weeklyFlow} className="lg:min-h-0 lg:flex-1" />
    </div>
  )
}
