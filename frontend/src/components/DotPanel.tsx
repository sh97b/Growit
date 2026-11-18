import { useState } from 'react'
import { useDotsCurrent } from '../hooks/useDots'
import { Button } from './Button'
import { DotGridView } from './DotGridView'
import { Loading } from './Loading'
import { Note } from './Note'

/** 홈 활동 지도 카드의 "도트" 보기: 진행 중인 그림, 방금 완성한 그림, 아직 없는 상태를 차분하게 보여 준다. */
export function DotPanel() {
  const query = useDotsCurrent()
  const [startClicked, setStartClicked] = useState(false)

  if (query.isLoading) {
    return <Loading />
  }

  if (query.isError || !query.data) {
    return (
      <div className="flex flex-col items-start gap-2">
        <p className="text-body-min text-ink-2">도트 그림을 지금은 불러오지 못했어요. 기록은 그대로 안전해요.</p>
        <button
          type="button"
          onClick={() => query.refetch()}
          className="inline-flex min-h-11 items-center rounded-full px-2 -mx-2 text-caption-min font-medium text-base-ink hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]"
        >
          다시 불러오기
        </button>
      </div>
    )
  }

  const { goal, justCompleted } = query.data
  const shown = goal ?? justCompleted

  if (shown) {
    const done = goal == null
    const remaining = shown.goalDays - shown.filledCount
    return (
      <div className="flex flex-col gap-3">
        <DotGridView key={shown.id} goal={shown} />
        <div className="flex flex-col gap-0.5 text-center">
          <p className="text-body-min font-medium text-ink">{done ? `${shown.title} 그림이 완성됐어요` : shown.title}</p>
          <p className="text-body-min text-ink-2">
            {done ? `${shown.goalDays}칸을 모두 채웠어요` : `${shown.goalDays}칸 중 ${shown.filledCount}칸`}
          </p>
          {!done ? (
            <p className="text-caption-min text-ink-3">
              {shown.filledCount === 0
                ? '기록한 날마다 한 칸씩 채워져요'
                : `완성까지 ${remaining}칸 남았어요`}
            </p>
          ) : null}
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col items-start gap-3">
      <div className="flex flex-col gap-1">
        <p className="text-body-min font-medium text-ink">도트 그림으로 기록을 쌓아 볼까요?</p>
        <p className="text-caption-min text-ink-3">기록한 날마다 그림이 한 칸씩 채워져요. 쉬는 날은 그대로 두면 돼요.</p>
      </div>
      <Button type="button" onClick={() => setStartClicked(true)} aria-describedby={startClicked ? 'dot-soon' : undefined}>
        시작하기
      </Button>
      {startClicked ? (
        <div id="dot-soon" className="w-full">
          <Note variant="slate">준비 중이에요. 조금만 기다려 주세요.</Note>
        </div>
      ) : null}
    </div>
  )
}
