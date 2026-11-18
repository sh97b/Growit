import { useLayoutEffect, useRef, useState } from 'react'

/**
 * 홈 사이드 열처럼 "우선순위가 낮은 블록부터 숨겨서 열 안에 다 들어가게" 하는 측정 기반 훅(P1 5단계).
 * 블록 배열의 앞쪽(index가 낮은 쪽)이 우선순위가 높다 — 넘치면 마지막(가장 낮은 우선순위) 블록부터
 * 하나씩 실제로 숨기고(display:none) 다시 재는 식으로, 열 안에 다 들어갈 때까지 반복한다. 열이
 * 넓어지면(리사이즈) 매번 전부 보여준 뒤 다시 측정해 필요한 만큼만 숨겨 자동으로 복원된다.
 */
export function useOverflowHidePriority(
  itemCount: number,
  /** 아무리 좁아도 앞에서부터 이 개수만큼은 숨기지 않는다(첫 블록까지 사라져 열이 텅 비는 것을 막는다). */
  minVisible = 0,
): {
  containerRef: React.RefObject<HTMLDivElement | null>
  itemRefs: React.RefObject<(HTMLDivElement | null)[]>
  visibleCount: number
  /** 열의 실제 높이(px). 도트 그림처럼 남는 높이에 맞춰 줄여야 하는 블록이 쓴다. 아직 못 쟀으면 0. */
  containerHeight: number
} {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const itemRefs = useRef<(HTMLDivElement | null)[]>([])
  const [visibleCount, setVisibleCount] = useState(itemCount)
  const [containerHeight, setContainerHeight] = useState(0)

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
      // 열이 아직 배치되지 않아 높이가 0이면(첫 그리기 직후 등) 넘치는지 판단할 수 없으니 전부 보여 둔다.
      // 높이가 정해지면 ResizeObserver가 다시 잰다.
      if (el.clientHeight === 0) {
        setVisibleCount((prev) => (prev === shown ? prev : shown))
        return
      }
      while (shown > minVisible && el.scrollHeight > el.clientHeight + 1) {
        shown -= 1
        const node = itemRefs.current[shown]
        if (node) node.style.display = 'none'
      }
      setVisibleCount((prev) => (prev === shown ? prev : shown))
      setContainerHeight((prev) => (prev === el.clientHeight ? prev : el.clientHeight))
    }

    // 열 크기만이 아니라 각 블록의 크기도 지켜본다. 도트 카드처럼 데이터가 늦게 와서 블록 높이가 나중에
    // 바뀌는 경우에도 다시 재서, 열 밖으로 잘리지 않고 아래 블록부터 숨겨지게 한다(measure가 끝난 상태는
    // 직전과 같아서 서로를 계속 다시 부르지 않는다).
    const observer = new ResizeObserver(measure)
    observer.observe(container)
    for (let i = 0; i < itemCount; i += 1) {
      const node = itemRefs.current[i]
      if (node) observer.observe(node)
    }
    measure()

    return () => observer.disconnect()
  }, [itemCount, minVisible])

  return { containerRef, itemRefs, visibleCount, containerHeight }
}
