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
  /**
   * S1 A단계: 측정 전(그리고 컨테이너 높이가 0 등 비정상일 때)에 쓸 기본 개수. 기본값은
   * min과 max 사이의 6(데스크톱에서 합리적인 값) — 측정이 끝나기 전에도 빈 화면 대신
   * 이 개수로 즉시 목록을 그린다("개수가 확정되기 전에는 그리지 않는다"는 이전 규칙 폐기).
   */
  defaultSize?: number
}

/**
 * 디자인에 없는 추가 훅(F3): 목록 영역의 실제 높이를 ResizeObserver로 재서 한 페이지에 들어갈
 * 개수를 계산한다. 고정 개수 대신 화면 높이에 맞춰 늘고 줄어, 해상도가 달라도 페이지 스크롤이
 * 생기지 않게 한다. 루트 글자 크기(F3 1단계의 clamp)가 화면마다 달라지므로 rem→px 환산은
 * 매번 document.documentElement의 실제 computed font-size를 읽어서 한다.
 *
 * S1 A단계: 컨테이너가 flex 높이 체인이 끊겨 클라이언트 높이를 아직 못 받은 순간(마운트 직후,
 * 레이아웃이 완전히 자리잡기 전)에는 measure()가 height<=0을 계속 받을 수 있다. 예전에는 이때
 * ready를 영영 true로 만들지 않아 호출부가 "자리표시만 계속 보여주는" 무한 로딩에 빠졌다
 * (활동 분야 관리에서 재현됨). 이제는 비정상 측정값도 "측정은 끝났다"로 간주해 즉시 ready를
 * true로 만들고, 그 순간의 pageSize(기본값 또는 직전 값)를 그대로 쓴다 — 다음 리사이즈에서
 * 정상 측정되면 자연스럽게 보정된다.
 */
export function useFitPageSize({
  itemHeightRem,
  columns = 1,
  gapRem = 0,
  min = 2,
  max = 12,
  debounceMs = 150,
  defaultSize,
}: UseFitPageSizeOptions): { containerRef: React.RefObject<HTMLDivElement | null>; pageSize: number; ready: boolean } {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const initialSize = Math.min(max, Math.max(min, defaultSize ?? 6))
  const [pageSize, setPageSize] = useState(initialSize)
  // ready는 더 이상 렌더를 막는 용도가 아니라(S1 A), 참고용 신호로만 남겨 둔다 — 첫 측정
  // 시도(성공이든 비정상이든)가 끝났는지를 나타낸다.
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
      if (height <= 0 || itemHeightPx <= 0) {
        // 비정상 측정값: 기본값을 그대로 쓰고, "측정 시도는 끝났다"로 표시해 무한 대기를 막는다.
        setReady(true)
        return
      }
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
