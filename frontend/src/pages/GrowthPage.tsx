import { useMemo, useState, type KeyboardEvent, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { BarChart, ChartCard, HBarChart, Legend, Swatch, type BarItem } from '../components/GrowthCharts'
import { Dropdown } from '../components/Dropdown'
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
  filterTopics,
  readGrowthFilter,
  writeGrowthFilter,
  type Growth,
  type GrowthFilter,
  type GrowthPeriod,
} from '../lib/growth'

type TabKey = 'overview' | 'hobby' | 'feeling' | 'rhythm'

const TABS: { key: TabKey; label: string }[] = [
  { key: 'overview', label: '한눈에' },
  { key: 'hobby', label: '분야별' },
  { key: 'feeling', label: '감정' },
  { key: 'rhythm', label: '요일·주제' },
]

const WEEKDAY_LABELS = ['월', '화', '수', '목', '금', '토', '일']

/** 차트가 의미 있으려면 기록이 어느 정도는 있어야 한다. */
const MIN_RECORDS_FOR_CHARTS = 3

const GOOD_LABEL = '좋아요'
const NEUTRAL_LABEL = '보통이에요'
const HARD_LABEL = '힘들어요'

const DIRECTION_LABEL = { GOOD: GOOD_LABEL, NEUTRAL: NEUTRAL_LABEL, HARD: HARD_LABEL } as const
const DIRECTION_COLOR = { GOOD: CHART_COLORS.good, NEUTRAL: CHART_COLORS.neutral, HARD: CHART_COLORS.hard } as const

const MAX_HOBBY_ROWS = 8
const MAX_TOPICS = 6
const MIN_TOPICS = 3

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
      <span className="truncate t-section">{value}</span>
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
        className="inline-flex min-h-11 w-fit flex-none items-center rounded-full px-2 -mx-2 t-sub font-medium text-base-ink no-underline hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)] focus-visible:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)] lg:min-h-8"
      >
        변화 지표 자세히 보기
      </Link>
    </div>
  )
}

function firstDateLabel(isoDate: string | null): string {
  if (!isoDate) return ''
  const sameYear = isoDate.slice(0, 4) === String(new Date().getFullYear())
  return `첫 기록 ${sameYear ? formatMonthDay(isoDate) : formatYearMonthDay(isoDate)}`
}

type HobbyMetric = 'records' | 'minutes'

