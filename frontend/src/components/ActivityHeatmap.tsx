import { useMemo, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Loading } from './Loading'
import { useIsDesktop, useMediaQuery } from '../hooks/useIsDesktop'
import { addDays, formatMonthDay, startOfWeekLocal, todayLocalDate } from '../lib/date'
import { fetchActivityMap } from '../lib/stats'

type Props = {
  /** 토글이 없을 때(홈의 12주 지도)만 쓰는 고정 주 수. toggle이 true면 무시된다. */
  weeks: number
  hobbyId?: number | null
  /** 지난 기록의 1년 지도처럼 [6개월|1년] 토글을 둘지 여부(LAYOUT-V2 5단계). */
  toggle?: boolean
}

/*
 * LAYOUT-V2 5단계: 칸 크기를 컨테이너 폭에 맞춰 줄이지 않고 rem 고정값으로 그린다(칸 0.7rem,
 * 간격 0.2rem). SVG의 viewBox 숫자를 그대로 rem 값으로 쓰고(1 유닛 = 1rem), 렌더링 폭도 같은
 * 숫자로 된 rem 단위를 줘서 축소 없이 1:1로 보이게 한다 — 카드가 좁으면 칸이 작아지는 대신
 * 카드 안에서만 가로로 스크롤된다.
 */
const CELL = 0.7
const GAP = 0.2
const PITCH = CELL + GAP
const MONTH_LABEL_HEIGHT = 1.1
/* 활동 지도 라벨만 디자인 규칙의 예외로 최소 12px을 쓴다(다른 글자 토큰처럼 14px까지는 안 올린다). */
const LABEL_FONT_STYLE = { fontSize: 'max(12px, 0.75rem)' } as const

const FULL_WEEKS = 52
const WIDE_DESKTOP_QUERY = '(min-width: 1280px)'

/** 기록 수를 0·1·2·3+ 네 단계로 나눈다(디자인 §7의 활동 지도 초록 토큰 재사용). */
function levelOf(count: number): 0 | 1 | 2 | 3 {
  if (count <= 0) return 0
  if (count === 1) return 1
  if (count === 2) return 2
  return 3
}

const LEVEL_BG: Record<0 | 1 | 2 | 3, string> = {
  0: 'var(--color-rest)',
  1: 'color-mix(in srgb, var(--color-base) 35%, var(--color-paper))',
  2: 'color-mix(in srgb, var(--color-base) 65%, var(--color-paper))',
  3: 'var(--color-base)',
}

function monthLabel(isoDate: string): string {
  const [, month] = isoDate.split('-')
  return `${Number(month)}월`
}

