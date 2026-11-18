import { useEffect, useRef, useState } from 'react'
import type { DotCell } from '../lib/dots'

const MIN_CELL_PX = 6
const MAX_CELL_PX = 22
const FALLBACK_CELL_PX = 8

function useElementWidth() {
  const ref = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(0)
  useEffect(() => {
    const element = ref.current
    if (!element) return
    const observer = new ResizeObserver((entries) => {
      setWidth(Math.floor(entries[0].contentRect.width))
    })
    observer.observe(element)
    return () => observer.disconnect()
  }, [])
  return { ref, width }
}

type Props = {
  width: number
  height: number
  palette: string[]
  /** 채워지는 순서대로. 앞의 filledCount칸만 팔레트 색으로 칠하고 나머지는 연한 윤곽으로 둔다. */
  cells: DotCell[]
  filledCount: number
  ariaLabel: string
  /** 카드 너비에 맞추되 이 높이를 넘지 않게 칸 크기를 계산한다(기본). */
  maxHeight?: number
  /** 정해진 정사각형 상자(px) 안에 맞춘다. 미리보기 작은 그림처럼 자리를 고정해야 할 때 쓴다. */
  box?: number
  /** 이 번호부터 칠해진 칸은 한 번 살짝 반짝인다. */
  sparkleFrom?: number
}

/** 도트 그림 하나를 둥근 칸으로 그린다. 칸 크기는 카드 너비·높이 상한(또는 고정 상자)에 맞춰 계산한다. */
export function DotPicture({
  width: cols,
  height: rows,
  palette,
  cells,
  filledCount,
  ariaLabel,
  maxHeight = 220,
  box,
  sparkleFrom,
}: Props) {
  const { ref, width } = useElementWidth()
  const sparkleStart = sparkleFrom ?? filledCount

  let gap: number
  let cell: number
  if (box != null) {
    const longSide = Math.max(cols, rows)
    gap = box / longSide < 7 ? 1 : 2
    cell = Math.max(2, Math.floor((box - gap * (longSide - 1)) / longSide))
  } else {
    gap = width > 0 && width / cols < 14 ? 2 : 3
    const byWidth = width > 0 ? Math.floor((width - gap * (cols - 1)) / cols) : FALLBACK_CELL_PX
    const byHeight = Math.floor((maxHeight - gap * (rows - 1)) / rows)
    cell = Math.max(MIN_CELL_PX, Math.min(byWidth, byHeight, MAX_CELL_PX))
  }

  return (
    <div ref={ref} className="flex w-full justify-center">
      <div
        role="img"
        aria-label={ariaLabel}
        style={{
          display: 'grid',
          gridTemplateColumns: `repeat(${cols}, ${cell}px)`,
          gridTemplateRows: `repeat(${rows}, ${cell}px)`,
          gap: `${gap}px`,
        }}
      >
        {cells.map((dotCell, index) => {
          const filled = index < filledCount
          const sparkle = filled && index >= sparkleStart
          return (
            <span
              key={`${dotCell.x}-${dotCell.y}`}
              aria-hidden="true"
              className={sparkle ? 'dot-sparkle' : undefined}
              style={{
                gridColumn: dotCell.x + 1,
                gridRow: dotCell.y + 1,
                borderRadius: '32%',
                boxSizing: 'border-box',
                ...(filled
                  ? {
                      backgroundColor: palette[dotCell.color],
                      boxShadow: 'inset 0 0 0 1px rgba(43, 40, 35, 0.22)',
                      animationDelay: sparkle ? `${Math.min((index - sparkleStart) * 70, 700)}ms` : undefined,
                    }
                  : { border: '1px solid var(--color-line-2)' }),
              }}
            />
          )
        })}
      </div>
    </div>
  )
}
