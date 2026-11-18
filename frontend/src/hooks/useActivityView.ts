import { useCallback, useSyncExternalStore } from 'react'
import { type ActivityView, readActivityView, writeActivityView } from '../lib/dots'

/*
 * 홈의 활동 지도 카드는 데스크톱 사이드와 모바일 본문에 하나씩, 두 번 그려진다(한쪽은 CSS로 숨김).
 * 두 카드가 같은 선택을 보도록 모듈 안에 값을 하나 두고 구독한다.
 */
let current: ActivityView | null = null
const listeners = new Set<() => void>()

function getSnapshot(): ActivityView {
  if (current == null) current = readActivityView()
  return current
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export function useActivityView(): [ActivityView, (view: ActivityView) => void] {
  const view = useSyncExternalStore(subscribe, getSnapshot, () => 'grass' as ActivityView)
  const setView = useCallback((next: ActivityView) => {
    current = next
    writeActivityView(next)
    listeners.forEach((listener) => listener())
  }, [])
  return [view, setView]
}
