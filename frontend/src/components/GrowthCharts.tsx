import type { ReactNode } from 'react'
import { CHART_COLORS } from '../lib/chartColors'

/** 성장 화면의 차트 부품. 새 라이브러리 없이 SVG로 직접 그린다. 색만으로 구분하지 않도록 범례·값 라벨을 함께 둔다. */

const VIEW_WIDTH = 600
const PAD_LEFT = 30
const PAD_RIGHT = 8
const PAD_TOP = 18
const PAD_BOTTOM = 26

function niceMax(value: number): number {
  return Math.max(1, value)
}

function labelStep(count: number): number {
  return count <= 14 ? 1 : Math.ceil(count / 8)
}

export type BarItem = { label: string; value: number }

/** 세로 막대. 값이 0인 칸은 점선 테두리의 빈 막대로 둬서 "쉬어 간" 칸도 자리가 보인다. */
export function VBarChart({
  items,
  ariaLabel,
  color = CHART_COLORS.base,
  height = 200,
  unit = '',
}: {
  items: BarItem[]
  ariaLabel: string
  color?: string
  height?: number
  unit?: string
}) {
  const max = niceMax(Math.max(0, ...items.map((item) => item.value)))
  const plotW = VIEW_WIDTH - PAD_LEFT - PAD_RIGHT
  const plotH = height - PAD_TOP - PAD_BOTTOM
  const slot = plotW / Math.max(1, items.length)
  const barW = Math.min(36, slot * 0.66)
  const step = labelStep(items.length)
  const showValues = items.length <= 14

  return (
    <svg
      role="img"
      aria-label={ariaLabel}
      viewBox={`0 0 ${VIEW_WIDTH} ${height}`}
      className="h-auto w-full max-w-full"
    >
      <line x1={PAD_LEFT} x2={VIEW_WIDTH - PAD_RIGHT} y1={PAD_TOP + plotH} y2={PAD_TOP + plotH} stroke={CHART_COLORS.grid} />
      <line x1={PAD_LEFT} x2={VIEW_WIDTH - PAD_RIGHT} y1={PAD_TOP} y2={PAD_TOP} stroke={CHART_COLORS.grid} strokeDasharray="3 4" />
      <text x={PAD_LEFT - 6} y={PAD_TOP + 4} textAnchor="end" fontSize="12" fill={CHART_COLORS.text}>
        {max}
      </text>
      <text x={PAD_LEFT - 6} y={PAD_TOP + plotH + 4} textAnchor="end" fontSize="12" fill={CHART_COLORS.text}>
        0
      </text>
      {items.map((item, index) => {
        const x = PAD_LEFT + slot * index + (slot - barW) / 2
        const h = item.value === 0 ? 0 : Math.max(3, (item.value / max) * plotH)
        return (
          <g key={`${item.label}-${index}`}>
            {item.value === 0 ? (
              <rect
                x={x}
                y={PAD_TOP + plotH - 6}
                width={barW}
                height={6}
                rx={2}
                fill="none"
                stroke={CHART_COLORS.muted}
                strokeDasharray="3 2"
              />
            ) : (
              <rect x={x} y={PAD_TOP + plotH - h} width={barW} height={h} rx={3} fill={color} />
            )}
            {showValues && item.value > 0 ? (
              <text x={x + barW / 2} y={PAD_TOP + plotH - h - 4} textAnchor="middle" fontSize="12" fill={CHART_COLORS.text}>
                {item.value}
                {unit}
              </text>
            ) : null}
            {index % step === 0 ? (
              <text x={x + barW / 2} y={height - 8} textAnchor="middle" fontSize="12" fill={CHART_COLORS.text}>
                {item.label}
              </text>
            ) : null}
          </g>
        )
      })}
    </svg>
  )
}

export type StackItem = { label: string; good: number; neutral: number; hard: number }

