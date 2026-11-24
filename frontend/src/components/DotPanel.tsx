import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useDotAlbum, useDotsCurrent, useStopDot } from '../hooks/useDots'
import type { DotGoal } from '../lib/dots'
import { Button } from './Button'
import { Dialog } from './Dialog'
import { DotGridView } from './DotGridView'
import { DotPicture } from './DotPicture'
import { DotStartFlow } from './DotStartFlow'
import { MoreIcon } from './icons'
import { Loading } from './Loading'
import { Note } from './Note'

const STOP_TITLE_ID = 'dot-stop-title'

/** 카드 아래의 작은 링크. 완성한 그림이 모여 있는 도감으로 간다. */
function CollectionLink() {
  return (
    <Link
      to="/dots/collection"
      className="inline-flex min-h-11 items-center self-center rounded-full px-2 text-caption-min font-medium text-base-ink no-underline hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]"
    >
      도감 보기
    </Link>
  )
}

/** 작은 메뉴에서 "그만두기"를 고르면 한 번 더 물어본다. 그만둔 그림은 보관만 되고 화면에서는 사라진다. */
function StopMenu({ goal }: { goal: DotGoal }) {
  const [menuOpen, setMenuOpen] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const stoppingRef = useRef(false)
  const stop = useStopDot()

  useEffect(() => {
    if (!menuOpen) return
    const handlePointer = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setMenuOpen(false)
    }
    const handleKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setMenuOpen(false)
    }
    document.addEventListener('mousedown', handlePointer)
    document.addEventListener('keydown', handleKey)
    return () => {
      document.removeEventListener('mousedown', handlePointer)
      document.removeEventListener('keydown', handleKey)
    }
  }, [menuOpen])

  const closeConfirm = () => {
    stop.reset()
    setConfirming(false)
  }

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        aria-label="도트 그림 메뉴"
        aria-haspopup="true"
        aria-expanded={menuOpen}
        onClick={() => setMenuOpen((open) => !open)}
        className="tap-min inline-flex items-center justify-center rounded-full text-ink-2 hover:bg-white"
      >
        <MoreIcon width={20} height={20} />
      </button>
      {menuOpen ? (
        <div className="absolute right-0 top-full z-10 mt-1 min-w-[9rem] rounded-xl border border-line-2 bg-white p-1 shadow-md">
          <Link
            to="/dots/collection"
            className="tap-min flex w-full items-center rounded-lg px-3 text-left text-body-min text-ink no-underline hover:bg-surface"
          >
            도감 보기
          </Link>
          <button
            type="button"
            onClick={() => {
              setMenuOpen(false)
              setConfirming(true)
            }}
            className="tap-min flex w-full items-center rounded-lg px-3 text-left text-body-min text-ink hover:bg-surface"
          >
            그만두기
          </button>
        </div>
      ) : null}
      {confirming ? (
        <Dialog onClose={closeConfirm} labelledBy={STOP_TITLE_ID}>
          <h2 id={STOP_TITLE_ID} className="font-serif t-section font-semibold">
            이 그림을 그만둘까요?
          </h2>
          <p className="mt-3 text-body-min leading-[1.7] text-ink">이 그림은 보관만 돼요. 새로 시작할 수 있어요.</p>
          {stop.isError ? (
            <div className="mt-3">
              <Note variant="sand">지금은 그만두지 못했어요. 잠시 후 다시 시도해 주세요.</Note>
            </div>
          ) : null}
          <div className="mt-5 flex gap-2 lg:justify-end">
            <Button type="button" variant="secondary" onClick={closeConfirm} className="flex-1! lg:flex-none!">
              계속 채울게요
            </Button>
            <Button
              type="button"
              disabled={stop.isPending}
              onClick={() => {
                if (stoppingRef.current || stop.isPending) return
                stoppingRef.current = true
                stop.mutate(goal.id, {
                  onSuccess: closeConfirm,
                  onSettled: () => {
                    stoppingRef.current = false
                  },
                })
              }}
              className="flex-1! lg:flex-none!"
            >
              {stop.isPending ? '그만두는 중이에요' : '그만두기'}
            </Button>
          </div>
        </Dialog>
      ) : null}
    </div>
  )
}

/** 요소의 폭을 재서 돌려준다(카드 폭에 따라 2열/세로 배치를 고르는 용도). 요소가 나중에 생겨도(로딩 뒤) 붙도록 콜백 ref를 쓴다. */
function useElementWidth() {
  const [element, setElement] = useState<HTMLDivElement | null>(null)
  const [width, setWidth] = useState(0)
  useEffect(() => {
    if (!element) return
    const observer = new ResizeObserver((entries) => setWidth(Math.floor(entries[0].contentRect.width)))
    observer.observe(element)
    return () => observer.disconnect()
  }, [element])
  return { ref: setElement, width }
}

/** 카드(컨테이너) 폭이 이 값 이상이면 왼쪽 그림·오른쪽 글자의 2열, 미만이면 그림 위·글자 아래. */
const WIDE_MIN_PX = 520
/** 세로 배치에서 부모가 높이를 정해 주지 않을 때(기록 탭 등) 그림 영역의 고정 높이. */
const STACK_PICTURE_AREA_PX = 240

