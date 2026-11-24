import { useCallback, useEffect, useRef, useState, type PointerEvent } from 'react'
import { ChevronLeftIcon, ChevronRightIcon } from './icons'
import { pickClass } from '../lib/recordForm'

type Hobby = { id: number; name: string }

const SCROLL_RATIO = 0.7
/** 첫·끝 칩이 가장자리에서 잘리지 않게 두는 좌우 여백(px). 칩 줄의 패딩과 같다. */
const EDGE_GAP = 8

function prefersReducedMotion(): boolean {
  return typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/**
 * 분야를 시간 선택처럼 가로 칩 한 줄로 고른다. 칩이 폭을 넘으면 웹에서는 양 끝에 반투명 화살표가 나타나
 * (올리거나 누르면 한 번에 약 70%씩 부드럽게 이동, 끝에 닿으면 숨김). 모바일도 같은 화살표(터치 영역 36px)를 쓰고 손가락으로 밀어 넘길 수도 있다.
 * 선택된 칩이 보이도록 자동으로 맞춘다.
 */
export function HobbyChipRow({
  hobbies,
  selectedId,
  onSelect,
  allLabel,
  onSelectAll,
  addOpen,
  onToggleAdd,
}: {
  hobbies: Hobby[]
  /** allLabel이 있으면 null이 "전체" 칩 선택 상태다. */
  selectedId: number | null
  onSelect: (id: number) => void
  /** 맨 앞에 "전체" 같은 칩을 하나 더 둔다(분야로 거르지 않는 화면). */
  allLabel?: string
  onSelectAll?: () => void
  /** 둘 다 없으면 "+ 분야 추가" 칩을 그리지 않는다(분야만 고르는 화면). */
  addOpen?: boolean
  onToggleAdd?: () => void
}) {
  const scrollerRef = useRef<HTMLDivElement>(null)
  const lastHoverScrollRef = useRef(0)
  const [canLeft, setCanLeft] = useState(false)
  const [canRight, setCanRight] = useState(false)

  const updateArrows = useCallback(() => {
    const el = scrollerRef.current
    if (!el) return
    setCanLeft(el.scrollLeft > 1)
    setCanRight(el.scrollLeft + el.clientWidth < el.scrollWidth - 1)
  }, [])

  useEffect(() => {
    const el = scrollerRef.current
    if (!el) return
    updateArrows()
    const observer = new ResizeObserver(updateArrows)
    observer.observe(el)
    return () => observer.disconnect()
  }, [updateArrows, hobbies.length])

  // 스크롤 위치는 항상 scrollLeft만으로 [0, scrollWidth - clientWidth]에 묶어서 옮긴다(transform·음수 오프셋을 쓰지 않는다).
  const scrollToClamped = useCallback((target: number, smooth: boolean) => {
    const el = scrollerRef.current
    if (!el) return
    const max = Math.max(0, el.scrollWidth - el.clientWidth)
    el.scrollTo({ left: Math.min(max, Math.max(0, target)), behavior: smooth && !prefersReducedMotion() ? 'smooth' : 'auto' })
  }, [])

  // 선택된 칩이 보이는 위치로 옮긴다(페이지 자체는 움직이지 않도록 칩 줄 안에서만 스크롤한다).
  useEffect(() => {
    const el = scrollerRef.current
    const chip = el?.querySelector<HTMLElement>('[data-selected="true"]')
    if (!el || !chip) return
    const left = chip.getBoundingClientRect().left - el.getBoundingClientRect().left + el.scrollLeft
    const right = left + chip.offsetWidth
    if (left < el.scrollLeft) scrollToClamped(left - EDGE_GAP, false)
    else if (right > el.scrollLeft + el.clientWidth) scrollToClamped(right - el.clientWidth + EDGE_GAP, false)
    updateArrows()
  }, [selectedId, hobbies.length, updateArrows, scrollToClamped])

  function scrollByPage(direction: 1 | -1) {
    const el = scrollerRef.current
    if (!el) return
    scrollToClamped(el.scrollLeft + direction * el.clientWidth * SCROLL_RATIO, true)
  }

  function handleHover(direction: 1 | -1, event: PointerEvent<HTMLElement>) {
    // 터치 탭은 클릭으로 처리한다(올림 이벤트로 두 번 움직이지 않게 마우스일 때만).
    if (event.pointerType !== 'mouse') return
    const now = Date.now()
    if (now - lastHoverScrollRef.current < 600) return
    lastHoverScrollRef.current = now
    scrollByPage(direction)
  }

  const arrowClass =
    'absolute top-1/2 z-10 inline-flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-full bg-white/80 text-ink shadow-sm backdrop-blur-sm'

  return (
    <div className="relative w-full min-w-0 max-w-full">
      {/* overflow-x-auto 안에 width:max-content 칩 줄을 두고 왼쪽 시작 정렬만 쓴다(가운데 정렬은 넘친 쪽이 잘려 접근 불가). */}
      <div
        ref={scrollerRef}
        onScroll={updateArrows}
        role="group"
        aria-label="분야"
        className="w-full min-w-0 overflow-x-auto overflow-y-hidden pb-1 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
      >
        <div className="flex w-max min-w-full flex-nowrap items-center justify-start gap-2 px-2">
        {allLabel && onSelectAll ? (
          <button
            type="button"
            aria-pressed={selectedId === null}
            data-selected={selectedId === null}
            onClick={onSelectAll}
            className={pickClass(selectedId === null)}
          >
            {allLabel}
          </button>
        ) : null}
        {hobbies.map((hobby) => (
          <button
            key={hobby.id}
            type="button"
            aria-pressed={hobby.id === selectedId}
            data-selected={hobby.id === selectedId}
            onClick={() => onSelect(hobby.id)}
            className={pickClass(hobby.id === selectedId)}
          >
            {hobby.name}
          </button>
        ))}
        {onToggleAdd ? (
          <button
            type="button"
            aria-expanded={addOpen ?? false}
            onClick={onToggleAdd}
            className="inline-flex min-h-11 flex-none items-center justify-center rounded-lg border border-dashed border-line-2 bg-white px-4 t-sub font-medium whitespace-nowrap text-ink-2"
          >
            + 분야 추가
          </button>
        ) : null}
        </div>
      </div>
      {canLeft ? (
        <div aria-hidden="true" className="pointer-events-none absolute inset-y-0 left-0 z-[5] w-10 bg-gradient-to-r from-paper to-transparent" />
      ) : null}
      {canRight ? (
        <div aria-hidden="true" className="pointer-events-none absolute inset-y-0 right-0 z-[5] w-10 bg-gradient-to-l from-paper to-transparent" />
      ) : null}
      {canLeft ? (
        <button
          type="button"
          aria-label="이전 분야 보기"
          onClick={() => scrollByPage(-1)}
          onPointerEnter={(event) => handleHover(-1, event)}
          className={`${arrowClass} left-0`}
        >
          <ChevronLeftIcon width={18} height={18} />
        </button>
      ) : null}
      {canRight ? (
        <button
          type="button"
          aria-label="다음 분야 보기"
          onClick={() => scrollByPage(1)}
          onPointerEnter={(event) => handleHover(1, event)}
          className={`${arrowClass} right-0`}
        >
          <ChevronRightIcon width={18} height={18} />
        </button>
      ) : null}
    </div>
  )
}
