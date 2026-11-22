import { useState, type KeyboardEvent, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import {
  ChartCard,
  ChartTable,
  HBarChart,
  Legend,
  StackedBarChart,
  VBarChart,
} from '../components/GrowthCharts'
import { CHART_COLORS } from '../lib/chartColors'
import { HobbyChipRow } from '../components/HobbyChipRow'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
import { useGrowth } from '../hooks/useGrowth'
import { useHobbies } from '../hooks/useHobbies'
import { formatDurationMinutes, formatMonthDay, formatYearMonthDay } from '../lib/date'
import {
  PERIOD_OPTIONS,
  readGrowthFilter,
  writeGrowthFilter,
  type Growth,
  type GrowthFilter,
  type GrowthPeriod,
} from '../lib/growth'

type TabKey = 'overview' | 'hobby' | 'feeling' | 'rhythm'

const TABS: { key: TabKey; label: string }[] = [
  { key: 'overview', label: '한눈에' },
  { key: 'hobby', label: '분야' },
  { key: 'feeling', label: '감정' },
  { key: 'rhythm', label: '리듬' },
]

const WEEKDAY_LABELS = ['월', '화', '수', '목', '금', '토', '일']

/** 차트가 의미 있으려면 기록이 어느 정도는 있어야 한다. */
const MIN_RECORDS_FOR_CHARTS = 3

const GOOD_LABEL = '좋아요'
const NEUTRAL_LABEL = '보통이에요'
const HARD_LABEL = '힘들어요'

const DIRECTION_LABEL = { GOOD: GOOD_LABEL, NEUTRAL: NEUTRAL_LABEL, HARD: HARD_LABEL } as const
const DIRECTION_COLOR = { GOOD: CHART_COLORS.base, NEUTRAL: CHART_COLORS.neutral, HARD: CHART_COLORS.hard } as const

function monthDayShort(isoDate: string): string {
  const [, month, day] = isoDate.split('-')
  return `${Number(month)}/${Number(day)}`
}

function TooFew() {
  return <p className="py-6 text-body-min text-ink-2">기록이 조금 더 쌓이면 보여 드릴게요</p>
}

function Tile({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex min-w-0 flex-col gap-1 rounded-xl border border-line bg-white px-4 py-3">
      <span className="text-caption-min text-ink-2">{label}</span>
      <span className="truncate font-serif text-xl font-semibold lg:text-2xl">{value}</span>
    </div>
  )
}

/** 웹: 한 화면(페이지 스크롤 없음)에 카드들을 나란히 두고, 넘치는 건 카드 안에서 스크롤한다. */
function Panel({ children, columns = 2 }: { children: ReactNode; columns?: 2 | 3 }) {
  return (
    <div
      className={[
        'grid gap-4 lg:min-h-0 lg:flex-1 lg:grid-rows-[minmax(0,1fr)]',
        columns === 3 ? 'lg:grid-cols-3' : 'lg:grid-cols-2',
      ].join(' ')}
    >
      {children}
    </div>
  )
}

function OverviewTab({ growth }: { growth: Growth }) {
  const enough = growth.summary.records >= MIN_RECORDS_FOR_CHARTS
  const items = growth.weekly.map((week) => ({ label: monthDayShort(week.start), value: week.days }))
  const minuteItems = growth.weekly.map((week) => ({ label: monthDayShort(week.start), value: week.minutes }))
  return (
    <div className="flex flex-col gap-4 lg:min-h-0 lg:flex-1">
      <div className="grid flex-none grid-cols-2 gap-3 lg:grid-cols-4">
        <Tile label="기록한 날" value={`${growth.summary.recordedDays}일`} />
        <Tile label="기록" value={`${growth.summary.records}개`} />
        <Tile label="쌓인 시간" value={formatDurationMinutes(growth.summary.minutes)} />
        <Tile
          label="첫 기록일"
          value={growth.summary.firstRecordDate ? formatYearMonthDay(growth.summary.firstRecordDate) : '아직 없어요'}
        />
      </div>
      <Panel>
        <ChartCard title="주별 기록한 날">
          {enough ? (
            <>
              <VBarChart items={items} unit="일" ariaLabel={`주별 기록한 날 막대 그래프, ${items.length}주`} />
              <p className="text-caption-min text-ink-2">쉬어 간 주도 흐름이에요</p>
              <ChartTable
                caption="주별 기록한 날"
                headers={['주 시작', '기록한 날']}
                rows={growth.weekly.map((week) => [formatMonthDay(week.start), `${week.days}일`])}
              />
            </>
          ) : (
            <TooFew />
          )}
        </ChartCard>
        <ChartCard title="주별 쌓인 시간">
          {enough ? (
            <>
              <VBarChart
                items={minuteItems}
                unit="분"
                color={CHART_COLORS.neutral}
                ariaLabel={`주별 쌓인 시간 막대 그래프, ${minuteItems.length}주`}
              />
              <ChartTable
                caption="주별 쌓인 시간"
                headers={['주 시작', '쌓인 시간']}
                rows={growth.weekly.map((week) => [formatMonthDay(week.start), formatDurationMinutes(week.minutes)])}
              />
            </>
          ) : (
            <TooFew />
          )}
        </ChartCard>
      </Panel>
      <Link
        to="/changes"
        className="inline-flex min-h-11 w-fit flex-none items-center rounded-full px-2 -mx-2 text-sm font-medium text-base-ink no-underline hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)] focus-visible:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]"
      >
        변화 지표 자세히 보기
      </Link>
    </div>
  )
}

function HobbyTab({ growth }: { growth: Growth }) {
  const enough = growth.summary.records >= MIN_RECORDS_FOR_CHARTS
  const hobbyName = (name: string, archived: boolean) => (archived ? `${name} (보관)` : name)
  return (
    <Panel>
      <ChartCard title="분야별 기록 수">
        {enough ? (
          <>
            <HBarChart
              ariaLabel="분야별 기록 수 가로 막대 그래프"
              items={growth.byHobby.map((hobby) => ({
                label: hobbyName(hobby.name, hobby.archived),
                value: hobby.records,
                valueText: `${hobby.records}개`,
                muted: hobby.archived,
              }))}
            />
            <ChartTable
              caption="분야별 기록 수"
              headers={['분야', '기록', '첫 기록일']}
              rows={growth.byHobby.map((hobby) => [
                hobbyName(hobby.name, hobby.archived),
                `${hobby.records}개`,
                hobby.firstDate ? formatYearMonthDay(hobby.firstDate) : '-',
              ])}
            />
          </>
        ) : (
          <TooFew />
        )}
      </ChartCard>
      <ChartCard title="분야별 쌓인 시간">
        {enough ? (
          <>
            <HBarChart
              color={CHART_COLORS.neutral}
              ariaLabel="분야별 쌓인 시간 가로 막대 그래프"
              items={growth.byHobby.map((hobby) => ({
                label: hobbyName(hobby.name, hobby.archived),
                value: hobby.minutes,
                valueText: formatDurationMinutes(hobby.minutes),
                muted: hobby.archived,
              }))}
            />
            <ul className="flex flex-col gap-1 text-caption-min text-ink-2" aria-label="분야별 첫 기록일">
              {growth.byHobby.map((hobby) => (
                <li key={hobby.hobbyId} className={hobby.archived ? 'opacity-60' : ''}>
                  {hobbyName(hobby.name, hobby.archived)} · 첫 기록{' '}
                  {hobby.firstDate ? formatYearMonthDay(hobby.firstDate) : '-'}
                </li>
              ))}
            </ul>
            <ChartTable
              caption="분야별 쌓인 시간"
              headers={['분야', '쌓인 시간']}
              rows={growth.byHobby.map((hobby) => [
                hobbyName(hobby.name, hobby.archived),
                formatDurationMinutes(hobby.minutes),
              ])}
            />
          </>
        ) : (
          <TooFew />
        )}
      </ChartCard>
    </Panel>
  )
}

function FeelingTab({ growth }: { growth: Growth }) {
  const enough = growth.summary.records >= MIN_RECORDS_FOR_CHARTS
  const { buckets, byFeeling, totals } = growth.feelings
  const daily = buckets.length > 0 && buckets[0].start === buckets[0].end
  const items = buckets.map((bucket) => ({
    label: daily ? String(Number(bucket.start.slice(8))) : monthDayShort(bucket.start),
    good: bucket.good,
    neutral: bucket.neutral,
    hard: bucket.hard,
  }))
  return (
    <Panel>
      <ChartCard title={daily ? '날짜별 감정' : '주별 감정'}>
        {enough ? (
          <>
            <p className="text-body-min">
              이 기간 기록 {growth.summary.records}개 중 {GOOD_LABEL} 쪽 {totals.good}개, {NEUTRAL_LABEL} {totals.neutral}개,{' '}
              {HARD_LABEL} {totals.hard}개예요.
            </p>
            <StackedBarChart
              items={items}
              ariaLabel={`${daily ? '날짜별' : '주별'} 감정 쌓은 막대 그래프. 막대 높이는 그 구간의 기록 수예요`}
            />
            <Legend
              items={[
                { label: GOOD_LABEL, color: CHART_COLORS.base },
                { label: NEUTRAL_LABEL, color: CHART_COLORS.neutral },
                { label: HARD_LABEL, color: CHART_COLORS.hard },
              ]}
            />
            <ChartTable
              caption={daily ? '날짜별 감정' : '주별 감정'}
              headers={[daily ? '날짜' : '주 시작', GOOD_LABEL, NEUTRAL_LABEL, HARD_LABEL, '고르지 않음']}
              rows={buckets.map((bucket) => [
                formatMonthDay(bucket.start),
                String(bucket.good),
                String(bucket.neutral),
                String(bucket.hard),
                String(bucket.none),
              ])}
            />
          </>
        ) : (
          <TooFew />
        )}
      </ChartCard>
      <ChartCard title="자주 쓴 감정">
        {byFeeling.length === 0 ? (
          <p className="py-6 text-body-min text-ink-2">이 기간에 고른 감정이 아직 없어요</p>
        ) : (
          <ul className="flex flex-wrap gap-2" aria-label="자주 쓴 감정">
            {byFeeling.map((feeling) => (
              <li
                key={`${feeling.builtIn}-${feeling.label}`}
                className="inline-flex min-h-9 items-center gap-2 rounded-full border border-line-2 bg-white px-3 text-sm"
              >
                <span
                  aria-hidden="true"
                  className="inline-block h-2.5 w-2.5 rounded-full"
                  style={{ background: DIRECTION_COLOR[feeling.direction] }}
                />
                {feeling.label}
                <span className="text-ink-2">{feeling.count}번</span>
                <span className="sr-only">{DIRECTION_LABEL[feeling.direction]} 쪽 감정</span>
              </li>
            ))}
          </ul>
        )}
        <Legend
          items={[
            { label: GOOD_LABEL, color: CHART_COLORS.base },
            { label: NEUTRAL_LABEL, color: CHART_COLORS.neutral },
            { label: HARD_LABEL, color: CHART_COLORS.hard },
          ]}
        />
        {totals.none > 0 ? (
          <p className="text-caption-min text-ink-2">감정을 고르지 않은 기록은 {totals.none}개예요.</p>
        ) : null}
      </ChartCard>
    </Panel>
  )
}

function RhythmTab({ growth }: { growth: Growth }) {
  const enough = growth.summary.records >= MIN_RECORDS_FOR_CHARTS
  return (
    <Panel columns={3}>
      <ChartCard title="요일별 기록">
        {enough ? (
          <>
            <VBarChart
              height={190}
              items={WEEKDAY_LABELS.map((label, index) => ({ label, value: growth.weekday[index] ?? 0 }))}
              ariaLabel="요일별 기록 수 막대 그래프"
              unit="개"
            />
            <ChartTable
              caption="요일별 기록"
              headers={['요일', '기록']}
              rows={WEEKDAY_LABELS.map((label, index) => [label, `${growth.weekday[index] ?? 0}개`])}
            />
          </>
        ) : (
          <TooFew />
        )}
      </ChartCard>
      <ChartCard title="자주 하는 주제">
        {growth.topics.length === 0 ? (
          <p className="py-6 text-body-min text-ink-2">AI가 정리한 기록이 쌓이면 주제가 보여요</p>
        ) : (
          <>
            <HBarChart
              ariaLabel="자주 하는 주제 가로 막대 그래프"
              items={growth.topics.map((topic) => ({
                label: topic.tag,
                value: topic.count,
                valueText: `${topic.count}번`,
              }))}
            />
            <ChartTable
              caption="자주 하는 주제"
              headers={['주제', '횟수']}
              rows={growth.topics.map((topic) => [topic.tag, `${topic.count}번`])}
            />
          </>
        )}
      </ChartCard>
      <ChartCard title="의미 있는 날">
        {growth.milestones.length === 0 ? (
          <p className="py-6 text-body-min text-ink-2">기록할 때 &lsquo;의미 있는 날&rsquo;로 표시하면 여기에 모여요</p>
        ) : (
          <ol className="flex flex-col" aria-label="의미 있는 날 목록">
            {growth.milestones.map((milestone) => (
              <li key={milestone.recordId} className="border-l-2 border-[color-mix(in_srgb,var(--color-base)_40%,white)] pl-3">
                <Link
                  to={`/records/${milestone.recordId}`}
                  className="flex min-h-11 flex-col justify-center rounded-lg py-1.5 text-ink no-underline hover:bg-surface focus-visible:bg-surface"
                >
                  <span className="text-caption-min text-ink-2">
                    {formatYearMonthDay(milestone.date)} · {milestone.hobbyName}
                  </span>
                  <span className="font-serif text-body-min">{milestone.preview}</span>
                </Link>
              </li>
            ))}
          </ol>
        )}
      </ChartCard>
    </Panel>
  )
}

function segmentClass(active: boolean): string {
  return [
    'inline-flex min-h-11 flex-1 items-center justify-center whitespace-nowrap rounded-md px-3 text-sm font-medium lg:min-h-9 lg:flex-none',
    active ? 'bg-ink text-white' : 'bg-transparent text-ink-2',
  ].join(' ')
}

export function GrowthPage() {
  const { data: hobbies } = useHobbies(true)
  const [filter, setFilter] = useState<GrowthFilter>(readGrowthFilter)
  const [tab, setTab] = useState<TabKey>('overview')

  const list = hobbies ?? []
  // 지금 없는 분야가 저장돼 있으면 전체로 본다(분야를 지웠거나 다른 계정일 때).
  const hobbyId = hobbies && filter.hobbyId != null && !hobbies.some((hobby) => hobby.id === filter.hobbyId)
    ? null
    : filter.hobbyId
  const { data: growth, isLoading, isError } = useGrowth(filter.period, hobbyId)

  function update(next: GrowthFilter) {
    setFilter(next)
    writeGrowthFilter(next)
  }

  function onTabKey(event: KeyboardEvent<HTMLDivElement>) {
    const index = TABS.findIndex((item) => item.key === tab)
    if (event.key === 'ArrowRight') setTab(TABS[(index + 1) % TABS.length].key)
    else if (event.key === 'ArrowLeft') setTab(TABS[(index + TABS.length - 1) % TABS.length].key)
    else return
    event.preventDefault()
  }

  let body: ReactNode
  if (isError) {
    body = <Note variant="sand">성장 기록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</Note>
  } else if (isLoading || !growth) {
    body = <Loading />
  } else if (tab === 'overview') {
    body = <OverviewTab growth={growth} />
  } else if (tab === 'hobby') {
    body = <HobbyTab growth={growth} />
  } else if (tab === 'feeling') {
    body = <FeelingTab growth={growth} />
  } else {
    body = <RhythmTab growth={growth} />
  }

  return (
    <div className="flex flex-col gap-4 lg:min-h-0 lg:flex-1">
      <h1 className="text-page-title">성장</h1>

      <div className="flex flex-col gap-2 lg:flex-row lg:items-center lg:gap-4">
        <div className="min-w-0 lg:flex-1">
          <HobbyChipRow
            hobbies={list}
            selectedId={hobbyId}
            onSelect={(id) => update({ ...filter, hobbyId: id })}
            allLabel="전체"
            onSelectAll={() => update({ ...filter, hobbyId: null })}
          />
        </div>
        <div
          role="radiogroup"
          aria-label="기간"
          className="flex flex-none gap-1 rounded-lg border border-line-2 bg-white p-1"
        >
          {PERIOD_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              role="radio"
              aria-checked={filter.period === option.value}
              className={segmentClass(filter.period === option.value)}
              onClick={() => update({ ...filter, period: option.value as GrowthPeriod })}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>

      <div
        role="tablist"
        aria-label="성장 보기"
        onKeyDown={onTabKey}
        className="flex flex-none gap-1 rounded-lg border border-line-2 bg-white p-1 lg:w-fit"
      >
        {TABS.map((item) => (
          <button
            key={item.key}
            type="button"
            role="tab"
            id={`growth-tab-${item.key}`}
            aria-selected={tab === item.key}
            aria-controls="growth-panel"
            tabIndex={tab === item.key ? 0 : -1}
            className={segmentClass(tab === item.key)}
            onClick={() => setTab(item.key)}
          >
            {item.label}
          </button>
        ))}
      </div>

      <div
        id="growth-panel"
        role="tabpanel"
        aria-labelledby={`growth-tab-${tab}`}
        className="flex flex-col gap-4 lg:min-h-0 lg:flex-1"
      >
        {body}
      </div>
    </div>
  )
}