/** 시작 전 안내에 쓰는 연한 윤곽 그림(8×8). */
const GHOST_CELLS = Array.from({ length: 64 }, (_, index) => ({ x: index % 8, y: Math.floor(index / 8), color: 0 }))

/**
 * 홈·기록 탭 활동 지도 카드의 "도트" 보기. 시작 흐름은 화면 상태(없음·진행 중·완성)와 상관없이 여기서 열어 둔다 —
 * 다른 기기에서 먼저 시작해 화면 상태가 바뀌어도(409 안내를 보여 주는 동안) 모달이 사라지지 않게 하려는 것이다.
 * fit이면 부모가 정해 준 높이를 그대로 쓰고(홈), 아니면 그림 영역이 고정 높이를 쓴다.
 */
export function DotPanel({ fit = false }: { fit?: boolean }) {
  const [starting, setStarting] = useState(false)
  return (
    <>
      <DotPanelBody onStart={() => setStarting(true)} fit={fit} />
      {starting ? <DotStartFlow onClose={() => setStarting(false)} /> : null}
    </>
  )
}

/** 진행 중인 그림, 방금 완성한 그림, 아직 없는 상태를 차분하게 보여 준다. */
function DotPanelBody({ onStart, fit }: { onStart: () => void; fit: boolean }) {
  const query = useDotsCurrent()
  const albumQuery = useDotAlbum()
  const hasAlbum = (albumQuery.data ?? []).length > 0
  const { ref, width } = useElementWidth()
  const wide = width >= WIDE_MIN_PX

  if (query.isLoading) {
    return <Loading />
  }

  if (query.isError || !query.data) {
    return (
      <div className="flex flex-col items-start gap-2">
        <p className="t-body text-ink-2">도트 그림을 지금은 불러오지 못했어요. 기록은 그대로 안전해요.</p>
        <button
          type="button"
          onClick={() => query.refetch()}
          className="t-sub inline-flex min-h-11 items-center rounded-full px-2 -mx-2 font-medium text-base-ink hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]"
        >
          다시 불러오기
        </button>
      </div>
    )
  }

  const { goal, justCompleted } = query.data
  const shown = goal ?? justCompleted

  // 그림 영역: 부모가 높이를 정해 주면(fit) 그 높이를 다 쓰고, 아니면 고정 높이. 2열이면 왼쪽 칸 전체 높이.
  const pictureArea = (picture: ReactNode) => (
    <div
      className="min-h-0 min-w-0"
      style={wide || fit ? { flex: '1 1 0' } : { height: STACK_PICTURE_AREA_PX, flex: 'none' }}
    >
      {picture}
    </div>
  )
  const layout = (picture: ReactNode, text: ReactNode) => (
    <div
      ref={ref}
      className={[
        'flex min-h-0 w-full flex-1',
        wide ? 'flex-row items-stretch gap-6' : 'flex-col gap-3',
        fit ? 'h-full' : '',
      ].join(' ')}
    >
      {pictureArea(picture)}
      <div className={wide ? 'flex min-w-0 flex-1 flex-col justify-center' : 'flex-none'}>{text}</div>
    </div>
  )

  if (shown) {
    const done = goal == null
    const remaining = shown.goalDays - shown.filledCount
    return layout(
      <DotGridView key={shown.id} goal={shown} fit />,
      <div className={['relative flex flex-col gap-1', wide ? 'items-start text-left' : 'items-center text-center'].join(' ')}>
        {goal ? (
          <div className="absolute right-0 top-0 -mt-1">
            <StopMenu goal={goal} />
          </div>
        ) : null}
        <p className="t-key pr-11 text-ink">{done ? `${shown.title} 그림이 완성됐어요` : shown.title}</p>
        <p className="t-stat text-ink">{done ? `${shown.goalDays}칸을 모두 채웠어요` : `${shown.goalDays}칸 중 ${shown.filledCount}칸`}</p>
        {!done ? (
          <p className="t-body text-ink-2">{shown.filledCount === 0 ? '기록한 날마다 한 칸씩 채워져요' : `완성까지 ${remaining}칸 남았어요`}</p>
        ) : null}
        <CollectionLink />
      </div>,
    )
  }

  return layout(
    <DotPicture width={8} height={8} palette={[]} cells={GHOST_CELLS} filledCount={0} ariaLabel="아직 시작하지 않은 도트 그림" fit ghost />,
    <div className={['flex flex-col gap-3', wide ? 'items-start' : 'items-center text-center'].join(' ')}>
      <div className="flex flex-col gap-1">
        <p className="t-key text-ink">도트 그림으로 기록을 쌓아 볼까요?</p>
        <p className="t-body text-ink-2">기록한 날마다 그림이 한 칸씩 채워져요. 쉬는 날은 그대로 두면 돼요.</p>
      </div>
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
        <Button type="button" onClick={onStart}>
          시작하기
        </Button>
        {hasAlbum ? <CollectionLink /> : null}
      </div>
    </div>,
  )
}
