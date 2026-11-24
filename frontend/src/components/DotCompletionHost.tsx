import { useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useDotCompletionSeen, useDotsCurrent } from '../hooks/useDots'
import { Button } from './Button'
import { Dialog } from './Dialog'
import { DotPicture } from './DotPicture'
import { DotStartFlow } from './DotStartFlow'
import { XIcon } from './icons'

const TITLE_ID = 'dot-complete-title'

/**
 * 도트 그림을 완성했지만 연출을 아직 안 본 경우에만 한 번 보여 준다(서버의 justCompleted).
 * 기록을 저장한 직후(저장 직후 화면, 홈)에 이 컴포넌트가 있으면 그 자리에서 바로 뜬다.
 * 닫으면 서버에 "봤어요"를 알리지만, 그 요청이 실패해도 화면은 먼저 닫는다 — 다음에 한 번 더 보일 수 있을 뿐이다.
 */
export function DotCompletionHost() {
  const query = useDotsCurrent()
  const navigate = useNavigate()
  const markSeen = useDotCompletionSeen()
  const [dismissedIds, setDismissedIds] = useState<number[]>([])
  const [starting, setStarting] = useState(false)
  // 같은 순간 두 번 눌려도 "봤어요" 요청이 한 번만 나가게 한다(상태 갱신보다 빠른 연속 클릭 방지).
  const finishingRef = useRef(false)

  const goal = query.data?.justCompleted ?? null
  const open = goal != null && !dismissedIds.includes(goal.id)

  const finish = (after?: () => void) => {
    if (!goal || finishingRef.current) return
    finishingRef.current = true
    setDismissedIds((ids) => [...ids, goal.id])
    markSeen.mutate(goal.id, {
      onError: (error) => {
        console.warn('완성 연출을 봤다는 표시를 저장하지 못했어요. 다음에 한 번 더 보일 수 있어요.', error)
      },
      onSettled: () => {
        finishingRef.current = false
      },
    })
    after?.()
  }

  return (
    <>
      {open && goal ? (
        <Dialog onClose={() => finish()} labelledBy={TITLE_ID}>
          <div className="flex items-start justify-between gap-3">
            <h2 id={TITLE_ID} className="font-serif t-section font-semibold">
              {goal.goalDays}칸을 모두 채웠어요
            </h2>
            <button
              type="button"
              onClick={() => finish()}
              aria-label="닫기"
              className="tap-min -mr-2 -mt-1 inline-flex flex-none items-center justify-center rounded-full text-ink-2 hover:bg-surface"
            >
              <XIcon width={20} height={20} />
            </button>
          </div>
          <div className="mt-4 flex flex-col gap-3">
            <div className="flex items-center justify-center rounded-xl bg-surface px-2 py-4">
              <DotPicture
                width={goal.width}
                height={goal.height}
                palette={goal.palette}
                cells={goal.cells}
                filledCount={goal.goalDays}
                sparkleFrom={0}
                maxHeight={240}
                ariaLabel={`${goal.title} 도트 그림 ${goal.goalDays}칸 완성`}
              />
            </div>
            <p className="text-body-min leading-[1.7] text-ink">
              {goal.title} 그림이 완성됐어요. 기록한 날마다 한 칸씩 쌓아 만든 그림이에요.
            </p>
          </div>
          <div className="mt-5 flex gap-2 lg:justify-end">
            <Button
              type="button"
              variant="secondary"
              onClick={() => finish(() => setStarting(true))}
              className="flex-1! lg:flex-none!"
            >
              새 그림 시작하기
            </Button>
            <Button type="button" onClick={() => finish(() => navigate('/dots/collection'))} className="flex-1! lg:flex-none!">
              도감에서 보기
            </Button>
          </div>
        </Dialog>
      ) : null}
      {starting ? <DotStartFlow onClose={() => setStarting(false)} /> : null}
    </>
  )
}
