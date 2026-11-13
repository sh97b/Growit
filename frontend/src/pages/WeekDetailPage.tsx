import { Link, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AiViewCard } from '../components/AiViewCard'
import { ChevronLeftIcon, ChevronRightIcon, RestIcon } from '../components/icons'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
import { PageHeader, Split } from '../components/PageLayout'
import { ApiError } from '../lib/api'
import { fetchWeeklyOneLiner, type AiViewResult } from '../lib/ai'
import { formatDurationMinutes, formatMonthDay } from '../lib/date'
import { fetchWeekDetail } from '../lib/stats'

const WEEKDAY_LABELS = ['월', '화', '수', '목', '금', '토', '일']

export function WeekDetailPage() {
  const { weekStart } = useParams()
  const queryClient = useQueryClient()

  const detailQuery = useQuery({
    queryKey: ['stats', 'weeks', weekStart],
    queryFn: () => fetchWeekDetail(weekStart!),
    enabled: Boolean(weekStart),
  })

  const hasRecords = (detailQuery.data?.recordedDays ?? 0) > 0
  const weeklyKey = ['ai', 'weekly', weekStart]
  const weeklyQuery = useQuery({
    queryKey: weeklyKey,
    queryFn: () => fetchWeeklyOneLiner({ weekStart: weekStart! }),
    enabled: Boolean(weekStart) && detailQuery.isSuccess && hasRecords,
    retry: false,
  })

  if (detailQuery.isLoading) {
    return <Loading />
  }

  if (detailQuery.isError) {
    return (
      <Note variant="sand">
        {detailQuery.error instanceof ApiError ? detailQuery.error.message : '잠시 후 다시 시도해 주세요'}
      </Note>
    )
  }

  const detail = detailQuery.data
  if (!detail) return null

  const summary = (
    <div className="flex gap-10 rounded-2xl bg-surface p-5 lg:flex-col lg:gap-4">
      <div className="flex flex-col">
        <b className="font-serif text-lg">{detail.recordedDays}일</b>
        <span className="text-sm text-ink-2">기록한 날</span>
      </div>
      <div className="flex flex-col">
        <b className="font-serif text-lg">{formatDurationMinutes(detail.totalMinutes)}</b>
        <span className="text-sm text-ink-2">쌓인 시간</span>
      </div>
      <div className="flex flex-col">
        <b className="font-serif text-lg">{detail.hobbyCount}개</b>
        <span className="text-sm text-ink-2">활동 분야</span>
      </div>
    </div>
  )

  const aiCard = hasRecords ? (
    <AiViewCard
      label="이 주의 한 줄"
      question="이 주의 한 줄이 맞나요?"
      isLoading={weeklyQuery.isLoading}
      isError={weeklyQuery.isError}
      error={weeklyQuery.error}
      data={weeklyQuery.data}
      onRetry={() => weeklyQuery.refetch()}
      onChange={(updated: AiViewResult) => queryClient.setQueryData(weeklyKey, updated)}
    />
  ) : (
    <Note variant="calm">쉬어 간 주예요.</Note>
  )

  const main = (
    <>
      <div className="flex flex-col">
        {detail.days.map((day) => {
          const weekdayIndex = (new Date(day.date).getDay() + 6) % 7
          const dayLabel = `${WEEKDAY_LABELS[weekdayIndex]} ${formatMonthDay(day.date)}`
          if (day.records.length === 0) {
            return (
              <div key={day.date} className="flex items-center gap-5 border-t border-line py-3 text-ink last:border-b">
                <span className="w-[4.5rem] flex-none tabular-nums text-sm text-ink-2">{dayLabel}</span>
                <span className="flex items-center gap-1.5 text-sm text-ink-2">
                  <RestIcon width={14} height={14} />
                  쉬어 간 날
                </span>
              </div>
            )
          }
          return day.records.map((record, index) => (
            <Link
              key={record.id}
              to={`/records/${record.id}`}
              className="flex items-start gap-5 border-t border-line py-3 text-ink no-underline last:border-b"
            >
              <span className="w-[4.5rem] flex-none font-semibold tabular-nums">{index === 0 ? dayLabel : ''}</span>
              <span className="flex min-w-0 flex-col gap-1.5">
                <span className="font-serif text-base leading-[1.7]">{record.content}</span>
                <span className="flex items-center gap-2 text-sm text-ink-2">
                  {record.hobbyName}
                  {record.minutes != null ? ` · ${record.minutes}분` : ''}
                </span>
              </span>
            </Link>
          ))
        })}
      </div>

      <div className="flex items-center justify-between">
        <Link to={`/weeks/${detail.previousWeekStart}`} className="inline-flex min-h-11 items-center gap-1 text-base-ink no-underline">
          <ChevronLeftIcon width={16} height={16} />
          이전 주
        </Link>
        {detail.nextWeekStart ? (
          <Link to={`/weeks/${detail.nextWeekStart}`} className="inline-flex min-h-11 items-center gap-1 text-base-ink no-underline">
            다음 주
            <ChevronRightIcon width={16} height={16} />
          </Link>
        ) : (
          <span />
        )}
      </div>
    </>
  )

  const side = (
    <>
      {summary}
      {aiCard}
    </>
  )

  return (
    <div className="flex flex-col gap-5 lg:min-h-0 lg:flex-1">
      <nav className="text-sm text-ink-2">
        <Link to="/records" className="inline-flex items-center gap-1 text-ink-2 no-underline">
          <ChevronLeftIcon width={16} height={16} />
          지난 기록
        </Link>
      </nav>

      <PageHeader title={`${formatMonthDay(detail.weekStart)} – ${formatMonthDay(detail.weekEnd)}`} />

      <div className="lg:hidden">{summary}</div>
      <div className="lg:hidden">{aiCard}</div>

      <Split main={main} side={<div className="hidden lg:flex lg:flex-col lg:gap-5">{side}</div>} className="lg:min-h-0 lg:flex-1" />
    </div>
  )
}
