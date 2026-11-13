import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AiViewCard } from '../components/AiViewCard'
import { ChevronLeftIcon, ChevronRightIcon } from '../components/icons'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
import { useHobbies } from '../hooks/useHobbies'
import { ApiError } from '../lib/api'
import { fetchMetricsInterpretation, type AiViewResult } from '../lib/ai'
import { formatDurationMinutes, formatMonthDay, startOfWeekLocal, todayLocalDate } from '../lib/date'
import { pickClass } from '../lib/recordForm'
import { fetchWeeks, type WeekSummary } from '../lib/stats'

const WEEK_COUNT = 12
const CHART_HEIGHT = 108

type Metric = 'days' | 'minutes' | 'tags'

const METRIC_TABS: { value: Metric; label: string }[] = [
  { value: 'days', label: '기록한 날' },
  { value: 'minutes', label: '시간' },
  { value: 'tags', label: '태그' },
]

function addWeeks(weekStart: string, amount: number): string {
  const [year, month, day] = weekStart.split('-').map(Number)
  const date = new Date(year, month - 1, day)
  date.setDate(date.getDate() + amount * 7)
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

export function MetricsDetailPage() {
  const { data: activeHobbies, isLoading: hobbiesLoading } = useHobbies(false)
  const [hobbyId, setHobbyId] = useState<number | null>(null)
  const [selectedWeekStart, setSelectedWeekStart] = useState<string | null>(null)
  const [metric, setMetric] = useState<Metric>('days')
  const queryClient = useQueryClient()

  const currentWeekStart = startOfWeekLocal(new Date())
  const from = addWeeks(currentWeekStart, -(WEEK_COUNT - 1))
  const to = todayLocalDate()

  const weeksQuery = useQuery({
    queryKey: ['stats', 'weeks', from, to, hobbyId],
    queryFn: () => fetchWeeks({ from, to, hobbyId: hobbyId ?? undefined }),
  })

  const metricsKey = ['ai', 'metrics', from, to, hobbyId]
  const metricsQuery = useQuery({
    queryKey: metricsKey,
    queryFn: () => fetchMetricsInterpretation({ hobbyId: hobbyId ?? undefined, from, to }),
    enabled: weeksQuery.isSuccess,
    retry: false,
  })

  const weeks = weeksQuery.data ?? []
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

  if (hobbiesLoading) return <Loading />

  const selectedWeekSummary = selectedWeek ? (
    <div className="flex flex-col gap-1.5 rounded-2xl bg-surface p-4">
      <span className="text-sm font-semibold text-ink">
        선택한 주 · {formatMonthDay(selectedWeek.weekStart)} – {formatMonthDay(addWeeks(selectedWeek.weekStart, 1))
          .replace(/^0/, '')}
      </span>
      <span className="tabular-nums text-sm text-ink-2">
        기록한 날 {selectedWeek.recordedDays}일
        {selectedWeek.hobbies.length > 0
          ? ` · ${selectedWeek.hobbies.map((h) => `${activeHobbies?.find((a) => a.id === h.hobbyId)?.name ?? '분야'} ${h.recordedDays}`).join(', ')}`
          : ''}
        {' · '}
        {formatDurationMinutes(selectedWeek.totalMinutes)}
      </span>
      <Link to={`/weeks/${selectedWeek.weekStart}`} className="inline-flex min-h-9 w-fit items-center gap-1 text-sm font-medium text-base-ink no-underline underline underline-offset-4">
        주 상세 보기
        <ChevronRightIcon width={16} height={16} />
      </Link>
    </div>
  ) : null

  return (
    <div className="flex flex-col gap-5 lg:mx-auto lg:max-w-[45rem]">
      <nav className="text-sm text-ink-2">
        <Link to="/changes" className="inline-flex items-center gap-1 text-ink-2 no-underline">
          <ChevronLeftIcon width={16} height={16} />
          나의 변화
        </Link>
      </nav>

      <div className="flex items-center justify-between gap-3">
        <h1 className="text-page-title">변화 지표</h1>
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

      <div className="flex flex-wrap gap-2">
        <button type="button" aria-pressed={hobbyId === null} onClick={() => setHobbyId(null)} className={pickClass(hobbyId === null)}>
          전체
        </button>
        {(activeHobbies ?? []).map((h) => (
          <button key={h.id} type="button" aria-pressed={hobbyId === h.id} onClick={() => setHobbyId(h.id)} className={pickClass(hobbyId === h.id)}>
            {h.name}
          </button>
        ))}
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
}
