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
 * L3: SVG를 버리고 HTML grid로 다시 그린다(월 라벨이 SVG viewBox 확대와 함께 커지던 문제 수정).
 * N1: 기록 탭 데스크톱(toggle 없는 52주)의 "두 줄로 쌓기"를 폐기하고, 칸 14px 고정 한 줄 + 카드 안
 * 가로 스크롤 + [‹][›] 이동 버튼으로 바꾼다(두 줄 모드에서 12월·6월 라벨이 사라지던 원인은 아래 참고).
 */
const GAP_PX = 3
const HALF_WEEKS = 26
const FULL_WEEKS = 52
const FULL_CELL_PX = 10
const MAX_CLAMP_CELL_PX = 16
/** 기록 탭 데스크톱 전용 한 줄 모드의 고정 칸 크기(지시문 명시: 14px, 간격 3px). */
const SCROLL_CELL_PX = 14
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

/** 홈·모바일 토글처럼 칸 폭이 좁은 곳에서 쓰는 라벨 중복 억제(3열 미만 간격이면 뒤 라벨 숨김). */
function computeMonthLabels(weekColumns: string[][]): { col: number; label: string }[] {
  const labels: { col: number; label: string }[] = []
  let lastMonth = ''
  let lastAcceptedCol = -Infinity
  weekColumns.forEach((week, colIndex) => {
    const label = monthLabel(week[0])
    if (label === lastMonth) return
    lastMonth = label
    if (colIndex - lastAcceptedCol < 3) return
    labels.push({ col: colIndex, label })
    lastAcceptedCol = colIndex
  })
  return labels
}

/**
 * 기록 탭 데스크톱 한 줄 모드 전용: 라벨을 숨기는 규칙 없이 12개월 전부 표시한다(지시문 6).
 * 칸이 14px로 고정돼 있어 월 한 칸(약 4열 = 68px 이상)이 라벨끼리 겹칠 만큼 좁아지지 않는다.
 */
function computeAllMonthLabels(weekColumns: string[][]): { col: number; label: string }[] {
  const labels: { col: number; label: string }[] = []
  let lastMonth = ''
  weekColumns.forEach((week, colIndex) => {
    const label = monthLabel(week[0])
    if (label === lastMonth) return
    lastMonth = label
    labels.push({ col: colIndex, label })
  })
  return labels
}

