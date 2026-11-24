import { useEffect, useState } from 'react'
import { type DotGoal, readSeenFilledCount, writeSeenFilledCount } from '../lib/dots'
import { DotPicture } from './DotPicture'

/** 진행 중이거나 완성한 도트 그림. 이 기기에서 지난번에 본 뒤 새로 칠해진 칸은 한 번 살짝 반짝인다. */
export function DotGridView({ goal, maxHeight, fit = false }: { goal: DotGoal; maxHeight?: number; fit?: boolean }) {
  // 처음 보여 줄 때의 기준만 필요해서 마운트 때 한 번 읽는다(그림이 바뀌면 호출하는 쪽이 key로 새로 만든다).
  const [seenCount] = useState(() => readSeenFilledCount(goal.id))
  const sparkleFrom = seenCount != null && goal.filledCount > seenCount ? seenCount : goal.filledCount

  useEffect(() => {
    writeSeenFilledCount(goal.id, goal.filledCount)
  }, [goal.id, goal.filledCount])

  return (
    <DotPicture
      width={goal.width}
      height={goal.height}
      palette={goal.palette}
      cells={goal.cells}
      filledCount={goal.filledCount}
      sparkleFrom={sparkleFrom}
      maxHeight={maxHeight}
      fit={fit}
      ariaLabel={`도트 그림 ${goal.goalDays}칸 중 ${goal.filledCount}칸 완성`}
    />
  )
}
