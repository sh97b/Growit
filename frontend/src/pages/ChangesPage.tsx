import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AiLabel } from '../components/AiLabel'
import { AiViewCard } from '../components/AiViewCard'
import { ChevronRightIcon } from '../components/icons'
import { EvidenceChip } from '../components/EvidenceChip'
import { FeedbackRow } from '../components/FeedbackRow'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
import { PageHeader, Split } from '../components/PageLayout'
import { RecordPairCards } from '../components/RecordPairCards'
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
import { addWeeks, formatDurationMinutes, formatMonthDay, startOfWeekLocal, todayLocalDate } from '../lib/date'
import { pickClass } from '../lib/recordForm'
import { fetchWeeks, type WeekSummary } from '../lib/stats'

const PERIODS: { value: CompareTo; label: string }[] = [
  { value: '1W', label: '1주 전' },
  { value: '2W', label: '2주 전' },
  { value: '1M', label: '한 달 전' },
  { value: '3M', label: '3개월 전' },
]

const WEEK_COUNT = 12
const CHART_HEIGHT = 108

type Metric = 'days' | 'minutes' | 'tags'

const METRIC_TABS: { value: Metric; label: string }[] = [
  { value: 'days', label: '기록한 날' },
  { value: 'minutes', label: '시간' },
  { value: 'tags', label: '태그' },
]

