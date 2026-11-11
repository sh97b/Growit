import { useMemo, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Loading } from './Loading'
import { addDays, formatMonthDay, startOfWeekLocal, todayLocalDate } from '../lib/date'
import { fetchActivityMap } from '../lib/stats'

type Props = {
  /** 열(주)의 개수. 52 = 지난 기록의 1년 활동 지도, 12 = 홈의 최근 12주 활동 지도. */
  weeks: number
  hobbyId?: number | null
}

const CELL = 11
const GAP = 3
const ROW_LABEL_WIDTH = 0
const MONTH_LABEL_HEIGHT = 16

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

export function ActivityHeatmap({ weeks, hobbyId }: Props) {
  const today = todayLocalDate()
  const currentWeekStart = startOfWeekLocal(new Date())
  const [selectedDate, setSelectedDate] = useState<string | null>(null)
  // 364칸까지도 Tab만으로 전부 거치면 접근성이 떨어져, 한 칸만 탭 순서에 두고(roving tabindex)
  // 그 칸에서 방향키로 다른 칸으로 옮겨 다니게 한다. 기본 초점은 오늘 칸.
  const [focusedDate, setFocusedDate] = useState<string>(today)
  const cellRefs = useRef(new Map<string, SVGRectElement>())

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

  const width = ROW_LABEL_WIDTH + columns.length * (CELL + GAP)
  const height = MONTH_LABEL_HEIGHT + 7 * (CELL + GAP)

  const monthLabels: { x: number; label: string }[] = []
  let lastMonth = ''
  columns.forEach((week, colIndex) => {
    const label = monthLabel(week[0])
    if (label !== lastMonth) {
      monthLabels.push({ x: ROW_LABEL_WIDTH + colIndex * (CELL + GAP), label })
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

  return (
    <div className="flex flex-col gap-2">
      <svg
        viewBox={`0 0 ${width} ${height}`}
        role="img"
        aria-label={`최근 ${weeks}주 활동 지도`}
        className="w-full"
        style={{ maxWidth: width }}
      >
        {monthLabels.map(({ x, label }) => (
          <text key={x} x={x} y={10} fontSize={10} fill="var(--color-ink-3)">
            {label}
          </text>
        ))}
        {columns.map((week, colIndex) =>
          week.map((date, rowIndex) => {
            const count = countByDate.get(date) ?? 0
            const level = levelOf(count)
            const x = ROW_LABEL_WIDTH + colIndex * (CELL + GAP)
            const y = MONTH_LABEL_HEIGHT + rowIndex * (CELL + GAP)
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
                rx={2}
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

      <div className="flex items-center justify-between gap-3">
        <p className="min-h-5 text-xs text-ink-2" aria-live="polite">
          {selectedDate ? `${formatMonthDay(selectedDate)} · 기록 ${selectedCount}개` : ' '}
        </p>
        <div className="flex items-center gap-1 text-xs text-ink-2">
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
