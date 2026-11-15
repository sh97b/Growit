import { useLayoutEffect, useRef, useState } from 'react'

/**
 * 홈 사이드 열처럼 "우선순위가 낮은 블록부터 숨겨서 열 안에 다 들어가게" 하는 측정 기반 훅(P1 5단계).
 * 블록 배열의 앞쪽(index가 낮은 쪽)이 우선순위가 높다 — 넘치면 마지막(가장 낮은 우선순위) 블록부터
 * 하나씩 실제로 숨기고(display:none) 다시 재는 식으로, 열 안에 다 들어갈 때까지 반복한다. 열이
 * 넓어지면(리사이즈) 매번 전부 보여준 뒤 다시 측정해 필요한 만큼만 숨겨 자동으로 복원된다.
 */
export function useOverflowHidePriority(itemCount: number): {
  containerRef: React.RefObject<HTMLDivElement | null>
  itemRefs: React.RefObject<(HTMLDivElement | null)[]>
  visibleCount: number
} {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const itemRefs = useRef<(HTMLDivElement | null)[]>([])
  const [visibleCount, setVisibleCount] = useState(itemCount)

  useLayoutEffect(() => {
    const container = containerRef.current
    if (!container) return

    function measure() {
      const el = containerRef.current
      if (!el) return
      for (let i = 0; i < itemCount; i += 1) {
        const node = itemRefs.current[i]
        if (node) node.style.display = ''
      }
      let shown = itemCount
      while (shown > 0 && el.scrollHeight > el.clientHeight + 1) {
        shown -= 1
        const node = itemRefs.current[shown]
        if (node) node.style.display = 'none'
      }
      setVisibleCount((prev) => (prev === shown ? prev : shown))
    }

    const observer = new ResizeObserver(measure)
    observer.observe(container)
    measure()

    return () => observer.disconnect()
  }, [itemCount])

  return { containerRef, itemRefs, visibleCount }
}
