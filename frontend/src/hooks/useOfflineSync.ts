import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useCurrentUser } from './useCurrentUser'
import { flushOfflineQueue, type OfflineQueueItem } from '../lib/offlineQueue'

const DROPPED_KEY = 'growit_offline_dropped'

function recordDropped(items: OfflineQueueItem[]): void {
  try {
    const existing = JSON.parse(sessionStorage.getItem(DROPPED_KEY) ?? '[]') as string[]
    sessionStorage.setItem(DROPPED_KEY, JSON.stringify([...existing, ...items.map((item) => item.content)]))
  } catch {
    // 안내를 못 남겨도 동기화 자체는 이미 끝난 뒤라 무시한다.
  }
}

/** 연결이 돌아오면(online 이벤트·화면 재방문) 오프라인 큐를 순서대로 올리고 목록·홈 캐시를 갱신한다. */
export function useOfflineSync() {
  const { data: currentUser } = useCurrentUser()
  const queryClient = useQueryClient()

  useEffect(() => {
    if (!currentUser) return

    function sync() {
      flushOfflineQueue(currentUser!.id, (dropped) => {
        recordDropped(dropped)
        queryClient.invalidateQueries({ queryKey: ['records'] })
      }).then(() => {
        queryClient.invalidateQueries({ queryKey: ['records'] })
        queryClient.invalidateQueries({ queryKey: ['stats'] })
        queryClient.invalidateQueries({ queryKey: ['hobbies'] })
      })
    }

    sync()
    window.addEventListener('online', sync)
    return () => window.removeEventListener('online', sync)
  }, [currentUser, queryClient])
}

export function takeDroppedOfflineMessages(): string[] {
  try {
    const existing = JSON.parse(sessionStorage.getItem(DROPPED_KEY) ?? '[]') as string[]
    if (existing.length > 0) {
      sessionStorage.removeItem(DROPPED_KEY)
    }
    return existing
  } catch {
    return []
  }
}