function HobbyTab({ growth }: { growth: Growth }) {
  const enough = growth.summary.records >= MIN_RECORDS_FOR_CHARTS
  const [metric, setMetric] = useState<HobbyMetric>('records')
  const hobbyName = (name: string, archived: boolean) => (archived ? `${name} (보관)` : name)

  // 기록 0개 분야는 뺀다. 선택한 기준으로 내림차순, 같으면 다른 기준으로.
  const sorted = useMemo(() => {
    const other: HobbyMetric = metric === 'records' ? 'minutes' : 'records'
    return growth.byHobby
      .filter((hobby) => hobby.records > 0)
      .sort((a, b) => b[metric] - a[metric] || b[other] - a[other])
  }, [growth.byHobby, metric])
  const shown = sorted.slice(0, MAX_HOBBY_ROWS)
  const max = Math.max(1, ...shown.map((hobby) => hobby[metric]))

  const toggle = (
    <div role="radiogroup" aria-label="분야별 기준" className="flex gap-0.5 rounded-lg border border-line-2 bg-white p-0.5">
      {(
        [
          ['records', '기록 수'],
          ['minutes', '쌓인 시간'],
        ] as const
      ).map(([key, label]) => (
        <button
          key={key}
          type="button"
          role="radio"
          aria-checked={metric === key}
          onClick={() => setMetric(key)}
          className={[
            'inline-flex min-h-11 items-center justify-center whitespace-nowrap rounded-md px-3 text-caption-min font-medium lg:min-h-8',
            metric === key ? 'bg-ink text-white' : 'text-ink-2',
          ].join(' ')}
        >
          {label}
        </button>
      ))}
    </div>
  )

  return (
    <div className="grid gap-4 lg:min-h-0 lg:flex-1 lg:grid-rows-[minmax(0,1fr)]">
      <ChartCard
        title="분야별 기록"
        scrollBody
        headerExtra={toggle}
        table={
          enough
            ? {
                headers: ['분야', '기록', '쌓인 시간', '첫 기록일'],
                numeric: [1, 2],
                rows: sorted.map((hobby) => [
                  hobbyName(hobby.name, hobby.archived),
                  `${hobby.records}개`,
                  formatDurationMinutes(hobby.minutes),
                  hobby.firstDate ? formatYearMonthDay(hobby.firstDate) : '-',
                ]),
              }
            : undefined
        }
        caption={
          enough && sorted.length > MAX_HOBBY_ROWS ? (
            <p className="text-caption-min text-ink-2">나머지는 표로 볼 수 있어요</p>
          ) : undefined
        }
        chart={
          enough ? (
            <ul className="flex flex-col gap-3" aria-label={metric === 'records' ? '분야별 기록 수' : '분야별 쌓인 시간'}>
              {shown.map((hobby) => (
                // 분야명·값 열을 고정 폭으로 둬서 모든 행의 트랙 시작·끝이 세로로 맞는다.
                <li
                  key={hobby.hobbyId}
                  className={[
                    'grid grid-cols-[6.5rem_minmax(0,1fr)_7.5rem] items-center gap-x-3 lg:grid-cols-[8.5rem_minmax(0,1fr)_9.5rem]',
                    hobby.archived ? 'opacity-60' : '',
                  ].join(' ')}
                >
                  <div className="min-w-0">
                    <p className="truncate t-sub text-ink" title={hobby.name}>
                      {hobbyName(hobby.name, hobby.archived)}
                    </p>
                    {hobby.firstDate ? (
                      <p className="truncate t-cap text-ink-2">{firstDateLabel(hobby.firstDate)}</p>
                    ) : null}
                  </div>
                  <span className="block h-3 overflow-hidden rounded-full bg-[var(--track)]" aria-hidden="true">
                    <span
                      className="block h-3 rounded-full"
                      style={{
                        width: `${hobby[metric] > 0 ? Math.max(4, (hobby[metric] / max) * 100) : 0}%`,
                        background: metric === 'records' ? CHART_COLORS.base : CHART_COLORS.time,
                        visibility: hobby[metric] > 0 ? 'visible' : 'hidden',
                      }}
                    />
                  </span>
                  <span className="text-right t-sub tabular-nums text-ink">
                    {hobby.records}개 · {formatDurationMinutes(hobby.minutes)}
                  </span>
                </li>
              ))}
            </ul>
          ) : (
            <TooFew />
          )
        }
      />
    </div>
  )
}

