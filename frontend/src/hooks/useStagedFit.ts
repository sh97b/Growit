import { useLayoutEffect, useRef, useState } from 'react'

type Options = {
  /** 줄여 나갈 최근 기록의 가장 큰 개수. */
  recentMax: number
  /** 활동 지도 카드를 압축하지 않았을 때 도트 그림 높이의 위쪽 한도(px). */
  pictureCap: number
}

/**
 * 웹 홈 왼쪽 열의 높이 맞춤. 추정값 없이 실제 높이를 재서, 열 안이 넘칠 때만 한 단계씩 줄여 나간다.
 *   단계 0~recentMax: 최근 기록을 3→2→1→0개로 줄인다.
 *   그다음: 활동 지도 카드 압축(잔디는 월 라벨·범례를 숨기고 칸을 줄이고, 도트는 남는 높이에 그림을 맞춘다).
 *   마지막: 인사말 날짜 줄과 여백을 줄인다.
 * 열 높이(창 크기)가 바뀌면 처음 단계부터 다시 재서, 넓어지면 자동으로 복원된다.
 *
 * 요소는 콜백 ref(state)로 받는다. 홈은 데이터를 불러오는 동안 이 열이 아직 없어서, 처음 한 번만 도는 effect로는
 * 나중에 생기는 요소를 지켜볼 수 없기 때문이다.
 */
export function useStagedFit({ recentMax, pictureCap }: Options) {
  const [column, setColumn] = useState<HTMLDivElement | null>(null)
  const [fit, setFit] = useState<HTMLDivElement | null>(null)
  const recentRef = useRef<HTMLDivElement | null>(null)
  const activityRef = useRef<HTMLDivElement | null>(null)
  const [level, setLevel] = useState(0)
  const [metrics, setMetrics] = useState({ fit: 0, recent: 0, activity: 0, picture: 0, gap: 28 })
  const lastLevel = recentMax + 2

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
      // 열 높이가 바뀌었으면 처음 단계부터 다시 시작한다. 여기서 끝내지 않고 바로 아래에서 넘치는지도 재야,
      // 처음 한 번 그린 직후에 크기가 더 바뀌지 않아도 단계가 올라간다.
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
      if (needed > fitHeight + 1) setLevel((current) => Math.min(lastLevel, current + 1))
    }

    const observer = new ResizeObserver(check)
    observer.observe(column)
    observer.observe(fit)
    for (const child of Array.from(fit.children)) observer.observe(child)
    check()
    return () => observer.disconnect()
  }, [column, fit, lastLevel])

  const activityCompact = level > recentMax
  const greetingCompact = level >= lastLevel
  const recentBlock = metrics.recent > 0 ? metrics.recent + metrics.gap : 0
  const room = metrics.fit - recentBlock - (metrics.activity - metrics.picture)
  const pictureMax = activityCompact && metrics.fit > 0 ? Math.max(0, Math.min(pictureCap, room)) : pictureCap

  return {
    columnRef: setColumn,
    fitRef: setFit,
    recentRef,
    activityRef,
    recentCount: Math.max(0, recentMax - level),
    activityCompact,
    greetingCompact,
    pictureMax,
  }
}