export function ChangesPage() {
  const { data: currentUser, isLoading: userLoading } = useCurrentUser()
  const { data: activeHobbies, isLoading: hobbiesLoading } = useHobbies(false)
  const [hobbyId, setHobbyId] = useState<number | null>(null)
  const [compareTo, setCompareTo] = useState<CompareTo>('1M')
  const [result, setResult] = useState<CompareResult | null>(null)
  const [feedbackError, setFeedbackError] = useState<string | null>(null)
  const [metric, setMetric] = useState<Metric>('days')
  const [selectedWeekStart, setSelectedWeekStart] = useState<string | null>(null)
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
  const weeksFrom = addWeeks(currentWeekStart, -(WEEK_COUNT - 1))
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
  const selectedWeek: WeekSummary | undefined = useMemo(
    () => weeks.find((w) => w.weekStart === (selectedWeekStart ?? weeks[weeks.length - 1]?.weekStart)),
    [weeks, selectedWeekStart],
  )

  if (userLoading || hobbiesLoading) {
    return <Loading />
  }

  if (!currentUser?.aiEnabled) {
    return (
      <div className="flex flex-col gap-4">
        <PageHeader title="나의 변화" />
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
        <PageHeader title="나의 변화" />
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

  const selectors = (
    <>
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

      {!result ? (
        <button
          type="button"
          disabled={mutation.isPending || effectiveHobbyId == null}
          onClick={() => mutation.mutate()}
          className="inline-flex min-h-11 w-fit items-center justify-center rounded-lg bg-base px-5 text-body-min font-medium text-white disabled:bg-line disabled:text-[#5F594F] lg:ml-auto"
        >
          변화 보기
        </button>
      ) : null}
    </>
  )

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
            <div className="flex flex-wrap gap-2">
              {result.pastRecord ? <EvidenceChip record={result.pastRecord} /> : null}
              {result.currentRecord ? <EvidenceChip record={result.currentRecord} /> : null}
            </div>
            <FeedbackRow
              question="이 해석이 맞나요?"
              status={result.feedback === 'HELPFUL' ? 'positive' : result.feedback === 'NOT_HELPFUL' ? 'negative' : null}
              pending={feedbackMutation.isPending}
              onPositive={() => handleFeedback('HELPFUL')}
              onNegative={() => handleFeedback('NOT_HELPFUL')}
              errorMessage={feedbackError}
            />
          </div>

          {result.metrics ? (
            <div className="flex flex-col gap-4">
              <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
                <div className="flex flex-col gap-1 rounded-xl bg-surface p-4">
                  <span className="text-sm text-ink-2">기록한 날</span>
                  <span className="font-serif text-lg text-ink">
                    {result.metrics.recordedDays.past}일 → {result.metrics.recordedDays.current}일
                  </span>
                </div>
                {result.metrics.totalMinutes.past === 0 && result.metrics.totalMinutes.current === 0 ? null : (
                  <div className="flex flex-col gap-1 rounded-xl bg-surface p-4">
                    <span className="text-sm text-ink-2">쌓인 시간</span>
                    <span className="font-serif text-lg text-ink">
                      {result.metrics.totalMinutes.past === 0 ? '기록 안 함' : formatDurationMinutes(result.metrics.totalMinutes.past)}
                      {' → '}
                      {result.metrics.totalMinutes.current === 0 ? '기록 안 함' : formatDurationMinutes(result.metrics.totalMinutes.current)}
                    </span>
                  </div>
                )}
              </div>
              {result.metrics.topTags && result.metrics.topTags.length > 0 ? (
                <div className="flex flex-col gap-2 rounded-xl bg-surface p-4">
                  <span className="text-sm text-ink-2">자주 나온 주제</span>
                  <div className="flex flex-wrap gap-2">
                    {result.metrics.topTags.map((topic) => (
                      <span
                        key={topic.tag}
                        className="inline-flex h-7 items-center rounded-md bg-tag-topic-bg px-2.5 text-sm font-medium text-tag-topic-fg"
                      >
                        {topic.tag} · {topic.count}
                      </span>
                    ))}
                  </div>
                </div>
              ) : null}
            </div>
          ) : null}

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
        선택한 주 · {formatMonthDay(selectedWeek.weekStart)} – {formatMonthDay(addWeeks(selectedWeek.weekStart, 1)).replace(/^0/, '')}
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
                      <span className="inline-flex h-7 items-center rounded-md bg-tag-topic-bg px-2.5 text-sm font-medium text-tag-topic-fg">
                        {tag}
                      </span>
                      <span className="tabular-nums font-semibold">{count}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          ) : (
            <div className="flex flex-col gap-2">
              <span className="text-sm text-ink-2">
                {metric === 'days' ? '한 주에 기록한 날 수 (0–7일)' : '한 주에 쌓인 시간'}
              </span>
              <div
                role="img"
                aria-label={`최근 ${WEEK_COUNT}주 ${metric === 'days' ? '한 주에 기록한 날 수' : '한 주에 쌓인 시간'}`}
                className="grid items-end gap-2"
                style={{ gridTemplateColumns: `repeat(${weeks.length}, minmax(0, 1fr))`, height: CHART_HEIGHT }}
              >
                {weeks.map((week) => {
                  const ratio = metric === 'days' ? week.recordedDays / maxDays : week.totalMinutes / maxMinutes
                  const barHeight = Math.max(3, Math.round(ratio * CHART_HEIGHT))
                  const isSelected = week.weekStart === (selectedWeekStart ?? weeks[weeks.length - 1]?.weekStart)
                  const valueLabel = metric === 'days' ? String(week.recordedDays) : formatDurationMinutes(week.totalMinutes)
                  return (
                    <button
                      key={week.weekStart}
                      type="button"
                      onClick={() => setSelectedWeekStart(week.weekStart)}
                      aria-label={`${formatMonthDay(week.weekStart)} 주, ${valueLabel}`}
                      aria-pressed={isSelected}
                      className="flex h-full flex-col items-center justify-end gap-1 bg-transparent p-0"
                    >
                      <b className="whitespace-nowrap font-sans text-caption-min font-semibold text-ink">{valueLabel}</b>
                      <i
                        style={{ height: barHeight }}
                        className={['block w-full rounded-t', isSelected ? 'bg-base' : 'bg-[color-mix(in_srgb,var(--color-base)_58%,var(--color-paper))]'].join(' ')}
                      />
                      <span className="whitespace-nowrap text-caption-min text-ink-2">{formatMonthDay(week.weekStart)}</span>
                    </button>
                  )
                })}
              </div>
            </div>
          )}

          {selectedWeekSummary}

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
        </>
      ) : null}
    </div>
  )

  return (
    <div className="flex flex-col gap-5">
      <PageHeader title="나의 변화" />

      <div className="flex flex-col gap-5 lg:flex-row lg:flex-wrap lg:items-center">{selectors}</div>

      <Split main={comparison} side={weeklyFlow} />
    </div>
  )
}