function FeelingTab({ growth, minSlot }: { growth: Growth; minSlot: number }) {
  const enough = growth.summary.records >= MIN_RECORDS_FOR_CHARTS
  const { buckets, byFeeling, totals } = growth.feelings
  const counted = totals.good + totals.neutral + totals.hard
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
      { value: bucket.good, color: CHART_COLORS.good },
      { value: bucket.neutral, color: CHART_COLORS.neutral },
      { value: bucket.hard, color: CHART_COLORS.hard },
    ],
    valueText: String(bucket.good + bucket.neutral + bucket.hard),
    tooltip: `${daily ? formatMonthDay(bucket.start) : weekLabel(bucket.start)} · ${GOOD_LABEL} ${bucket.good} · ${NEUTRAL_LABEL} ${bucket.neutral} · ${HARD_LABEL} ${bucket.hard}${!daily && bucket.start === thisWeek ? ' · 이번 주' : ''}`,
    tooltipTitle: `${daily ? formatMonthDay(bucket.start) : weekLabel(bucket.start)}${!daily && bucket.start === thisWeek ? ' · 이번 주' : ''}`,
    tooltipRows: [
      { label: GOOD_LABEL, value: bucket.good, color: CHART_COLORS.good },
      { label: NEUTRAL_LABEL, value: bucket.neutral, color: CHART_COLORS.neutral },
      { label: HARD_LABEL, value: bucket.hard, color: CHART_COLORS.hard },
    ],
    current: !daily && bucket.start === thisWeek,
  }))
  const axis = countAxis(Math.max(0, ...buckets.map((bucket) => bucket.good + bucket.neutral + bucket.hard)))

  return (
    <div className="grid gap-4 lg:min-h-0 lg:flex-1 lg:grid-rows-[minmax(0,1fr)]">
      <ChartCard
        title={daily ? '날짜별 감정' : '주별 감정'}
        intro={
          enough ? (
            <p className="t-sub">
              {counted > 0
                ? `감정을 남긴 기록 ${counted}개 중 ${GOOD_LABEL} 쪽 ${totals.good}개, ${NEUTRAL_LABEL} ${totals.neutral}개, ${HARD_LABEL} ${totals.hard}개예요.`
                : '이 기간에는 감정을 남긴 기록이 아직 없어요.'}
            </p>
          ) : undefined
        }
        table={
          enough
            ? {
                headers: [daily ? '날짜' : '주', GOOD_LABEL, NEUTRAL_LABEL, HARD_LABEL, '고르지 않음'],
                numeric: [1, 2, 3, 4],
                headerDots: { 1: CHART_COLORS.good, 2: CHART_COLORS.neutral, 3: CHART_COLORS.hard },
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
        caption={
          enough ? (
            <>
              <Legend
                items={[
                  { label: GOOD_LABEL, color: CHART_COLORS.good },
                  { label: NEUTRAL_LABEL, color: CHART_COLORS.neutral },
                  { label: HARD_LABEL, color: CHART_COLORS.hard },
                ]}
              />
              {byFeeling.length > 0 ? (
                <ul className="flex flex-wrap gap-2 pt-1" aria-label="자주 쓴 감정">
                  {byFeeling.map((feeling) => (
                    <li
                      key={`${feeling.builtIn}-${feeling.label}`}
                      className="inline-flex min-h-8 items-center gap-1.5 rounded-full border border-line-2 bg-white px-3 t-sub"
                    >
                      <Swatch fill={DIRECTION_COLOR[feeling.direction]} />
                      {feeling.label}
                      <span className="text-ink-2">{feeling.count}번</span>
                      <span className="sr-only">{DIRECTION_LABEL[feeling.direction]} 쪽 감정</span>
                    </li>
                  ))}
                </ul>
              ) : null}
              {totals.none > 0 ? (
                <p className="pt-1 t-cap text-ink-2">감정을 고르지 않은 기록 {totals.none}개는 빼고 셌어요</p>
              ) : null}
            </>
          ) : undefined
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
    </div>
  )
}

function RhythmTab({ growth, minSlot }: { growth: Growth; minSlot: number }) {
  const enough = growth.summary.records >= MIN_RECORDS_FOR_CHARTS
  const { data: allHobbies } = useHobbies(true)
  const axis = countAxis(Math.max(0, ...growth.weekday))
  const items: BarItem[] = WEEKDAY_LABELS.map((label, index) => ({
    id: label,
    tick: label,
    parts: [{ value: growth.weekday[index] ?? 0, color: CHART_COLORS.base }],
    valueText: String(growth.weekday[index] ?? 0),
    tooltip: `${label}요일 · ${growth.weekday[index] ?? 0}개`,
  }))
  const topics = useMemo(
    () => filterTopics(growth.topics, (allHobbies ?? []).map((hobby) => hobby.name), MAX_TOPICS),
    [growth.topics, allHobbies],
  )
  const enoughTopics = topics.length >= MIN_TOPICS
  const recentMilestones = growth.milestones.slice(0, 3)

  return (
    <div className="flex flex-col gap-4 lg:min-h-0 lg:flex-1">
      <Panel>
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
            enoughTopics
              ? {
                  headers: ['주제', '횟수'],
                  numeric: [1],
                  rows: topics.map((topic) => [topic.tag, `${topic.count}번`]),
                }
              : undefined
          }
          chart={
            enoughTopics ? (
              <HBarChart
                ariaLabel="자주 하는 주제 가로 막대 그래프"
                items={topics.map((topic) => ({
                  label: topic.tag,
                  value: topic.count,
                  valueText: `${topic.count}번`,
                }))}
              />
            ) : (
              <p className="py-6 text-body-min text-ink-2">기록이 쌓이면 자주 하는 주제를 보여 드릴게요</p>
            )
          }
        />
      </Panel>
      {recentMilestones.length > 0 ? (
        <section aria-label="의미 있는 날" className="flex-none">
          <ol className="grid gap-2 lg:grid-cols-3 lg:gap-3">
            {recentMilestones.map((milestone) => (
              <li key={milestone.recordId} className="min-w-0">
                <Link
                  to={`/records/${milestone.recordId}`}
                  className="flex min-h-11 flex-col justify-center rounded-xl border border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_7%,white)] px-4 py-2 text-ink no-underline"
                >
                  <span className="truncate t-cap text-ink-2">
                    의미 있는 날 · {formatMonthDay(milestone.date)} · {milestone.hobbyName}
                  </span>
                  <span className="truncate font-serif t-sub">{milestone.preview}</span>
                </Link>
              </li>
            ))}
          </ol>
        </section>
      ) : null}
    </div>
  )
}

export function GrowthPage() {
  const { data: hobbies } = useHobbies(true)
  const [filter, setFilter] = useState<GrowthFilter>(readGrowthFilter)
  const [tab, setTab] = useState<TabKey>('overview')
  const isDesktop = useIsDesktop()
  const lowHeight = useMediaQuery(`(max-height: ${COMPACT_MAX_HEIGHT}px)`)
  // 막대 하나가 차지하는 최소 칸: 웹 12px(막대 8px+여백), 모바일 24px(막대당 20px 이상).
  const minSlot = isDesktop ? 12 : 24

  // 드롭다운에는 보관하지 않은 분야만 둔다(기록이 0개여도 보인다).
  const activeHobbies = (hobbies ?? []).filter((hobby) => !hobby.archived)
  // 지금 목록에 없는 분야가 저장돼 있으면 전체로 본다(보관·삭제했거나 다른 계정일 때).
  const hobbyId =
    hobbies && filter.hobbyId != null && !activeHobbies.some((hobby) => hobby.id === filter.hobbyId)
      ? null
      : filter.hobbyId
  const { data: growth, isLoading, isError } = useGrowth(filter.period, hobbyId)

  function update(next: GrowthFilter) {
    setFilter(next)
    writeGrowthFilter(next)
  }

  function onTabKey(event: KeyboardEvent<HTMLDivElement>) {
    const index = TABS.findIndex((item) => item.key === tab)
    let next: TabKey
    if (event.key === 'ArrowRight') next = TABS[(index + 1) % TABS.length].key
    else if (event.key === 'ArrowLeft') next = TABS[(index + TABS.length - 1) % TABS.length].key
    else return
    event.preventDefault()
    setTab(next)
    document.getElementById(`growth-tab-${next}`)?.focus()
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
    <div className="flex flex-col gap-3 lg:min-h-0 lg:flex-1">
      <h1 className="text-page-title">성장</h1>

      <div className="flex flex-col gap-2 lg:flex-row lg:items-center lg:justify-between lg:gap-4">
        <div
          role="tablist"
          aria-label="성장 보기"
          onKeyDown={onTabKey}
          className="scrollbar-none flex min-w-0 flex-none gap-2 overflow-x-auto"
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
              onClick={() => setTab(item.key)}
              className={[
                'inline-flex h-11 flex-none items-center justify-center whitespace-nowrap rounded-full border px-4 t-sub lg:h-10',
                tab === item.key
                  ? 'border-[#A8CDB4] bg-[#EEF2E2] font-bold text-[#3F6B4F]'
                  : 'border-line-2 bg-white font-medium text-ink-2',
              ].join(' ')}
            >
              {item.label}
            </button>
          ))}
        </div>
        {/* 돌아보기와 같은 공용 Dropdown(커스텀 팝오버)을 그대로 쓴다. */}
        <div className="grid grid-cols-2 gap-2 lg:flex lg:flex-none lg:items-center">
          <Dropdown
            aria-label="분야"
            value={hobbyId == null ? 'ALL' : String(hobbyId)}
            options={[
              { value: 'ALL', label: '전체 분야' },
              ...activeHobbies.map((hobby) => ({ value: String(hobby.id), label: hobby.name })),
            ]}
            onChange={(value) => update({ ...filter, hobbyId: value === 'ALL' ? null : Number(value) })}
          />
          <Dropdown
            aria-label="기간"
            value={filter.period}
            options={PERIOD_OPTIONS}
            onChange={(value) => update({ ...filter, period: value as GrowthPeriod })}
          />
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
