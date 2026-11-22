import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useElementSize } from '../hooks/useElementSize'
import { CHART_COLORS } from '../lib/chartColors'

/**
 * 성장 화면의 차트 부품(SVG 직접). 컨테이너의 실제 폭·높이를 재서 그리고, 글자는 스케일되지 않게 고정 px(12 이상)로 둔다.
 * 색만으로 구분하지 않도록 범례·값 라벨·툴팁·표를 함께 둔다.
 */

const AXIS_WIDTH = 46
const PAD_TOP = 14
const PAD_BOTTOM = 28
const PAD_X = 8
const MIN_BAR = 8
const MAX_BAR = 40
const FONT = 12
const MIN_CHART_HEIGHT = 140
/** 가로 스크롤바가 생길 때 그만큼 그림을 줄여 아래쪽 글자가 잘리지 않게 한다. */
const SCROLLBAR_ROOM = 12

export type BarPart = { value: number; color: string }

export type BarItem = {
  id: string
  /** x축 라벨. 없으면 그리지 않는다. */
  tick?: string
  /** 아래부터 쌓는 조각들. 하나면 단색 막대다. */
  parts: BarPart[]
  /** 막대 위 값 라벨(자리가 충분하고 값이 0이 아닐 때만). */
  valueText?: string
  /** 호버·포커스·탭에 보이는 설명. 스크린 리더 라벨로도 쓴다. */
  tooltip: string
  /** 이번 주처럼 아직 진행 중인 막대(연하게). */
  current?: boolean
}

/**
 * 세로 막대(단색 또는 쌓은 막대). 폭이 모자라면(막대 최소 폭 8px, 모바일은 minSlot을 키워) 그림 영역만 가로로 밀고,
 * 처음에는 맨 오른쪽(최근)이 보이게 한다. y축 눈금은 왼쪽에 고정한다.
 */
