import { useEffect, useRef, useState } from 'react'
import { type DotGoal, readSeenFilledCount, writeSeenFilledCount } from '../lib/dots'

/** 그림이 카드 안에서 너무 길어지지 않게 하는 높이 상한(px). 데스크톱 사이드 열에 다른 카드와 같이 들어가야 한다. */
const MAX_HEIGHT_PX = 220
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

/**
 * 도트 그림. 서버가 칸을 "채워지는 순서"로 내려주므로 앞의 filledCount칸만 팔레트 색으로 칠하고,
 * 나머지는 연한 회갈색 윤곽으로 둔다. 이 기기에서 지난번에 본 뒤로 새로 칠해진 칸은 한 번 살짝 반짝인다.
 * 칸 크기는 카드 너비와 높이 상한에 맞춰 계산한다.
 */
export function DotGridView({ goal }: { goal: DotGoal }) {
  const { ref, width } = useElementWidth()
  // 처음 보여 줄 때의 기준만 필요해서 마운트 때 한 번 읽는다(그림이 바뀌면 호출하는 쪽이 key로 새로 만든다).
  const [seenCount] = useState(() => readSeenFilledCount(goal.id))
  const sparkleFrom = seenCount != null && goal.filledCount > seenCount ? seenCount : goal.filledCount

  useEffect(() => {
    writeSeenFilledCount(goal.id, goal.filledCount)
  }, [goal.id, goal.filledCount])

  const gap = width > 0 && width / goal.width < 14 ? 2 : 3
  const byWidth = width > 0 ? Math.floor((width - gap * (goal.width - 1)) / goal.width) : FALLBACK_CELL_PX
  const byHeight = Math.floor((MAX_HEIGHT_PX - gap * (goal.height - 1)) / goal.height)
  const cell = Math.max(MIN_CELL_PX, Math.min(byWidth, byHeight, MAX_CELL_PX))

  return (
    <div ref={ref} className="flex w-full justify-center">
      <div
        role="img"
        aria-label={`도트 그림 ${goal.goalDays}칸 중 ${goal.filledCount}칸 완성`}
        style={{
          display: 'grid',
          gridTemplateColumns: `repeat(${goal.width}, ${cell}px)`,
          gridTemplateRows: `repeat(${goal.height}, ${cell}px)`,
          gap: `${gap}px`,
        }}
      >
        {goal.cells.map((dotCell, index) => {
          const filled = index < goal.filledCount
          const sparkle = filled && index >= sparkleFrom
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
                      backgroundColor: goal.palette[dotCell.color],
                      boxShadow: 'inset 0 0 0 1px rgba(43, 40, 35, 0.22)',
                      animationDelay: sparkle ? `${Math.min((index - sparkleFrom) * 70, 700)}ms` : undefined,
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
