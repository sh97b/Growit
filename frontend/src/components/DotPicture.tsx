import { useEffect, useRef, useState } from 'react'
import type { DotCell } from '../lib/dots'

/** 칸 하나가 차지하는 폭(칸 + 사이 간격)의 위쪽 한도와, 아직 컨테이너 폭을 못 쟀을 때의 임시 값. */
const MAX_PITCH_PX = 26
const FALLBACK_PITCH_PX = 10
const gapFor = (pitch: number) => (pitch >= 14 ? 3 : pitch >= 8 ? 2 : 1)

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

  // 칸 하나의 폭(pitch) = min(가로 ÷ W, 세로 ÷ H)를 내림한 값이라, W×H 어떤 비율이어도 가로·세로 둘 다 컨테이너 안에 들어온다.
  let pitch: number
  if (box != null) {
    pitch = Math.floor(box / Math.max(cols, rows))
  } else {
    const byWidth = width > 0 ? Math.floor(width / cols) : FALLBACK_PITCH_PX
    const byHeight = Math.floor(maxHeight / rows)
    pitch = Math.min(byWidth, byHeight, MAX_PITCH_PX)
  }
  pitch = Math.max(2, pitch)
  const gap = gapFor(pitch)
  const cell = Math.max(1, pitch - gap)

  return (
    <div ref={ref} className="flex w-full justify-center overflow-hidden">
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
