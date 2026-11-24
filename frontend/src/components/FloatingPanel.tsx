import { useCallback, useEffect, useRef, useState, type KeyboardEvent, type PointerEvent, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { XIcon } from './icons'

type Position = { x: number; y: number }

const WIDTH = 440
const HEIGHT = 340
const MARGIN = 8
const KEY_STEP = 16

function readPosition(storageKey: string): Position | null {
  try {
    const raw = window.localStorage.getItem(`${storageKey}.pos`)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<Position>
    return typeof parsed.x === 'number' && typeof parsed.y === 'number' ? { x: parsed.x, y: parsed.y } : null
  } catch {
    return null
  }
}

function writePosition(storageKey: string, position: Position) {
  try {
    window.localStorage.setItem(`${storageKey}.pos`, JSON.stringify(position))
  } catch {
    /* 저장하지 못해도 화면은 그대로 동작한다. */
  }
}

/**
 * 비모달 "떠 있는 작은 패널". 오버레이가 없어 뒤 화면을 그대로 쓸 수 있고, 제목 줄을 잡아 옮긴다(포인터 이벤트, 뷰포트 밖으로 못 나감).
 * 제목 줄에 포커스를 두고 방향키로도 옮길 수 있다. Esc·X로 닫는다. 위치는 localStorage에 기억한다.
 * 모바일(mobile)은 드래그 대신 아래에서 올라오는 시트(높이 60%)로 보인다.
 */
export function FloatingPanel({
  title,
  storageKey,
  onClose,
  mobile = false,
  children,
}: {
  title: string
  storageKey: string
  onClose: () => void
  mobile?: boolean
  children: ReactNode
}) {
  const [position, setPosition] = useState<Position | null>(() => (mobile ? null : readPosition(storageKey)))
  const dragRef = useRef<{ dx: number; dy: number } | null>(null)
  const panelRef = useRef<HTMLDivElement>(null)

  const clamp = useCallback((value: Position): Position => {
    const width = Math.min(WIDTH, window.innerWidth - MARGIN * 2)
    const height = Math.min(HEIGHT, window.innerHeight - MARGIN * 2)
    return {
      x: Math.min(Math.max(MARGIN, value.x), Math.max(MARGIN, window.innerWidth - width - MARGIN)),
      y: Math.min(Math.max(MARGIN, value.y), Math.max(MARGIN, window.innerHeight - height - MARGIN)),
    }
  }, [])

  // 처음 열 때 위치가 없으면 오른쪽 위(헤더 아래)에 둔다. 창 크기가 바뀌면 화면 안으로 다시 맞춘다.
  const effective = mobile ? null : clamp(position ?? { x: window.innerWidth - WIDTH - 32, y: 132 })
  const [, forceRender] = useState(0)
  useEffect(() => {
    if (mobile) return
    const onResize = () => forceRender((count) => count + 1)
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [mobile])

  useEffect(() => {
    function onKeyDown(event: globalThis.KeyboardEvent) {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  function commit(next: Position) {
    const clamped = clamp(next)
    setPosition(clamped)
    writePosition(storageKey, clamped)
  }

  function onPointerDown(event: PointerEvent<HTMLDivElement>) {
    if (mobile || !effective || (event.target as HTMLElement).closest('button')) return
    event.currentTarget.setPointerCapture(event.pointerId)
    dragRef.current = { dx: event.clientX - effective.x, dy: event.clientY - effective.y }
  }
  function onPointerMove(event: PointerEvent<HTMLDivElement>) {
    const drag = dragRef.current
    if (!drag) return
    setPosition(clamp({ x: event.clientX - drag.dx, y: event.clientY - drag.dy }))
  }
  function onPointerUp(event: PointerEvent<HTMLDivElement>) {
    if (!dragRef.current) return
    dragRef.current = null
    event.currentTarget.releasePointerCapture(event.pointerId)
    if (position) writePosition(storageKey, position)
  }
  function onHandleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (mobile || !effective) return
    const delta: Record<string, Position> = {
      ArrowLeft: { x: -KEY_STEP, y: 0 },
      ArrowRight: { x: KEY_STEP, y: 0 },
      ArrowUp: { x: 0, y: -KEY_STEP },
      ArrowDown: { x: 0, y: KEY_STEP },
    }
    const step = delta[event.key]
    if (!step) return
    event.preventDefault()
    commit({ x: effective.x + step.x, y: effective.y + step.y })
  }

  const header = (
    <div
      role={mobile ? undefined : 'group'}
      tabIndex={mobile ? undefined : 0}
      aria-label={mobile ? undefined : `${title} 패널 위치 옮기기. 방향키로 옮겨요`}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onKeyDown={onHandleKeyDown}
      className={[
        'flex flex-none items-center justify-between gap-2 border-b border-line px-4 py-2.5',
        mobile ? '' : 'cursor-grab touch-none select-none active:cursor-grabbing',
      ].join(' ')}
    >
      <h2 className="text-card-title">{title}</h2>
      <button
        type="button"
        onClick={onClose}
        aria-label={`${title} 닫기`}
        className="inline-flex h-9 w-9 flex-none items-center justify-center rounded-full text-ink-2 hover:bg-surface"
      >
        <XIcon width={18} height={18} />
      </button>
    </div>
  )

  const panel = (
    <div
      ref={panelRef}
      role="dialog"
      aria-modal="false"
      aria-label={title}
      style={
        mobile || !effective
          ? undefined
          : { left: effective.x, top: effective.y, width: Math.min(WIDTH, window.innerWidth - MARGIN * 2), height: Math.min(HEIGHT, window.innerHeight - MARGIN * 2) }
      }
      className={[
        'fixed z-40 flex flex-col overflow-hidden border border-line-2 bg-paper shadow-xl',
        mobile ? 'inset-x-0 bottom-0 h-[60dvh] rounded-t-2xl' : 'rounded-2xl',
      ].join(' ')}
    >
      {header}
      <div className={['flex min-h-0 flex-1 flex-col gap-2 p-4', mobile ? 'overflow-y-auto' : 'overflow-hidden'].join(' ')}>{children}</div>
    </div>
  )

  return createPortal(panel, document.body)
}
