import { useEffect, useMemo, useRef, useState, type CSSProperties } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Loading } from './Loading'
import { addDays, formatMonthDay, startOfWeekLocal, todayLocalDate } from '../lib/date'
import { fetchActivityMap } from '../lib/stats'

type Props = {
  /** 토글이 없을 때만 쓰는 고정 주 수. toggle이 true면 무시된다(half/full을 직접 고른다). */
  weeks: number
  hobbyId?: number | null
  /** 지난 기록의 1년 지도·모바일처럼 [6개월|1년] 토글을 둘지 여부. */
  toggle?: boolean
}

/*
 * L3: SVG를 버리고 HTML grid로 다시 그린다. SVG의 viewBox 확대·축소가 <text>의 font-size까지
 * 함께 키워 월 라벨이 거대하게 겹쳐 보이던 문제(L3 증거 a)의 원인이었다 — HTML에는 그런 확대·축소
 * 변환이 없으므로 라벨을 고정 12px로 둬도 더 이상 커지지 않는다.
 */
const GAP_PX = 3
const HALF_WEEKS = 26
const FULL_WEEKS = 52
const FULL_CELL_PX = 10
/* 활동 지도 라벨만 디자인 규칙의 예외로 고정 12px을 쓴다(다른 글자 토큰처럼 rem으로 늘어나지 않는다). */
const LABEL_STYLE: CSSProperties = { fontSize: '12px', lineHeight: 1, whiteSpace: 'nowrap' }
const LABEL_COL_PX = 18
const WEEKDAY_LABELS: { row: number; label: string }[] = [
  { row: 0, label: '월' },
  { row: 2, label: '수' },
  { row: 4, label: '금' },
]

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
  const [range, setRange] = useState<'half' | 'full'>('half')
  const weeks = toggle ? (range === 'full' ? FULL_WEEKS : HALF_WEEKS) : fixedWeeks
  const fixedCell = toggle && range === 'full'

  const [selectedDate, setSelectedDate] = useState<string | null>(null)
  const scrollRef = useRef<HTMLDivElement>(null)

  const weekStarts = useMemo(
    () => Array.from({ length: weeks }, (_, index) => addDays(currentWeekStart, (index - (weeks - 1)) * 7)),
    [currentWeekStart, weeks],
  )
  const from = weekStarts[0]
  const columns = useMemo(
    () => weekStarts.map((weekStart) => Array.from({ length: 7 }, (_, dayIndex) => addDays(weekStart, dayIndex))),
    [weekStarts],
  )

  const mapQuery = useQuery({
    queryKey: ['stats', 'activity-map', 'heatmap', from, today, hobbyId ?? null],
    queryFn: () => fetchActivityMap({ from, to: today, hobbyId: hobbyId ?? undefined }),
  })

  const countByDate = new Map<string, number>()
  for (const entry of mapQuery.data ?? []) {
    countByDate.set(entry.date, (countByDate.get(entry.date) ?? 0) + entry.count)
  }

  // 같은 칸에서 방향키로 옮겨 다니는 roving tabindex. 기록이 있는 칸만 버튼(상호작용)이라
  // 포커스도 그 칸들 사이에서만 움직인다 — 없으면 전체를 훑어도 선택할 게 없다.
  const interactiveDates = useMemo(() => {
    const set = new Set<string>()
    for (const week of columns) {
      for (const date of week) {
        if (date <= today && (countByDate.get(date) ?? 0) > 0) set.add(date)
      }
    }
    return set
  }, [columns, today, mapQuery.data])
  const [focusedDate, setFocusedDate] = useState<string | null>(null)
  const cellRefs = useRef(new Map<string, HTMLButtonElement>())

  useEffect(() => {
    if (focusedDate && interactiveDates.has(focusedDate)) return
    let latest: string | null = null
    for (const date of interactiveDates) {
      if (!latest || date > latest) latest = date
    }
    setFocusedDate(latest)
  }, [interactiveDates, focusedDate])

  // 1년(full)을 고르면 카드 안에서만 가로로 밀리게 하고, 최신 주(오른쪽 끝)가 먼저 보이게 한다.
  useEffect(() => {
    if (!fixedCell) return
    const el = scrollRef.current
    if (el) el.scrollLeft = el.scrollWidth
  }, [fixedCell])

  if (mapQuery.isLoading) return <Loading />

  const monthLabels: { col: number; label: string }[] = []
  let lastMonth = ''
  let lastAcceptedCol = -Infinity
  columns.forEach((week, colIndex) => {
    const label = monthLabel(week[0])
    if (label === lastMonth) return
    lastMonth = label
    // 라벨끼리 3열 미만으로 가까우면(겹치면) 뒤의 라벨을 숨긴다.
    if (colIndex - lastAcceptedCol < 3) return
    monthLabels.push({ col: colIndex, label })
    lastAcceptedCol = colIndex
  })

  const selectedCount = selectedDate ? (countByDate.get(selectedDate) ?? 0) : null

  function moveFocus(colIndex: number, rowIndex: number, key: string): void {
    let col = colIndex
    let row = rowIndex
    const [dCol, dRow] =
      key === 'ArrowRight' ? [1, 0] : key === 'ArrowLeft' ? [-1, 0] : key === 'ArrowDown' ? [0, 1] : key === 'ArrowUp' ? [0, -1] : [0, 0]
    if (dCol === 0 && dRow === 0) return
    // 다음 상호작용 가능한 칸(기록이 있는 칸)을 찾을 때까지 같은 방향으로 계속 이동한다.
    for (let step = 0; step < columns.length * 7; step += 1) {
      col += dCol
      row += dRow
      if (col < 0 || col >= columns.length || row < 0 || row > 6) return
      const candidate = columns[col][row]
      if (interactiveDates.has(candidate)) {
        setFocusedDate(candidate)
        setSelectedDate(candidate)
        cellRefs.current.get(candidate)?.focus()
        return
      }
    }
  }

  function selectRange(next: 'half' | 'full') {
    setRange(next)
  }

  const gridVars = {
    '--weeks': weeks,
    '--label-col': `${LABEL_COL_PX}px`,
    '--cell': fixedCell ? `${FULL_CELL_PX}px` : `clamp(9px, calc((100cqw - var(--label-col)) / var(--weeks) - ${GAP_PX}px), 16px)`,
  } as CSSProperties

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

      <div
        style={{ containerType: 'inline-size', ...gridVars } as CSSProperties}
        role="group"
        aria-label={`최근 ${weeks}주 활동 지도`}
      >
        <div
          ref={scrollRef}
          className={fixedCell ? 'scrollbar-none overflow-x-auto' : 'scrollbar-thin-fade overflow-x-auto'}
        >
          <div
            style={{
              display: 'inline-grid',
              gridTemplateColumns: 'var(--label-col) 1fr',
              columnGap: 0,
              rowGap: '2px',
              justifyItems: 'start',
            }}
          >
            <div />
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: `repeat(${weeks}, var(--cell))`,
                columnGap: `${GAP_PX}px`,
                height: '14px',
              }}
            >
              {monthLabels.map(({ col, label }) => (
                <span key={col} style={{ ...LABEL_STYLE, gridColumn: col + 1, color: 'var(--color-ink-3)' }}>
                  {label}
                </span>
              ))}
            </div>

            <div
              style={{
                display: 'grid',
                gridTemplateRows: `repeat(7, var(--cell))`,
                rowGap: `${GAP_PX}px`,
              }}
            >
              {WEEKDAY_LABELS.map(({ row, label }) => (
                <span
                  key={row}
                  style={{ ...LABEL_STYLE, gridRow: row + 1, color: 'var(--color-ink-3)', alignSelf: 'center' }}
                >
                  {label}
                </span>
              ))}
            </div>

            <div
              style={{
                display: 'grid',
                gridAutoFlow: 'column',
                gridTemplateRows: `repeat(7, var(--cell))`,
                gridAutoColumns: 'var(--cell)',
                columnGap: `${GAP_PX}px`,
                rowGap: `${GAP_PX}px`,
              }}
            >
              {columns.map((week, colIndex) =>
                week.map((date, rowIndex) => {
                  const count = countByDate.get(date) ?? 0
                  const level = levelOf(date > today ? 0 : count)
                  const interactive = interactiveDates.has(date)
                  const background = date > today ? 'transparent' : LEVEL_BG[level]
                  if (!interactive) {
                    return (
                      <div
                        key={date}
                        aria-hidden="true"
                        style={{ width: '100%', aspectRatio: '1', borderRadius: '2px', background }}
                      />
                    )
                  }
                  return (
                    <button
                      key={date}
                      type="button"
                      ref={(node) => {
                        if (node) cellRefs.current.set(date, node)
                        else cellRefs.current.delete(date)
                      }}
                      tabIndex={date === focusedDate ? 0 : -1}
                      aria-label={`${formatMonthDay(date)} · 기록 ${count}개`}
                      title={`${formatMonthDay(date)} · 기록 ${count}개`}
                      onClick={() => setSelectedDate(date)}
                      onFocus={() => setFocusedDate(date)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter' || event.key === ' ') {
                          event.preventDefault()
                          setSelectedDate(date)
                          return
                        }
                        if (event.key.startsWith('Arrow')) {
                          event.preventDefault()
                          moveFocus(colIndex, rowIndex, event.key)
                        }
                      }}
                      style={{
                        width: '100%',
                        aspectRatio: '1',
                        borderRadius: '2px',
                        background,
                        border: 'none',
                        padding: 0,
                        cursor: 'pointer',
                      }}
                    />
                  )
                }),
              )}
            </div>
          </div>
        </div>
      </div>

      <div className="flex items-center justify-between gap-3">
        <p className="min-h-5 text-caption-min text-ink-2" aria-live="polite">
          {selectedDate ? `${formatMonthDay(selectedDate)} · 기록 ${selectedCount}개` : ' '}
        </p>
        <div className="flex items-center gap-1 text-caption-min text-ink-2">
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
