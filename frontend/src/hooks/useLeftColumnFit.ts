import { useLayoutEffect, useRef, useState } from 'react'

/** 단계: 0 전체 → 1 압축 → 2 한 줄 요약 카드 → 3 요약 + 인사말 여백 축소 → 4 활동 지도 카드 숨김. */
export const LEFT_FIT_LAST_LEVEL = 4

/**
 * 웹 홈 왼쪽 열의 높이 맞춤(최근 기록 3개는 항상 보이고, 그 아래 활동 지도 카드만 줄인다).
 * 추정값 없이 열과 블록의 실제 높이를 재서, 열 안이 넘칠 때만 한 단계씩 올린다. 열 높이(창 크기)가 바뀌면
 * 처음 단계부터 다시 재서 넓어지면 자동으로 복원된다.
 *
 * 요소는 콜백 ref(state)로 받는다. 홈은 데이터를 불러오는 동안 이 열이 아직 없어서, 처음 한 번만 도는 effect로는
 * 나중에 생기는 요소를 지켜볼 수 없기 때문이다. 줄이는 블록은 숨길 때도 DOM에 남겨 두고(크기만 0) 계속 지켜본다.
 */
export function useLeftColumnFit({ pictureCap }: { pictureCap: number }) {
  const [column, setColumn] = useState<HTMLDivElement | null>(null)
  const [fit, setFit] = useState<HTMLDivElement | null>(null)
  const recentRef = useRef<HTMLDivElement | null>(null)
  const activityRef = useRef<HTMLDivElement | null>(null)
  const [level, setLevel] = useState(0)
  const [metrics, setMetrics] = useState({ fit: 0, recent: 0, activity: 0, picture: 0, gap: 28 })

  useLayoutEffect(() => {
    if (!column || !fit) return
    let lastColumnHeight = -1

    function check() {
      if (!column || !fit) return
      const columnHeight = column.clientHeight
      const gap = parseFloat(getComputedStyle(fit).rowGap) || 28
      const recent = recentRef.current?.offsetHeight ?? 0
      const activity = activityRef.current?.offsetHeight ?? 0
      const picture = activityRef.current?.querySelector<HTMLElement>('[role="img"]')?.offsetHeight ?? 0
      const fitHeight = fit.clientHeight
      setMetrics((prev) =>
        prev.fit === fitHeight && prev.recent === recent && prev.activity === activity && prev.picture === picture && prev.gap === gap
          ? prev
          : { fit: fitHeight, recent, activity, picture, gap },
      )
      // 열 높이가 바뀌었으면 처음 단계부터 다시 시작한다. 바로 이어서 넘치는지도 재야, 처음 그린 직후처럼
      // 크기가 더 바뀌지 않는 경우에도 단계가 올라간다.
      if (columnHeight !== lastColumnHeight) {
        lastColumnHeight = columnHeight
        setLevel(0)
      }
      let needed = 0
      let visible = 0
      for (const child of Array.from(fit.children)) {
        const height = (child as HTMLElement).offsetHeight
        if (height > 0) {
          needed += height
          visible += 1
        }
      }
      needed += gap * Math.max(0, visible - 1)
      if (needed > fitHeight + 1) setLevel((current) => Math.min(LEFT_FIT_LAST_LEVEL, current + 1))
    }

    const observer = new ResizeObserver(check)
    observer.observe(column)
    observer.observe(fit)
    for (const child of Array.from(fit.children)) observer.observe(child)
    check()
    return () => observer.disconnect()
  }, [column, fit])

  // 압축 단계의 도트 그림 높이 = 열 높이 − 최근 기록 − (카드에서 그림을 뺀 실제 높이)
  const room = metrics.fit - (metrics.recent > 0 ? metrics.recent + metrics.gap : 0) - (metrics.activity - metrics.picture)
  const pictureMax = level >= 1 && metrics.fit > 0 ? Math.max(0, Math.min(pictureCap, room)) : pictureCap

  return { columnRef: setColumn, fitRef: setFit, recentRef, activityRef, level, pictureMax }
}