/** 쌓은 막대: 아래부터 좋아요·보통이에요·힘들어요. 높이는 그 구간의 기록 수다. */
export function StackedBarChart({
  items,
  ariaLabel,
  height = 200,
}: {
  items: StackItem[]
  ariaLabel: string
  height?: number
}) {
  const max = niceMax(Math.max(0, ...items.map((item) => item.good + item.neutral + item.hard)))
  const plotW = VIEW_WIDTH - PAD_LEFT - PAD_RIGHT
  const plotH = height - PAD_TOP - PAD_BOTTOM
  const slot = plotW / Math.max(1, items.length)
  const barW = Math.min(30, slot * 0.7)
  const step = labelStep(items.length)
  const base = PAD_TOP + plotH

  return (
    <svg role="img" aria-label={ariaLabel} viewBox={`0 0 ${VIEW_WIDTH} ${height}`} className="h-auto w-full max-w-full">
      <line x1={PAD_LEFT} x2={VIEW_WIDTH - PAD_RIGHT} y1={base} y2={base} stroke={CHART_COLORS.grid} />
      <line x1={PAD_LEFT} x2={VIEW_WIDTH - PAD_RIGHT} y1={PAD_TOP} y2={PAD_TOP} stroke={CHART_COLORS.grid} strokeDasharray="3 4" />
      <text x={PAD_LEFT - 6} y={PAD_TOP + 4} textAnchor="end" fontSize="12" fill={CHART_COLORS.text}>
        {max}
      </text>
      <text x={PAD_LEFT - 6} y={base + 4} textAnchor="end" fontSize="12" fill={CHART_COLORS.text}>
        0
      </text>
      {items.map((item, index) => {
        const x = PAD_LEFT + slot * index + (slot - barW) / 2
        const parts = [
          { value: item.good, color: CHART_COLORS.base },
          { value: item.neutral, color: CHART_COLORS.neutral },
          { value: item.hard, color: CHART_COLORS.hard },
        ]
        let y = base
        return (
          <g key={`${item.label}-${index}`}>
            {parts.map((part, partIndex) => {
              if (part.value === 0) return null
              const h = (part.value / max) * plotH
              y -= h
              return (
                <rect
                  key={partIndex}
                  x={x}
                  y={y}
                  width={barW}
                  height={h}
                  fill={part.color}
                  stroke="white"
                  strokeWidth={h > 3 ? 1 : 0}
                />
              )
            })}
            {index % step === 0 ? (
              <text x={x + barW / 2} y={height - 8} textAnchor="middle" fontSize="12" fill={CHART_COLORS.text}>
                {item.label}
              </text>
            ) : null}
          </g>
        )
      })}
    </svg>
  )
}

export type HBarItem = { label: string; value: number; valueText: string; muted?: boolean }

/** 가로 막대. muted이면 연하게 그린다(예: 보관한 분야). */
export function HBarChart({
  items,
  ariaLabel,
  color = CHART_COLORS.base,
}: {
  items: HBarItem[]
  ariaLabel: string
  color?: string
}) {
  const rowH = 30
  const labelW = 112
  const valueW = 70
  const width = 480
  const max = niceMax(Math.max(0, ...items.map((item) => item.value)))
  const barMax = width - labelW - valueW

  return (
    <svg
      role="img"
      aria-label={ariaLabel}
      viewBox={`0 0 ${width} ${Math.max(1, items.length) * rowH}`}
      className="h-auto w-full max-w-full"
    >
      {items.map((item, index) => {
        const y = index * rowH
        const w = item.value === 0 ? 0 : Math.max(3, (item.value / max) * barMax)
        const label = item.label.length > 8 ? `${item.label.slice(0, 7)}…` : item.label
        return (
          <g key={`${item.label}-${index}`} opacity={item.muted ? 0.5 : 1}>
            <text x={0} y={y + 19} fontSize="13" fill="var(--color-ink)">
              {label}
            </text>
            <rect x={labelW} y={y + 6} width={w} height={rowH - 12} rx={3} fill={color} />
            <text x={labelW + w + 6} y={y + 19} fontSize="12" fill={CHART_COLORS.text}>
              {item.valueText}
            </text>
          </g>
        )
      })}
    </svg>
  )
}

/** 범례: 색 칸 옆에 이름을 적어 색만으로 구분하지 않게 한다. */
export function Legend({ items }: { items: { label: string; color: string }[] }) {
  return (
    <ul className="flex flex-wrap gap-x-4 gap-y-1 text-caption-min text-ink-2" aria-label="범례">
      {items.map((item) => (
        <li key={item.label} className="inline-flex items-center gap-1.5">
          <span aria-hidden="true" className="inline-block h-3 w-3 rounded-sm" style={{ background: item.color }} />
          {item.label}
        </li>
      ))}
    </ul>
  )
}

/** 같은 숫자를 표로 보여 준다. 기본은 접혀 있고, 길면 안에서 스크롤한다. */
export function ChartTable({ caption, headers, rows }: { caption: string; headers: string[]; rows: string[][] }) {
  return (
    <details className="group text-caption-min">
      <summary className="inline-flex min-h-11 cursor-pointer items-center rounded-lg px-1 font-medium text-base-ink">
        표로 보기
      </summary>
      <div className="max-h-48 overflow-auto overscroll-contain" tabIndex={0} role="region" aria-label={`${caption} 표`}>
        <table className="w-full border-collapse text-left">
          <caption className="sr-only">{caption}</caption>
          <thead>
            <tr>
              {headers.map((header) => (
                <th key={header} scope="col" className="sticky top-0 border-b border-line bg-white px-2 py-1.5 font-semibold">
                  {header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row, rowIndex) => (
              <tr key={rowIndex}>
                {row.map((cell, cellIndex) => (
                  <td key={cellIndex} className="border-b border-line px-2 py-1.5">
                    {cell}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </details>
  )
}

/** 차트 하나를 담는 카드. 웹에서는 카드 안에서만 스크롤한다. */
export function ChartCard({
  title,
  children,
  className = '',
}: {
  title: string
  children: ReactNode
  className?: string
}) {
  return (
    <section
      aria-label={title}
      className={[
        'flex min-w-0 flex-col gap-2 rounded-xl border border-line bg-white p-5 lg:min-h-0 lg:overflow-y-auto lg:overscroll-contain',
        className,
      ].join(' ')}
    >
      <h2 className="text-card-title font-semibold">{title}</h2>
      {children}
    </section>
  )
}
