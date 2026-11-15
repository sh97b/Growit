import { useEffect, useLayoutEffect, useRef, useState } from 'react'

type UseFitPageSizeOptions = {
  /** 카드(또는 줄) 하나의 최소 높이(rem). 현재 루트 글자 크기 기준 px로 환산해 계산한다. */
  itemHeightRem: number
  /** 한 줄에 놓이는 열 수. */
  columns?: number
  /** 줄 사이 간격(rem). */
  gapRem?: number
  min?: number
  max?: number
  debounceMs?: number
}

/**
 * 디자인에 없는 추가 훅(F3): 목록 영역의 실제 높이를 ResizeObserver로 재서 한 페이지에 들어갈
 * 개수를 계산한다. 고정 개수 대신 화면 높이에 맞춰 늘고 줄어, 해상도가 달라도 페이지 스크롤이
 * 생기지 않게 한다. 루트 글자 크기(F3 1단계의 clamp)가 화면마다 달라지므로 rem→px 환산은
 * 매번 document.documentElement의 실제 computed font-size를 읽어서 한다.
 */
export function useFitPageSize({
  itemHeightRem,
  columns = 1,
  gapRem = 0,
  min = 2,
  max = 12,
  debounceMs = 150,
}: UseFitPageSizeOptions): { containerRef: React.RefObject<HTMLDivElement | null>; pageSize: number; ready: boolean } {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const [pageSize, setPageSize] = useState(max)
  // P1 2단계: 첫 측정이 끝나기 전에는 카드를 그리지 않는다(값이 확정되기 전에 max개를 그렸다가
  // 다시 줄어드는 깜박임을 막는다). useLayoutEffect로 페인트 전에 첫 측정을 동기로 끝낸다.
  const [ready, setReady] = useState(false)

  useLayoutEffect(() => {
    const el = containerRef.current
    if (!el) return

    let timeoutId: ReturnType<typeof setTimeout> | null = null

    function measure() {
      const el2 = containerRef.current
      if (!el2) return
      const rootFontSizePx = parseFloat(getComputedStyle(document.documentElement).fontSize) || 16
      const itemHeightPx = itemHeightRem * rootFontSizePx
      const gapPx = gapRem * rootFontSizePx
      const height = el2.clientHeight
      if (height <= 0 || itemHeightPx <= 0) return
      // min이 0인 호출(예: 홈의 "0~3개만" 측정)에서는 공간이 없으면 실제로 0까지 내려가야 하므로
      // 더 이상 최소 1행을 강제하지 않는다(그 외 호출은 이미 min으로 하한을 보장한다).
      const rows = Math.max(0, Math.floor((height + gapPx) / (itemHeightPx + gapPx)))
      const next = Math.min(max, Math.max(min, rows * columns))
      setPageSize((prev) => (prev === next ? prev : next))
      setReady(true)
    }

    const observer = new ResizeObserver(() => {
      if (timeoutId) clearTimeout(timeoutId)
      timeoutId = setTimeout(measure, debounceMs)
    })
    observer.observe(el)
    measure()

    return () => {
      observer.disconnect()
      if (timeoutId) clearTimeout(timeoutId)
    }
  }, [itemHeightRem, columns, gapRem, min, max, debounceMs])

  return { containerRef, pageSize, ready }
}

/**
 * pageSize가 바뀌어도(화면 크기 변화) 현재 보고 있던 첫 항목이 같은 페이지에 남도록 page를 보정한다.
 */
export function useStablePageOnResize(page: number, setPage: (page: number) => void, pageSize: number): void {
  const prevSizeRef = useRef(pageSize)

  useEffect(() => {
    if (prevSizeRef.current !== pageSize) {
      const firstItemIndex = page * prevSizeRef.current
      const nextPage = Math.floor(firstItemIndex / pageSize)
      prevSizeRef.current = pageSize
      if (nextPage !== page) setPage(nextPage)
    }
  }, [pageSize, page, setPage])
}
