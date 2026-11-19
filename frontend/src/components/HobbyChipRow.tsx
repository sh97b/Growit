import { useCallback, useEffect, useRef, useState } from 'react'
import { ChevronLeftIcon, ChevronRightIcon } from './icons'
import { pickClass } from '../lib/recordForm'

type Hobby = { id: number; name: string }

const SCROLL_RATIO = 0.7

function prefersReducedMotion(): boolean {
  return typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/**
 * 분야를 시간 선택처럼 가로 칩 한 줄로 고른다. 칩이 폭을 넘으면 웹에서는 양 끝에 반투명 화살표가 나타나
 * (올리거나 누르면 한 번에 약 70%씩 부드럽게 이동, 끝에 닿으면 숨김), 모바일은 손가락으로 밀어 넘긴다.
 * 선택된 칩이 보이도록 자동으로 맞춘다.
 */
export function HobbyChipRow({
  hobbies,
  selectedId,
  onSelect,
  addOpen,
  onToggleAdd,
}: {
  hobbies: Hobby[]
  selectedId: number
  onSelect: (id: number) => void
  addOpen: boolean
  onToggleAdd: () => void
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

  // 선택된 칩이 보이는 위치로 옮긴다(페이지 자체는 움직이지 않도록 칩 줄 안에서만 스크롤한다).
  useEffect(() => {
    const el = scrollerRef.current
    const chip = el?.querySelector<HTMLElement>('[data-selected="true"]')
    if (!el || !chip) return
    const left = chip.offsetLeft
    const right = left + chip.offsetWidth
    if (left < el.scrollLeft) el.scrollTo({ left: Math.max(0, left - 8), behavior: 'auto' })
    else if (right > el.scrollLeft + el.clientWidth) el.scrollTo({ left: right - el.clientWidth + 8, behavior: 'auto' })
    updateArrows()
  }, [selectedId, hobbies.length, updateArrows])

  function scrollByPage(direction: 1 | -1) {
    const el = scrollerRef.current
    if (!el) return
    el.scrollBy({ left: direction * el.clientWidth * SCROLL_RATIO, behavior: prefersReducedMotion() ? 'auto' : 'smooth' })
  }

  function handleHover(direction: 1 | -1) {
    const now = Date.now()
    if (now - lastHoverScrollRef.current < 600) return
    lastHoverScrollRef.current = now
    scrollByPage(direction)
  }

  const arrowClass =
    'absolute top-1/2 z-10 hidden h-9 w-9 -translate-y-1/2 items-center justify-center rounded-full bg-white/80 text-ink shadow-sm backdrop-blur-sm lg:inline-flex'

  return (
    <div className="relative">
      <div
        ref={scrollerRef}
        onScroll={updateArrows}
        role="group"
        aria-label="분야"
        className="flex gap-2 overflow-x-auto scroll-smooth pb-1 [scrollbar-width:none] motion-reduce:scroll-auto [&::-webkit-scrollbar]:hidden"
      >
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
        <button
          type="button"
          aria-expanded={addOpen}
          onClick={onToggleAdd}
          className="inline-flex min-h-11 flex-none items-center justify-center rounded-lg border border-dashed border-line-2 bg-white px-4 text-sm font-medium whitespace-nowrap text-ink-2"
        >
          + 분야 추가
        </button>
      </div>
      {canLeft ? (
        <button
          type="button"
          aria-label="이전 분야 보기"
          onClick={() => scrollByPage(-1)}
          onMouseEnter={() => handleHover(-1)}
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
          onMouseEnter={() => handleHover(1)}
          className={`${arrowClass} right-0`}
        >
          <ChevronRightIcon width={18} height={18} />
        </button>
      ) : null}
    </div>
  )
}
