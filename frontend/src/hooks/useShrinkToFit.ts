import { useLayoutEffect, useRef, useState } from 'react'

type Options = {
  /** 줄여 나갈 항목(최근 기록)의 가장 큰 개수. */
  max: number
  /** 이 열에서 줄일 항목 말고 꼭 남겨야 하는 블록(활동 지도 카드)의 그림을 뺀 높이(px). */
  overhead: number
  /** 그 블록 안의 그림이 최소한 이만큼은 있어야 한다(px). 0이면 그림이 없어도 된다. */
  needPicture: number
  /** 그림 높이의 위쪽 한도(px). */
  pictureCap: number
}

/**
 * 웹 홈 왼쪽 열처럼 "줄일 항목(최근 기록)을 3→2→1→0으로 줄여 가며 남길 블록(활동 지도)이 들어가게" 하는 훅.
 * 열 안이 실제로 넘치거나, 남는 높이로는 남길 블록의 그림이 최소 크기도 못 그릴 때 개수를 하나씩 줄인다.
 * 열 높이가 바뀌면(창 크기 변경) 처음 개수에서 다시 시작해 넓어졌을 때 자동으로 복원된다.
 * 그림 높이(pictureMax)는 열 높이에서 줄일 항목과 나머지 높이를 뺀 값이다.
 */
export function useShrinkToFit({ max, overhead, needPicture, pictureCap }: Options) {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const shrinkRef = useRef<HTMLDivElement | null>(null)
  const [count, setCount] = useState(max)
  const [metrics, setMetrics] = useState({ container: 0, shrink: 0, gap: 28 })
  const paramsRef = useRef({ max, overhead, needPicture })

  useLayoutEffect(() => {
    paramsRef.current = { max, overhead, needPicture }
  })

  useLayoutEffect(() => {
    const el = containerRef.current
    if (!el) return
    let lastHeight = -1

    function check() {
      const container = containerRef.current
      if (!container) return
      const height = container.clientHeight
      const shrinkHeight = shrinkRef.current?.offsetHeight ?? 0
      const gap = parseFloat(getComputedStyle(container).rowGap) || 28
      setMetrics((prev) =>
        prev.container === height && prev.shrink === shrinkHeight && prev.gap === gap ? prev : { container: height, shrink: shrinkHeight, gap },
      )
      const params = paramsRef.current
      if (height !== lastHeight) {
        lastHeight = height
        setCount(params.max)
        return
      }
      const overflow = container.scrollHeight > container.clientHeight + 1
      const room = height - shrinkHeight - (shrinkHeight > 0 ? gap : 0) - params.overhead
      const tight = params.needPicture > 0 && room < params.needPicture
      if (overflow || tight) setCount((current) => (current > 0 ? current - 1 : current))
    }

    const observer = new ResizeObserver(check)
    observer.observe(el)
    for (const child of Array.from(el.children)) observer.observe(child)
    check()
    return () => observer.disconnect()
  }, [])

  const room = metrics.container - metrics.shrink - (metrics.shrink > 0 ? metrics.gap : 0) - overhead
  const pictureMax = metrics.container > 0 ? Math.max(0, Math.min(pictureCap, room)) : undefined
  return { containerRef, shrinkRef, count: Math.min(count, max), pictureMax }
}
