import { useMemo, useState, type KeyboardEvent, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { BarChart, ChartCard, HBarChart, Legend, type BarItem } from '../components/GrowthCharts'
import { HobbyChipRow } from '../components/HobbyChipRow'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
import { useGrowth } from '../hooks/useGrowth'
import { useHobbies } from '../hooks/useHobbies'
import { useIsDesktop, useMediaQuery } from '../hooks/useIsDesktop'
import { CHART_COLORS } from '../lib/chartColors'
import { countAxis, currentWeekStart, dayTicks, minutesAxis, trimWeeks, weekTicks } from '../lib/chartAxis'
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

const REST_NOTE = '쉬어 간 주도 흐름이에요'

/** 뷰포트 높이가 이보다 낮으면 요약 카드를 한 줄로 줄인다. */
const COMPACT_MAX_HEIGHT = 759

function weekLabel(isoDate: string): string {
  return `${formatMonthDay(isoDate)} 주`
}

function TooFew() {
  return <p className="py-6 text-body-min text-ink-2">기록이 조금 더 쌓이면 보여 드릴게요</p>
}

function Tile({ label, value, compact }: { label: string; value: string; compact: boolean }) {
  return (
    <div
      className={[
        'flex min-w-0 rounded-xl border border-line bg-white px-3',
        compact ? 'items-baseline gap-2 py-1.5' : 'flex-col gap-0.5 py-2',
      ].join(' ')}
    >
      <span className="flex-none text-caption-min text-ink-2">{label}</span>
      <span className="truncate font-serif text-lg font-semibold lg:text-xl">{value}</span>
    </div>
  )
}

/** 웹: 한 화면(페이지 스크롤 없음)에 카드들을 나란히 두고, 카드 높이는 남는 공간으로 정한다. */
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

function OverviewTab({ growth, minSlot, compact }: { growth: Growth; minSlot: number; compact: boolean }) {
  const enough = growth.summary.records >= MIN_RECORDS_FOR_CHARTS
  const weeks = useMemo(() => trimWeeks(growth.weekly), [growth.weekly])
  const thisWeek = currentWeekStart()
  const hasCurrent = weeks.length > 0 && weeks[weeks.length - 1].start === thisWeek
  const ticks = weekTicks(
    weeks.map((week) => week.start),
    hasCurrent,
  )
  const tooltipOf = (week: (typeof weeks)[number]) =>
    `${weekLabel(week.start)} · ${week.days}일 · ${formatDurationMinutes(week.minutes)}${week.start === thisWeek ? ' · 이번 주' : ''}`

  const dayItems: BarItem[] = weeks.map((week, index) => ({
    id: week.start,
    tick: ticks[index],
    parts: [{ value: week.days, color: CHART_COLORS.base }],
    valueText: String(week.days),
    tooltip: tooltipOf(week),
    current: week.start === thisWeek,
  }))
  const timeItems: BarItem[] = weeks.map((week, index) => ({
    id: week.start,
    tick: ticks[index],
    parts: [{ value: week.minutes, color: CHART_COLORS.time }],
    valueText: formatDurationMinutes(week.minutes),
    tooltip: tooltipOf(week),
    current: week.start === thisWeek,
  }))
  const timeAxis = minutesAxis(Math.max(0, ...weeks.map((week) => week.minutes)))

  const caption = (
    <>
      <p className="text-caption-min text-ink-2">{REST_NOTE}</p>
      {hasCurrent ? (
        <Legend items={[{ label: '이번 주(아직 진행 중이라 연하게 표시해요)', color: CHART_COLORS.base, faded: true }]} />
      ) : null}
    </>
  )

  return (
    <div className="flex flex-col gap-3 lg:min-h-0 lg:flex-1 lg:gap-3">
      <div className={['grid flex-none grid-cols-2 gap-2 lg:grid-cols-4 lg:gap-3'].join(' ')}>
        <Tile compact={compact} label="기록한 날" value={`${growth.summary.recordedDays}일`} />
        <Tile compact={compact} label="기록" value={`${growth.summary.records}개`} />
        <Tile compact={compact} label="쌓인 시간" value={formatDurationMinutes(growth.summary.minutes)} />
        <Tile
          compact={compact}
          label="첫 기록일"
          value={growth.summary.firstRecordDate ? formatYearMonthDay(growth.summary.firstRecordDate) : '아직 없어요'}
        />
      </div>
      <Panel>
        <ChartCard
          title="주별 기록한 날"
          caption={enough ? caption : undefined}
          table={
            enough
              ? {
                  headers: ['주', '기록한 날'],
                  numeric: [1],
                  rows: weeks.map((week) => [weekLabel(week.start), `${week.days}일`]),
                }
              : undefined
          }
          chart={
            enough ? (
              <BarChart
                items={dayItems}
                yMax={7}
                yTicks={[0, 7]}
                formatTick={(value) => `${value}일`}
                minSlot={minSlot}
                ariaLabel={`주별 기록한 날 막대 그래프, ${weeks.length}주`}
              />
            ) : (
              <TooFew />
            )
          }
        />
        <ChartCard
          title="주별 쌓인 시간"
          caption={enough ? caption : undefined}
          table={
            enough
              ? {
                  headers: ['주', '쌓인 시간'],
                  numeric: [1],
                  rows: weeks.map((week) => [weekLabel(week.start), formatDurationMinutes(week.minutes)]),
                }
              : undefined
          }
          chart={
            enough ? (
              <BarChart
                items={timeItems}
                yMax={timeAxis.yMax}
                yTicks={timeAxis.ticks}
                formatTick={formatDurationMinutes}
                minSlot={minSlot}
                ariaLabel={`주별 쌓인 시간 막대 그래프, ${weeks.length}주`}
              />
            ) : (
              <TooFew />
            )
          }
        />
      </Panel>
      <Link
        to="/changes"
        className="inline-flex min-h-11 w-fit flex-none items-center rounded-full px-2 -mx-2 text-sm font-medium text-base-ink no-underline hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)] focus-visible:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)] lg:min-h-8"
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
      <ChartCard
        title="분야별 기록 수"
        scrollBody
        table={
          enough
            ? {
                headers: ['분야', '기록', '첫 기록일'],
                numeric: [1],
                rows: growth.byHobby.map((hobby) => [
                  hobbyName(hobby.name, hobby.archived),
                  `${hobby.records}개`,
                  hobby.firstDate ? formatYearMonthDay(hobby.firstDate) : '-',
                ]),
              }
            : undefined
        }
        chart={
          enough ? (
            <HBarChart
              ariaLabel="분야별 기록 수 가로 막대 그래프"
              items={growth.byHobby.map((hobby) => ({
                label: hobbyName(hobby.name, hobby.archived),
                value: hobby.records,
                valueText: `${hobby.records}개`,
                muted: hobby.archived,
              }))}
            />
          ) : (
            <TooFew />
          )
        }
      />
      <ChartCard
        title="분야별 쌓인 시간"
        scrollBody
        table={
          enough
            ? {
                headers: ['분야', '쌓인 시간'],
                numeric: [1],
                rows: growth.byHobby.map((hobby) => [
                  hobbyName(hobby.name, hobby.archived),
                  formatDurationMinutes(hobby.minutes),
                ]),
              }
            : undefined
        }
        chart={
          enough ? (
            <div className="flex flex-col gap-3">
              <HBarChart
                color={CHART_COLORS.time}
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
            </div>
          ) : (
            <TooFew />
          )
        }
      />
    </Panel>
  )
}

function FeelingTab({ growth, minSlot }: { growth: Growth; minSlot: number }) {
  const enough = growth.summary.records >= MIN_RECORDS_FOR_CHARTS
  const { buckets, byFeeling, totals } = growth.feelings
  const daily = buckets.length > 0 && buckets[0].start === buckets[0].end
  const thisWeek = currentWeekStart()
  const hasCurrent = !daily && buckets.length > 0 && buckets[buckets.length - 1].start === thisWeek
  const ticks = daily
    ? dayTicks(buckets.map((bucket) => bucket.start))
    : weekTicks(
        buckets.map((bucket) => bucket.start),
        hasCurrent,
      )
  const items: BarItem[] = buckets.map((bucket, index) => ({
    id: bucket.start,
    tick: ticks[index],
    parts: [
      { value: bucket.good, color: CHART_COLORS.base },
      { value: bucket.neutral, color: CHART_COLORS.neutral },
      { value: bucket.hard, color: CHART_COLORS.hard },
    ],
    tooltip: `${daily ? formatMonthDay(bucket.start) : weekLabel(bucket.start)} · ${GOOD_LABEL} ${bucket.good} · ${NEUTRAL_LABEL} ${bucket.neutral} · ${HARD_LABEL} ${bucket.hard}${!daily && bucket.start === thisWeek ? ' · 이번 주' : ''}`,
    current: !daily && bucket.start === thisWeek,
  }))
  const axis = countAxis(Math.max(0, ...buckets.map((bucket) => bucket.good + bucket.neutral + bucket.hard)))
  const legend = (
    <Legend
      items={[
        { label: GOOD_LABEL, color: CHART_COLORS.base },
        { label: NEUTRAL_LABEL, color: CHART_COLORS.neutral },
        { label: HARD_LABEL, color: CHART_COLORS.hard },
      ]}
    />
  )
  return (
    <Panel>
      <ChartCard
        title={daily ? '날짜별 감정' : '주별 감정'}
        caption={
          enough ? (
            <>
              <p className="text-body-min">
                이 기간 기록 {growth.summary.records}개 중 {GOOD_LABEL} 쪽 {totals.good}개, {NEUTRAL_LABEL} {totals.neutral}개,{' '}
                {HARD_LABEL} {totals.hard}개예요.
              </p>
              {legend}
            </>
          ) : undefined
        }
        table={
          enough
            ? {
                headers: [daily ? '날짜' : '주', GOOD_LABEL, NEUTRAL_LABEL, HARD_LABEL, '고르지 않음'],
                numeric: [1, 2, 3, 4],
                rows: buckets.map((bucket) => [
                  daily ? formatMonthDay(bucket.start) : weekLabel(bucket.start),
                  String(bucket.good),
                  String(bucket.neutral),
                  String(bucket.hard),
                  String(bucket.none),
                ]),
              }
            : undefined
        }
        chart={
          enough ? (
            <BarChart
              items={items}
              yMax={axis.yMax}
              yTicks={axis.ticks}
              formatTick={String}
              minSlot={daily ? Math.max(minSlot - 6, 14) : minSlot}
              ariaLabel={`${daily ? '날짜별' : '주별'} 감정 쌓은 막대 그래프. 막대 높이는 그 구간의 기록 수예요`}
            />
          ) : (
            <TooFew />
          )
        }
      />
      <ChartCard
        title="자주 쓴 감정"
        scrollBody
        caption={
          <>
            {legend}
            {totals.none > 0 ? (
              <p className="text-caption-min text-ink-2">감정을 고르지 않은 기록은 {totals.none}개예요.</p>
            ) : null}
          </>
        }
        chart={
          byFeeling.length === 0 ? (
            <p className="py-6 text-body-min text-ink-2">이 기간에 고른 감정이 아직 없어요</p>
          ) : (
            <ul className="flex flex-wrap content-start gap-2" aria-label="자주 쓴 감정">
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
          )
        }
      />
    </Panel>
  )
}

function RhythmTab({ growth, minSlot }: { growth: Growth; minSlot: number }) {
  const enough = growth.summary.records >= MIN_RECORDS_FOR_CHARTS
  const axis = countAxis(Math.max(0, ...growth.weekday))
  const items: BarItem[] = WEEKDAY_LABELS.map((label, index) => ({
    id: label,
    tick: label,
    parts: [{ value: growth.weekday[index] ?? 0, color: CHART_COLORS.base }],
    valueText: String(growth.weekday[index] ?? 0),
    tooltip: `${label}요일 · ${growth.weekday[index] ?? 0}개`,
  }))
  return (
    <Panel columns={3}>
      <ChartCard
        title="요일별 기록"
        table={
          enough
            ? {
                headers: ['요일', '기록'],
                numeric: [1],
                rows: WEEKDAY_LABELS.map((label, index) => [`${label}요일`, `${growth.weekday[index] ?? 0}개`]),
              }
            : undefined
        }
        chart={
          enough ? (
            <BarChart
              items={items}
              yMax={axis.yMax}
              yTicks={axis.ticks}
              formatTick={String}
              minSlot={Math.max(minSlot, 28)}
              ariaLabel="요일별 기록 수 막대 그래프"
            />
          ) : (
            <TooFew />
          )
        }
      />
      <ChartCard
        title="자주 하는 주제"
        scrollBody
        table={
          growth.topics.length > 0
            ? {
                headers: ['주제', '횟수'],
                numeric: [1],
                rows: growth.topics.map((topic) => [topic.tag, `${topic.count}번`]),
              }
            : undefined
        }
        chart={
          growth.topics.length === 0 ? (
            <p className="py-6 text-body-min text-ink-2">AI가 정리한 기록이 쌓이면 주제가 보여요</p>
          ) : (
            <HBarChart
              ariaLabel="자주 하는 주제 가로 막대 그래프"
              items={growth.topics.map((topic) => ({
                label: topic.tag,
                value: topic.count,
                valueText: `${topic.count}번`,
              }))}
            />
          )
        }
      />
      <ChartCard
        title="의미 있는 날"
        scrollBody
        chart={
          growth.milestones.length === 0 ? (
            <p className="py-6 text-body-min text-ink-2">기록할 때 &lsquo;의미 있는 날&rsquo;로 표시하면 여기에 모여요</p>
          ) : (
            <ol className="flex flex-col" aria-label="의미 있는 날 목록">
              {growth.milestones.map((milestone) => (
                <li
                  key={milestone.recordId}
                  className="border-l-2 border-[color-mix(in_srgb,var(--color-base)_40%,white)] pl-3"
                >
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
          )
        }
      />
    </Panel>
  )
}

function segmentClass(active: boolean): string {
  return [
    'inline-flex min-h-11 flex-none items-center justify-center whitespace-nowrap rounded-md px-3 text-sm font-medium lg:min-h-9',
    active ? 'bg-ink text-white' : 'bg-transparent text-ink-2',
  ].join(' ')
}

export function GrowthPage() {
  const { data: hobbies } = useHobbies(true)
  const [filter, setFilter] = useState<GrowthFilter>(readGrowthFilter)
  const [tab, setTab] = useState<TabKey>('overview')
  const isDesktop = useIsDesktop()
  const lowHeight = useMediaQuery(`(max-height: ${COMPACT_MAX_HEIGHT}px)`)
  // 막대 하나가 차지하는 최소 칸: 웹 12px(막대 8px+여백), 모바일 24px(막대당 20px 이상).
  const minSlot = isDesktop ? 12 : 24

  const list = hobbies ?? []
  // 지금 없는 분야가 저장돼 있으면 전체로 본다(분야를 지웠거나 다른 계정일 때).
  const hobbyId =
    hobbies && filter.hobbyId != null && !hobbies.some((hobby) => hobby.id === filter.hobbyId) ? null : filter.hobbyId
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
    body = <OverviewTab growth={growth} minSlot={minSlot} compact={isDesktop && lowHeight} />
  } else if (tab === 'hobby') {
    body = <HobbyTab growth={growth} />
  } else if (tab === 'feeling') {
    body = <FeelingTab growth={growth} minSlot={minSlot} />
  } else {
    body = <RhythmTab growth={growth} minSlot={minSlot} />
  }

  return (
    <div className="flex flex-col gap-3 lg:min-h-0 lg:flex-1 lg:gap-3">
      <div className="flex flex-col gap-2 lg:flex-row lg:items-center lg:justify-between">
        <h1 className="text-page-title">성장</h1>
        <div
          role="tablist"
          aria-label="성장 보기"
          onKeyDown={onTabKey}
          className="scrollbar-none flex max-w-full flex-none gap-1 overflow-x-auto rounded-lg border border-line-2 bg-white p-1"
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
              className={[segmentClass(tab === item.key), 'flex-1 lg:flex-none'].join(' ')}
              onClick={() => setTab(item.key)}
            >
              {item.label}
            </button>
          ))}
        </div>
      </div>

      <div className="flex flex-col gap-2 lg:flex-row lg:items-center lg:gap-4">
        {/* 칩 줄은 안쪽에 0.5rem 패딩이 있어, 같은 크기만큼 당겨서 제목·카드의 왼쪽 끝에 맞춘다. */}
        <div className="-ml-2 min-w-0 lg:flex-1">
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
          className="scrollbar-none flex max-w-full flex-none gap-1 overflow-x-auto rounded-lg border border-line-2 bg-white p-1"
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
        id="growth-panel"
        role="tabpanel"
        aria-labelledby={`growth-tab-${tab}`}
        className="flex flex-col gap-3 lg:min-h-0 lg:flex-1"
      >
        {body}
      </div>
    </div>
  )
}