export function BarChart({
  items,
  yMax,
  yTicks,
  formatTick,
  ariaLabel,
  minSlot,
}: {
  items: BarItem[]
  yMax: number
  yTicks: number[]
  formatTick: (value: number) => string
  ariaLabel: string
  /** 막대 하나가 차지하는 최소 가로 칸(px). 데스크톱 12, 모바일 24 안팎. */
  minSlot: number
}) {
  const { ref, width, height } = useElementSize<HTMLDivElement>()
  const scrollRef = useRef<HTMLDivElement | null>(null)
  const [active, setActive] = useState<number | null>(null)

  const n = Math.max(1, items.length)
  const plotViewW = Math.max(0, width - AXIS_WIDTH)
  const slot = Math.max(minSlot, (plotViewW - PAD_X * 2) / n)
  const contentW = Math.ceil(PAD_X * 2 + slot * n)
  const scrolls = contentW > plotViewW + 1
  const totalH = Math.max(MIN_CHART_HEIGHT, height) - (scrolls ? SCROLLBAR_ROOM : 0)
  const plotH = totalH - PAD_TOP - PAD_BOTTOM
  const yOf = (value: number) => PAD_TOP + plotH - (value / yMax) * plotH
  const barW = Math.max(MIN_BAR, Math.min(MAX_BAR, slot * 0.66))

  // 데이터가 바뀌거나 폭이 정해지면 최근(오른쪽 끝)부터 보이게 한다.
  useEffect(() => {
    const el = scrollRef.current
    if (el) el.scrollLeft = el.scrollWidth
  }, [contentW, items.length])

  const activeItem = active != null ? items[active] : null
  const tipX = Math.min(Math.max(PAD_X + slot * (active ?? 0) + slot / 2, 110), Math.max(110, contentW - 110))

  return (
    <div
      ref={ref}
      className="flex h-[240px] min-h-0 w-full flex-none overflow-hidden lg:h-auto lg:flex-1"
      onMouseLeave={() => setActive(null)}
    >
      {width > 0 ? (
        <>
          <svg width={AXIS_WIDTH} height={totalH} aria-hidden="true" className="block flex-none">
            {yTicks.map((tick) => (
              <text key={tick} x={AXIS_WIDTH - 6} y={yOf(tick) + 4} textAnchor="end" fontSize={FONT} fill={CHART_COLORS.text}>
                {formatTick(tick)}
              </text>
            ))}
          </svg>
          <div ref={scrollRef} className="scrollbar-thin-fade relative min-w-0 flex-1 overflow-x-auto overflow-y-hidden">
            <div className="relative" style={{ width: contentW, height: totalH }}>
              <svg
                width={contentW}
                height={totalH}
                role="group"
                aria-label={ariaLabel}
                className="block"
                onClick={() => setActive(null)}
              >
                {yTicks.map((tick) => (
                  <line
                    key={tick}
                    x1={0}
                    x2={contentW}
                    y1={yOf(tick)}
                    y2={yOf(tick)}
                    stroke={tick === 0 ? CHART_COLORS.axis : CHART_COLORS.grid}
                    strokeDasharray={tick === 0 ? undefined : '3 4'}
                  />
                ))}
                {items.map((item, index) => {
                  const x = PAD_X + slot * index + (slot - barW) / 2
                  const total = item.parts.reduce((sum, part) => sum + part.value, 0)
                  const showValue = !!item.valueText && total > 0 && slot >= item.valueText.length * 9 + 4
                  let y = yOf(0)
                  const focus = () => setActive(index)
                  return (
                    <g
                      key={item.id}
                      tabIndex={0}
                      role="img"
                      aria-label={item.tooltip}
                      className="outline-none"
                      onMouseEnter={focus}
                      onFocus={focus}
                      onBlur={() => setActive((current) => (current === index ? null : current))}
                      onClick={(event) => {
                        event.stopPropagation()
                        focus()
                      }}
                    >
                      <rect
                        x={PAD_X + slot * index}
                        y={PAD_TOP - 4}
                        width={slot}
                        height={plotH + 4}
                        rx={4}
                        fill={active === index ? 'var(--color-surface)' : 'transparent'}
                      />
                      {total === 0 ? (
                        <rect
                          x={x}
                          y={yOf(0) - 6}
                          width={barW}
                          height={6}
                          rx={2}
                          fill="none"
                          stroke={CHART_COLORS.axis}
                          strokeDasharray="3 2"
                        />
                      ) : (
                        <g opacity={item.current ? 0.55 : 1}>
                          {item.parts.map((part, partIndex) => {
                            if (part.value <= 0) return null
                            const h = Math.max(3, (part.value / yMax) * plotH)
                            y -= h
                            return (
                              <rect
                                key={partIndex}
                                x={x}
                                y={y}
                                width={barW}
                                height={h}
                                rx={item.parts.length === 1 ? 3 : 0}
                                fill={part.color}
                                stroke={item.parts.length > 1 ? 'white' : undefined}
                                strokeWidth={item.parts.length > 1 && h > 3 ? 1 : 0}
                              />
                            )
                          })}
                        </g>
                      )}
                      {showValue ? (
                        <text
                          x={x + barW / 2}
                          y={yOf(total) - 4}
                          textAnchor="middle"
                          fontSize={FONT}
                          fill={CHART_COLORS.strong}
                        >
                          {item.valueText}
                        </text>
                      ) : null}
                      {item.tick ? (
                        <text
                          x={Math.min(Math.max(x + barW / 2, 22), contentW - 22)}
                          y={totalH - 8}
                          textAnchor="middle"
                          fontSize={FONT}
                          fill={CHART_COLORS.text}
                          fontWeight={item.current ? 600 : 400}
                        >
                          {item.tick}
                        </text>
                      ) : null}
                    </g>
                  )
                })}
              </svg>
              {activeItem ? (
                <div
                  role="tooltip"
                  className="pointer-events-none absolute z-10 whitespace-nowrap rounded-md bg-ink px-2.5 py-1.5 text-caption-min text-white shadow"
                  style={{ left: tipX, top: 0, transform: 'translateX(-50%)' }}
                >
                  {activeItem.tooltip}
                </div>
              ) : null}
            </div>
          </div>
        </>
      ) : null}
    </div>
  )
}

export type HBarItem = { label: string; value: number; valueText: string; muted?: boolean }

/** 가로 막대. 폭은 컨테이너에 맞추고 글자는 고정 px다. muted이면 연하게 그린다(예: 보관한 분야). */
export function HBarChart({
  items,
  ariaLabel,
  color = CHART_COLORS.base,
}: {
  items: HBarItem[]
  ariaLabel: string
  color?: string
}) {
  const { ref, width } = useElementSize<HTMLDivElement>()
  const rowH = 32
  const labelW = 116
  const valueW = 84
  const max = Math.max(1, ...items.map((item) => item.value))
  const barMax = Math.max(20, width - labelW - valueW)

  return (
    <div ref={ref} className="w-full flex-none">
      {width > 0 ? (
        <svg width={width} height={Math.max(1, items.length) * rowH} role="img" aria-label={ariaLabel} className="block">
          {items.map((item, index) => {
            const y = index * rowH
            const w = item.value === 0 ? 0 : Math.max(MIN_BAR, (item.value / max) * barMax)
            const label = item.label.length > 8 ? `${item.label.slice(0, 7)}…` : item.label
            return (
              <g key={`${item.label}-${index}`} opacity={item.muted ? 0.55 : 1}>
                <text x={0} y={y + 21} fontSize={13} fill={CHART_COLORS.strong}>
                  {label}
                </text>
                <rect x={labelW} y={y + 7} width={w} height={rowH - 14} rx={3} fill={color} />
                {item.value > 0 ? (
                  <text x={labelW + w + 6} y={y + 21} fontSize={FONT} fill={CHART_COLORS.text}>
                    {item.valueText}
                  </text>
                ) : null}
              </g>
            )
          })}
        </svg>
      ) : null}
    </div>
  )
}

