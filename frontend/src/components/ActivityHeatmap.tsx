import { useEffect, useLayoutEffect, useMemo, useRef, useState, type CSSProperties } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Loading } from './Loading'
import { addDays, formatMonthDay, startOfWeekLocal, todayLocalDate } from '../lib/date'
import { computeMonthLabels } from '../lib/activityHeatmapLabels'
import { fetchActivityMap } from '../lib/stats'

type Props = {
  /** 토글이 없을 때만 쓰는 고정 주 수. toggle이 true면 무시된다(half/full을 직접 고른다). */
  weeks: number
  hobbyId?: number | null
  /** 지난 기록의 1년 지도·모바일처럼 [6개월|1년] 토글을 둘지 여부. */
  toggle?: boolean
  /** 웹 홈용: 아래 범례·선택 줄을 없애고(헤더로 옮김), 칸 크기는 홈 밀도 변수(--heat-cell-max)를 따르며, 아주 낮은 화면에서는 한 줄 요약만 보인다. */
  home?: boolean
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
const HOME_CELL_MAX_PX = 48
const HOME_CELL_MIN_PX = 8
/** 기록 탭 데스크톱 전용 한 줄 모드의 고정 칸 크기(지시문 명시: 14px, 간격 3px). */
const SCROLL_CELL_PX = 14
const MONTH_LABEL_ROW_PX = 14
/**
 * P2 1번/S1 B단계: overflow-x:auto 스크롤바가 콘텐츠 박스 안쪽에서 공간을 차지해, 높이를 콘텐츠
 * 높이와 정확히 같게 두면 맨 아랫줄(일요일)이 스크롤바에 가려 잘린다. 스크롤 영역에는 스크롤바
 * 영역(14px)과 아래 여백(4px)을 더해 높이를 주고, 요일 라벨 열은 그 여유 없이(콘텐츠 높이만)
 * 위쪽부터 맞춘다.
 */
const SCROLLBAR_AREA_PX = 14
const BOTTOM_MARGIN_PX = 4
/* 활동 지도 라벨만 디자인 규칙의 예외로 고정 12px을 쓴다(다른 글자 토큰처럼 rem으로 늘어나지 않는다). */
const LABEL_STYLE: CSSProperties = { fontSize: '12px', lineHeight: 1, whiteSpace: 'nowrap' }

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

export function ActivityHeatmap({ weeks: fixedWeeks, hobbyId, toggle = false, home = false }: Props) {
  const today = todayLocalDate()
  const currentWeekStart = startOfWeekLocal(new Date())
  const [range, setRange] = useState<'half' | 'full'>('half')
  // 웹 홈: 칸은 정사각이고 크기는 가용 높이에서, 보여 줄 주 수는 가용 폭에서 정해 52주까지 채운다(오른쪽이 최신).
  const [boxEl, setBoxEl] = useState<HTMLDivElement | null>(null)
  const [box, setBox] = useState({ w: 0, h: 0 })
  useLayoutEffect(() => {
    if (!home || !boxEl) return
    const measure = () => setBox((prev) => (prev.w === boxEl.clientWidth && prev.h === boxEl.clientHeight ? prev : { w: boxEl.clientWidth, h: boxEl.clientHeight }))
    measure()
    const observer = new ResizeObserver(measure)
    observer.observe(boxEl)
    return () => observer.disconnect()
  }, [home, boxEl])
  const measured = home && box.w > 0
  const homeCellRaw = Math.floor((box.h - (MONTH_LABEL_ROW_PX + 2) - 6 * GAP_PX) / 7)
  const homeCell = Math.min(HOME_CELL_MAX_PX, homeCellRaw)
  const showHomeGrid = !home || !measured || homeCellRaw >= HOME_CELL_MIN_PX
  const homeWeeks = Math.max(4, Math.min(FULL_WEEKS, Math.floor((box.w + GAP_PX) / (Math.max(HOME_CELL_MIN_PX, homeCell) + GAP_PX))))
  const weeks = home ? (measured ? homeWeeks : fixedWeeks) : toggle ? (range === 'full' ? FULL_WEEKS : HALF_WEEKS) : fixedWeeks
  const fixedCell = toggle && range === 'full'
  // 기록 탭 데스크톱(toggle 없는 52주)만 한 줄 고정 칸 + 가로 스크롤 모드를 쓴다.
  const scrollableSingleRow = weeks === FULL_WEEKS && !toggle && !home

  const [selectedDate, setSelectedDate] = useState<string | null>(null)
  const scrollRef = useRef<HTMLDivElement>(null)
  const [canScrollPrev, setCanScrollPrev] = useState(false)
  const [canScrollNext, setCanScrollNext] = useState(false)

  const weekStarts = useMemo(
    () => Array.from({ length: weeks }, (_, index) => addDays(currentWeekStart, (index - (weeks - 1)) * 7)),
    [currentWeekStart, weeks],
  )
  const from = weekStarts[0]
  const queryFrom = home ? addDays(currentWeekStart, -(FULL_WEEKS - 1) * 7) : from
  const columns = useMemo(
    () => weekStarts.map((weekStart) => Array.from({ length: 7 }, (_, dayIndex) => addDays(weekStart, dayIndex))),
    [weekStarts],
  )

  const mapQuery = useQuery({
    // 홈은 보이는 주 수가 폭에 따라 바뀌므로 52주를 한 번에 받아 두고 그 안에서 잘라 쓴다.
    queryKey: ['stats', 'activity-map', 'heatmap', queryFrom, today, hobbyId ?? null],
    queryFn: () => fetchActivityMap({ from: queryFrom, to: today, hobbyId: hobbyId ?? undefined }),
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

  // S1 B단계: 가로 스크롤이 있는 두 모드(1년 토글의 fixedCell, 기록 탭 데스크톱의 scrollableSingleRow)
  // 모두 "처음 보일 때 + weeks·카드 폭이 바뀔 때는 오른쪽 끝(최근)으로, 단 사용자가 이미 수동으로
  // 스크롤했다면 폭 변경 때 덮어쓰지 않는다"를 공유한다. useLayoutEffect로 페인트 전에 맞춰
  // "가장 오래된 달이 잠깐 보였다 사라지는" 깜박임을 없앤다.
  const hasHorizontalScroll = fixedCell || scrollableSingleRow
  const suppressScrollTrackingRef = useRef(false)
  const userScrolledRef = useRef(false)

  function scrollToEnd(el: HTMLDivElement) {
    suppressScrollTrackingRef.current = true
    el.scrollLeft = el.scrollWidth
    requestAnimationFrame(() => {
      suppressScrollTrackingRef.current = false
    })
  }

  function updateScrollButtons() {
    const el = scrollRef.current
    if (!el) return
    if (!suppressScrollTrackingRef.current) userScrolledRef.current = true
    setCanScrollPrev(el.scrollLeft > 1)
    setCanScrollNext(el.scrollLeft < el.scrollWidth - el.clientWidth - 1)
  }

  // weeks·모드가 바뀌면(6개월/1년 토글 전환 포함) 새 범위로 보는 것이니 "수동 스크롤" 기억을 지우고 끝으로.
  // mapQuery.isLoading도 의존성에 넣는다 — 로딩 중엔 <Loading/>만 반환돼(아래) 이 스크롤 영역
  // DOM이 아예 없어 scrollRef.current가 null이고, 로딩이 끝나 실제로 그려진 뒤에도 hasHorizontalScroll·
  // weeks가 그대로면 effect가 다시 안 돌아 "기본값이 맨 왼쪽에 멈춰 있고 화살표도 비활성 그대로"였다.
  useLayoutEffect(() => {
    if (!hasHorizontalScroll || mapQuery.isLoading) return
    const el = scrollRef.current
    if (!el) return
    userScrolledRef.current = false
    scrollToEnd(el)
    updateScrollButtons()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hasHorizontalScroll, weeks, mapQuery.isLoading])

  // 카드 폭이 바뀌면(사이드바 리사이즈 등) 사용자가 손대지 않은 한 다시 끝으로 맞춘다.
  useEffect(() => {
    if (!hasHorizontalScroll || mapQuery.isLoading) return
    const el = scrollRef.current
    if (!el) return
    const observer = new ResizeObserver(() => {
      const current = scrollRef.current
      if (!current) return
      if (!userScrolledRef.current) scrollToEnd(current)
      updateScrollButtons()
    })
    observer.observe(el)
    return () => observer.disconnect()
  }, [hasHorizontalScroll, mapQuery.isLoading])

  function scrollByWeeks(amount: number) {
    const el = scrollRef.current
    if (!el) return
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    el.scrollBy({ left: amount * (SCROLL_CELL_PX + GAP_PX), behavior: reducedMotion ? 'auto' : 'smooth' })
    window.setTimeout(updateScrollButtons, reducedMotion ? 0 : 400)
  }

  if (mapQuery.isLoading) {
    return home ? (
      <div ref={setBoxEl} className="min-h-0 flex-1 rounded-lg bg-rest motion-safe:animate-pulse" />
    ) : (
      <Loading />
    )
  }

  const monthLabels = computeMonthLabels(from, weeks)

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

  // P2 1번: 기록 있는 칸(button)과 빈 칸(div)이 같은 크기·모서리를 쓰도록 공용 스타일로 고정하고,
  // 버튼은 브라우저 기본 버튼 스타일(패딩, 테두리, 폰트, appearance)을 전부 초기화한다.
  function renderCell(date: string, colIndex: number, rowIndex: number, cellPx?: number) {
    const count = countByDate.get(date) ?? 0
    const level = levelOf(date > today ? 0 : count)
    const interactive = interactiveDates.has(date)
    const background = date > today ? 'transparent' : LEVEL_BG[level]
    const sizeStyle: CSSProperties = cellPx != null ? { width: cellPx, height: cellPx } : { width: '100%', aspectRatio: '1' }
    const baseStyle: CSSProperties = {
      ...sizeStyle,
      display: 'block',
      boxSizing: 'border-box',
      borderRadius: '2px',
      background,
    }
    if (!interactive) {
      return <div key={date} aria-hidden="true" style={baseStyle} />
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
        style={{
          ...baseStyle,
          appearance: 'none',
          WebkitAppearance: 'none',
          border: 0,
          padding: 0,
          margin: 0,
          font: 'inherit',
          cursor: 'pointer',
        }}
      />
    )
  }

  function makeGridVars(weeksForRow: number): CSSProperties {
    // 홈은 칸 크기를 폭과 가용 높이 중 작은 쪽에서 구하고(월 라벨 줄 16px + 간격 18px = 34px를 뺀 일곱 줄), 홈 밀도 변수(--cell-min/--cell-max) 안으로 자른다.
    return {
      '--weeks': weeksForRow,
      '--cell': fixedCell
        ? `${FULL_CELL_PX}px`
        : home
          ? `${Math.max(HOME_CELL_MIN_PX, homeCell)}px`
          : `clamp(9px, calc(100cqw / var(--weeks) - ${GAP_PX}px), ${MAX_CLAMP_CELL_PX}px)`,
    } as CSSProperties
  }

  // T1-4(a): 요일 라벨(월·수·금) 열을 없애고 격자가 왼쪽 끝부터 시작하게 한다. 월 라벨 행은
  // 격자와 같은 열 구성(repeat(columns.length, var(--cell)))을 쓰는 별도 그리드라, 바깥 2열
  // 래퍼(라벨 열 + 1fr)를 걷어내도 월 라벨의 col 인덱스는 그대로 유효하다.
  function renderClampedRow() {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', rowGap: '2px' }}>
        <div style={{ position: 'relative', height: '14px' }}>
          {monthLabels.map(({ col, label }) => (
            <span
              key={col}
              data-col={col}
              data-month={label}
              style={{
                ...LABEL_STYLE,
                position: 'absolute',
                top: 0,
                left: `calc(${col} * (var(--cell) + ${GAP_PX}px))`,
                color: 'var(--color-ink-3)',
              }}
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
          {columns.map((week, colIndex) => week.map((date, rowIndex) => renderCell(date, colIndex, rowIndex)))}
        </div>
      </div>
    )
  }

  function renderScrollableRow() {
    const totalCellWidth = weeks * SCROLL_CELL_PX + (weeks - 1) * GAP_PX
    const gridContentHeight = MONTH_LABEL_ROW_PX + 7 * SCROLL_CELL_PX + 6 * GAP_PX
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
        {/* T1-4(a): 요일 라벨(월·수·금) 열을 없애고 격자가 왼쪽 끝부터 시작한다. */}
        <div
          ref={scrollRef}
          onScroll={updateScrollButtons}
          className="scrollbar-thin-fade min-w-0 overflow-x-auto overflow-y-hidden"
          role="group"
          aria-label={`최근 ${weeks}주 활동 지도`}
          style={{ height: gridContentHeight + SCROLLBAR_AREA_PX + BOTTOM_MARGIN_PX, boxSizing: 'border-box' }}
        >
          <div style={{ position: 'relative', width: totalCellWidth, height: gridContentHeight }}>
            {monthLabels.map(({ col, label }) => (
              <span
                key={col}
                data-col={col}
                data-month={label}
                style={{
                  ...LABEL_STYLE,
                  position: 'absolute',
                  top: 0,
                  left: col * (SCROLL_CELL_PX + GAP_PX),
                  color: 'var(--color-ink-3)',
                }}
              >
                {label}
              </span>
            ))}
            <div
              style={{
                position: 'absolute',
                top: MONTH_LABEL_ROW_PX,
                left: 0,
                display: 'grid',
                width: totalCellWidth,
                rowGap: `${GAP_PX}px`,
                columnGap: `${GAP_PX}px`,
                gridTemplateColumns: `repeat(${weeks}, ${SCROLL_CELL_PX}px)`,
                gridTemplateRows: `repeat(7, ${SCROLL_CELL_PX}px)`,
              }}
            >
              {columns.map((week, colIndex) =>
                week.map((date, rowIndex) => (
                  <div key={date} style={{ gridColumn: colIndex + 1, gridRow: rowIndex + 1 }}>
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
    <div className={home ? 'flex h-full min-h-0 flex-col gap-2' : 'flex flex-col gap-2'}>
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
        <div
          ref={home ? setBoxEl : undefined}
          style={
            home
              ? ({ flex: '1 1 0', minHeight: 0, display: 'flex', alignItems: 'flex-start', justifyContent: 'flex-end', overflow: 'hidden', ...makeGridVars(weeks) } as CSSProperties)
              : ({ containerType: 'inline-size', ...makeGridVars(weeks) } as CSSProperties)
          }
        >
          <div
            ref={fixedCell ? scrollRef : undefined}
            className={fixedCell ? 'scrollbar-none overflow-x-auto overflow-y-hidden' : 'overflow-x-visible overflow-y-hidden'}
          >
            {showHomeGrid ? renderClampedRow() : null}
          </div>
        </div>
      )}

      {home && !showHomeGrid ? (
        <p className="flex-none t-sub text-ink-2">
          최근 {weeks}주 중 {columns.flat().filter((date) => (countByDate.get(date) ?? 0) > 0).length}일 기록했어요
        </p>
      ) : null}

      {home ? null : (
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
      )}
    </div>
  )
}

/** 홈 활동 지도 카드 헤더에 들어가는 범례(적음 ■■■■ 많음). */
export function HeatmapLegend() {
  return (
    <div className="flex items-center gap-1 t-cap text-ink-2">
      <span>적음</span>
      {([0, 1, 2, 3] as const).map((level) => (
        <span key={level} className="h-3 w-3 rounded-sm" style={{ background: LEVEL_BG[level] }} />
      ))}
      <span>많음</span>
    </div>
  )
}