export function ActivityHeatmap({ weeks: fixedWeeks, hobbyId, toggle = false }: Props) {
  const today = todayLocalDate()
  const currentWeekStart = startOfWeekLocal(new Date())
  const isDesktop = useIsDesktop()
  const isWideDesktop = useMediaQuery(WIDE_DESKTOP_QUERY)
  const halfWeeks = isDesktop ? 26 : 24
  // 토글이 있을 때 기본 선택: 넓은 데스크톱(1280 이상)만 1년이 한 화면에 들어가 기본으로 펼쳐 보여준다.
  const [range, setRange] = useState<'half' | 'full'>(isWideDesktop ? 'full' : 'half')
  const weeks = toggle ? (range === 'full' ? FULL_WEEKS : halfWeeks) : fixedWeeks

  const [selectedDate, setSelectedDate] = useState<string | null>(null)
  // 364칸까지도 Tab만으로 전부 거치면 접근성이 떨어져, 한 칸만 탭 순서에 두고(roving tabindex)
  // 그 칸에서 방향키로 다른 칸으로 옮겨 다니게 한다. 기본 초점은 오늘 칸.
  const [focusedDate, setFocusedDate] = useState<string>(today)
  const cellRefs = useRef(new Map<string, SVGRectElement>())
  const scrollRef = useRef<HTMLDivElement>(null)

  const weekStarts = useMemo(
    () => Array.from({ length: weeks }, (_, index) => addDays(currentWeekStart, (index - (weeks - 1)) * 7)),
    [currentWeekStart, weeks],
  )
  const from = weekStarts[0]

  const mapQuery = useQuery({
    queryKey: ['stats', 'activity-map', 'heatmap', from, today, hobbyId ?? null],
    queryFn: () => fetchActivityMap({ from, to: today, hobbyId: hobbyId ?? undefined }),
  })

  if (mapQuery.isLoading) return <Loading />

  const countByDate = new Map<string, number>()
  for (const entry of mapQuery.data ?? []) {
    countByDate.set(entry.date, (countByDate.get(entry.date) ?? 0) + entry.count)
  }

  const columns = weekStarts.map((weekStart) => Array.from({ length: 7 }, (_, dayIndex) => addDays(weekStart, dayIndex)))

  const width = columns.length * PITCH
  const height = MONTH_LABEL_HEIGHT + 7 * PITCH

  const monthLabels: { x: number; label: string }[] = []
  let lastMonth = ''
  columns.forEach((week, colIndex) => {
    const label = monthLabel(week[0])
    if (label !== lastMonth) {
      monthLabels.push({ x: colIndex * PITCH, label })
      lastMonth = label
    }
  })

  const selectedCount = selectedDate ? (countByDate.get(selectedDate) ?? 0) : null

  function moveFocus(colIndex: number, rowIndex: number, key: string): void {
    let nextCol = colIndex
    let nextRow = rowIndex
    if (key === 'ArrowRight') nextCol = Math.min(columns.length - 1, colIndex + 1)
    else if (key === 'ArrowLeft') nextCol = Math.max(0, colIndex - 1)
    else if (key === 'ArrowDown') nextRow = Math.min(6, rowIndex + 1)
    else if (key === 'ArrowUp') nextRow = Math.max(0, rowIndex - 1)
    else return
    const nextDate = columns[nextCol][nextRow]
    if (nextDate > today) return
    setFocusedDate(nextDate)
    setSelectedDate(nextDate)
    cellRefs.current.get(nextDate)?.focus()
  }

  function selectRange(next: 'half' | 'full') {
    setRange(next)
    // 1년을 선택하면 카드 안에서 오른쪽 끝(최신 주)이 먼저 보이게 한다.
    if (next === 'full') {
      requestAnimationFrame(() => {
        const el = scrollRef.current
        if (el) el.scrollLeft = el.scrollWidth
      })
    }
  }

  return (
    <div className="flex flex-col gap-2">
      {toggle ? (
        <div className="flex justify-end">
          <div className="inline-flex rounded-lg border border-line-2 bg-white p-[0.1875rem]" role="group" aria-label="활동 지도 기간">
            <button
              type="button"
              aria-pressed={range === 'half'}
              onClick={() => selectRange('half')}
              className={[
                'tap-min whitespace-nowrap rounded-md px-2.5 text-caption-min font-medium',
                range === 'half' ? 'bg-ink text-white' : 'bg-transparent text-ink-2',
              ].join(' ')}
            >
              6개월
            </button>
            <button
              type="button"
              aria-pressed={range === 'full'}
              onClick={() => selectRange('full')}
              className={[
                'tap-min whitespace-nowrap rounded-md px-2.5 text-caption-min font-medium',
                range === 'full' ? 'bg-ink text-white' : 'bg-transparent text-ink-2',
              ].join(' ')}
            >
              1년
            </button>
          </div>
        </div>
      ) : null}

      <div ref={scrollRef} className="scrollbar-thin-fade overflow-x-auto">
        <svg
          viewBox={`0 0 ${width} ${height}`}
          role="img"
          aria-label={`최근 ${weeks}주 활동 지도`}
          style={{ width: `${width}rem`, height: `${height}rem`, flexShrink: 0 }}
        >
          {monthLabels.map(({ x, label }) => (
            <text key={x} x={x} y={0.85} fill="var(--color-ink-3)" style={LABEL_FONT_STYLE}>
              {label}
            </text>
          ))}
          {columns.map((week, colIndex) =>
            week.map((date, rowIndex) => {
              const count = countByDate.get(date) ?? 0
              const level = levelOf(count)
              const x = colIndex * PITCH
              const y = MONTH_LABEL_HEIGHT + rowIndex * PITCH
              const isFuture = date > today
              return (
                <rect
                  key={date}
                  ref={(node) => {
                    if (node) cellRefs.current.set(date, node)
                    else cellRefs.current.delete(date)
                  }}
                  x={x}
                  y={y}
                  width={CELL}
                  height={CELL}
                  rx={0.15}
                  fill={isFuture ? 'transparent' : LEVEL_BG[level]}
                  tabIndex={isFuture ? -1 : date === focusedDate ? 0 : -1}
                  role={isFuture ? undefined : 'img'}
                  aria-label={isFuture ? undefined : `${formatMonthDay(date)} · 기록 ${count}개`}
                  onClick={isFuture ? undefined : () => setSelectedDate(date)}
                  onFocus={isFuture ? undefined : () => setFocusedDate(date)}
                  onKeyDown={
                    isFuture
                      ? undefined
                      : (event) => {
                          if (event.key === 'Enter' || event.key === ' ') {
                            event.preventDefault()
                            setSelectedDate(date)
                            return
                          }
                          if (event.key.startsWith('Arrow')) {
                            event.preventDefault()
                            moveFocus(colIndex, rowIndex, event.key)
                          }
                        }
                  }
                  style={{ cursor: isFuture ? 'default' : 'pointer' }}
                >
                  {isFuture ? null : <title>{`${formatMonthDay(date)} · 기록 ${count}개`}</title>}
                </rect>
              )
            }),
          )}
        </svg>
      </div>

      <div className="flex items-center justify-between gap-3">
        <p className="min-h-5 text-caption-min text-ink-2" aria-live="polite" style={{ fontSize: 'max(12px, 0.75rem)' }}>
          {selectedDate ? `${formatMonthDay(selectedDate)} · 기록 ${selectedCount}개` : ' '}
        </p>
        <div className="flex items-center gap-1 text-ink-2" style={{ fontSize: 'max(12px, 0.75rem)' }}>
          <span>적음</span>
          <span className="h-3 w-3 rounded-sm" style={{ background: LEVEL_BG[0] }} />
          <span className="h-3 w-3 rounded-sm" style={{ background: LEVEL_BG[1] }} />
          <span className="h-3 w-3 rounded-sm" style={{ background: LEVEL_BG[2] }} />
          <span className="h-3 w-3 rounded-sm" style={{ background: LEVEL_BG[3] }} />
          <span>많음</span>
        </div>
      </div>
    </div>
  )
}