export function ActivityHeatmap({ weeks: fixedWeeks, hobbyId, toggle = false }: Props) {
  const today = todayLocalDate()
  const currentWeekStart = startOfWeekLocal(new Date())
  const [range, setRange] = useState<'half' | 'full'>('half')
  const weeks = toggle ? (range === 'full' ? FULL_WEEKS : HALF_WEEKS) : fixedWeeks
  const fixedCell = toggle && range === 'full'
  // 기록 탭 데스크톱(toggle 없는 52주)만 한 줄 고정 칸 + 가로 스크롤 모드를 쓴다.
  const scrollableSingleRow = weeks === FULL_WEEKS && !toggle

  const [selectedDate, setSelectedDate] = useState<string | null>(null)
  const scrollRef = useRef<HTMLDivElement>(null)
  const [canScrollPrev, setCanScrollPrev] = useState(false)
  const [canScrollNext, setCanScrollNext] = useState(false)

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

  // 1년(toggle의 full)을 고르면 카드 안에서만 가로로 밀리게 하고, 최신 주(오른쪽 끝)가 먼저 보이게 한다.
  useEffect(() => {
    if (!fixedCell) return
    const el = scrollRef.current
    if (el) el.scrollLeft = el.scrollWidth
  }, [fixedCell])

  function updateScrollButtons() {
    const el = scrollRef.current
    if (!el) return
    setCanScrollPrev(el.scrollLeft > 1)
    setCanScrollNext(el.scrollLeft < el.scrollWidth - el.clientWidth - 1)
  }

  // 한 줄 스크롤 모드는 처음에 오른쪽 끝(가장 최근)이 보이게 맞춘다.
  useEffect(() => {
    if (!scrollableSingleRow) return
    const el = scrollRef.current
    if (!el) return
    el.scrollLeft = el.scrollWidth
    updateScrollButtons()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scrollableSingleRow, weeks])

  function scrollByWeeks(amount: number) {
    const el = scrollRef.current
    if (!el) return
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    el.scrollBy({ left: amount * (SCROLL_CELL_PX + GAP_PX), behavior: reducedMotion ? 'auto' : 'smooth' })
    window.setTimeout(updateScrollButtons, reducedMotion ? 0 : 400)
  }

  if (mapQuery.isLoading) return <Loading />

  const monthLabels: { col: number; label: string }[] = scrollableSingleRow
    ? computeAllMonthLabels(columns)
    : computeMonthLabels(columns)

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

  function renderCell(date: string, colIndex: number, rowIndex: number, cellPx?: number) {
    const count = countByDate.get(date) ?? 0
    const level = levelOf(date > today ? 0 : count)
    const interactive = interactiveDates.has(date)
    const background = date > today ? 'transparent' : LEVEL_BG[level]
    const sizeStyle: CSSProperties = cellPx != null ? { width: cellPx, height: cellPx } : { width: '100%', aspectRatio: '1' }
    if (!interactive) {
      return <div key={date} aria-hidden="true" style={{ ...sizeStyle, borderRadius: '2px', background }} />
    }
    return (
      <button
        key={date}
        type="button"
        ref={(node) => {
          if (node) cellRefs.current.set(date, node)
          else cellRefs.current.delete(date)
        }}
        className="heatmap-cell"
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
        style={{ ...sizeStyle, borderRadius: '2px', background, border: 'none', padding: 0, cursor: 'pointer' }}
      />
    )
  }

  function makeGridVars(weeksForRow: number): CSSProperties {
    return {
      '--weeks': weeksForRow,
      '--label-col': `${LABEL_COL_PX}px`,
      '--cell': fixedCell
        ? `${FULL_CELL_PX}px`
        : `clamp(9px, calc((100cqw - var(--label-col)) / var(--weeks) - ${GAP_PX}px), ${MAX_CLAMP_CELL_PX}px)`,
    } as CSSProperties
  }

  function renderClampedRow() {
    return (
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
            gridTemplateColumns: `repeat(${columns.length}, var(--cell))`,
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

        <div style={{ display: 'grid', gridTemplateRows: `repeat(7, var(--cell))`, rowGap: `${GAP_PX}px` }}>
          {WEEKDAY_LABELS.map(({ row, label }) => (
            <span key={row} style={{ ...LABEL_STYLE, gridRow: row + 1, color: 'var(--color-ink-3)', alignSelf: 'center' }}>
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
          {columns.map((week, colIndex) => week.map((date, rowIndex) => renderCell(date, colIndex, rowIndex)))}
        </div>
      </div>
    )
  }

  function renderScrollableRow() {
    const totalCellWidth = weeks * SCROLL_CELL_PX + (weeks - 1) * GAP_PX
    return (
      <div className="flex flex-col gap-1.5">
        <div className="flex justify-end gap-1">
          <button
            type="button"
            aria-label="이전 26주"
            disabled={!canScrollPrev}
            onClick={() => scrollByWeeks(-HALF_WEEKS)}
            className="inline-flex h-7 w-7 items-center justify-center rounded-md border border-line-2 bg-white text-ink-2 disabled:opacity-40"
          >
            ‹
          </button>
          <button
            type="button"
            aria-label="다음 26주"
            disabled={!canScrollNext}
            onClick={() => scrollByWeeks(HALF_WEEKS)}
            className="inline-flex h-7 w-7 items-center justify-center rounded-md border border-line-2 bg-white text-ink-2 disabled:opacity-40"
          >
            ›
          </button>
        </div>
        <div className="flex gap-2">
          <div
            className="flex-none"
            style={{ display: 'grid', rowGap: `${GAP_PX}px`, gridTemplateRows: `14px repeat(7, ${SCROLL_CELL_PX}px)` }}
          >
            <div />
            {WEEKDAY_LABELS.map(({ row, label }) => (
              <span key={row} style={{ ...LABEL_STYLE, gridRow: row + 2, color: 'var(--color-ink-3)', alignSelf: 'center' }}>
                {label}
              </span>
            ))}
          </div>

          <div
            ref={scrollRef}
            onScroll={updateScrollButtons}
            className="scrollbar-thin-fade min-w-0 flex-1 overflow-x-auto overflow-y-hidden"
            role="group"
            aria-label={`최근 ${weeks}주 활동 지도`}
          >
            <div
              style={{
                display: 'grid',
                width: totalCellWidth,
                rowGap: `${GAP_PX}px`,
                columnGap: `${GAP_PX}px`,
                gridTemplateColumns: `repeat(${weeks}, ${SCROLL_CELL_PX}px)`,
                gridTemplateRows: `14px repeat(7, ${SCROLL_CELL_PX}px)`,
              }}
            >
              {monthLabels.map(({ col, label }) => (
                <span key={col} style={{ ...LABEL_STYLE, gridColumn: col + 1, gridRow: 1, color: 'var(--color-ink-3)' }}>
                  {label}
                </span>
              ))}
              {columns.map((week, colIndex) =>
                week.map((date, rowIndex) => (
                  <div key={date} style={{ gridColumn: colIndex + 1, gridRow: rowIndex + 2 }}>
                    {renderCell(date, colIndex, rowIndex, SCROLL_CELL_PX)}
                  </div>
                )),
              )}
            </div>
          </div>
        </div>
      </div>
    )
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

      {scrollableSingleRow ? (
        renderScrollableRow()
      ) : (
        <div style={{ containerType: 'inline-size', ...makeGridVars(weeks) } as CSSProperties}>
          <div
            ref={fixedCell ? scrollRef : undefined}
            className={fixedCell ? 'scrollbar-none overflow-x-auto overflow-y-hidden' : 'overflow-x-visible overflow-y-hidden'}
          >
            {renderClampedRow()}
          </div>
        </div>
      )}

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