/** 범례: 색 칸 옆에 이름을 적어 색만으로 구분하지 않게 한다. */
export function Legend({ items }: { items: { label: string; color: string; faded?: boolean }[] }) {
  return (
    <ul className="flex flex-wrap gap-x-4 gap-y-1 text-caption-min text-ink-2" aria-label="범례">
      {items.map((item) => (
        <li key={item.label} className="inline-flex items-center gap-1.5">
          <span
            aria-hidden="true"
            className="inline-block h-3 w-3 rounded-sm"
            style={{ background: item.color, opacity: item.faded ? 0.55 : 1 }}
          />
          {item.label}
        </li>
      ))}
    </ul>
  )
}

export type TableData = { headers: string[]; rows: string[][]; numeric?: number[] }

/** 차트와 같은 숫자를 표로. 넘칠 때만 이 영역 안에서 얇게 스크롤하고, 머리글은 위에 붙는다. */
function DataTable({ caption, data }: { caption: string; data: TableData }) {
  return (
    <div
      className="scrollbar-thin-fade min-h-0 w-full overflow-auto overscroll-contain lg:flex-1"
      tabIndex={0}
      role="region"
      aria-label={`${caption} 표`}
    >
      <table className="w-full border-collapse text-left text-caption-min">
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr>
            {data.headers.map((header, column) => (
              <th
                key={header}
                scope="col"
                className={[
                  'sticky top-0 border-b border-line bg-white px-2 py-1.5 font-semibold',
                  data.numeric?.includes(column) ? 'text-right' : '',
                ].join(' ')}
              >
                {header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {data.rows.map((row, rowIndex) => (
            <tr key={rowIndex}>
              {row.map((cell, column) => (
                <td
                  key={column}
                  className={[
                    'border-b border-line px-2 py-1',
                    data.numeric?.includes(column) ? 'text-right tabular-nums' : '',
                  ].join(' ')}
                >
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/**
 * 차트 카드: 헤더(제목 + [그래프|표] 토글), 본문(차트 또는 표, 남는 높이를 채움), 하단 캡션.
 * 카드 자체는 스크롤하지 않는다. 막대 차트가 아닌 목록형 본문(scrollBody)만 본문 안에서 한 겹 스크롤한다.
 */
export function ChartCard({
  title,
  chart,
  table,
  caption,
  scrollBody = false,
}: {
  title: string
  chart: ReactNode
  /** 없으면 토글을 그리지 않는다(데이터가 부족한 카드 등). */
  table?: TableData
  caption?: ReactNode
  scrollBody?: boolean
}) {
  const [view, setView] = useState<'chart' | 'table'>('chart')
  const showTable = view === 'table' && !!table

  return (
    <section
      aria-label={title}
      className="flex min-w-0 flex-col gap-2 rounded-xl border border-line bg-white p-4 lg:min-h-0 lg:p-5"
    >
      <div className="flex flex-none items-center justify-between gap-2">
        <h2 className="min-w-0 truncate text-card-title font-semibold">{title}</h2>
        {table ? (
          <div role="radiogroup" aria-label={`${title} 보기 방식`} className="flex flex-none gap-0.5 rounded-lg border border-line-2 bg-white p-0.5">
            {(['chart', 'table'] as const).map((key) => (
              <button
                key={key}
                type="button"
                role="radio"
                aria-checked={view === key}
                onClick={() => setView(key)}
                className={[
                  'inline-flex min-h-11 items-center justify-center rounded-md px-3 text-caption-min font-medium lg:min-h-8',
                  view === key ? 'bg-ink text-white' : 'text-ink-2',
                ].join(' ')}
              >
                {key === 'chart' ? '그래프' : '표'}
              </button>
            ))}
          </div>
        ) : null}
      </div>
      <div
        className={[
          'flex min-h-0 flex-col lg:flex-1',
          !showTable && scrollBody ? 'scrollbar-thin-fade lg:overflow-y-auto' : '',
        ].join(' ')}
      >
        {showTable ? <DataTable caption={title} data={table} /> : chart}
      </div>
      {caption ? <div className="flex flex-none flex-col gap-1">{caption}</div> : null}
    </section>
  )
}
