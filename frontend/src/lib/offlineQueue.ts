import { ApiError } from './api'
import { createRecord, type RecordCreatePayload } from './records'

export type OfflineQueueItem = RecordCreatePayload & {
  tempId: string
  hobbyName: string
  queuedAt: string
}

function queueKey(userId: number): string {
  return `growit_offline_${userId}`
}

export function loadOfflineQueue(userId: number): OfflineQueueItem[] {
  try {
    const raw = localStorage.getItem(queueKey(userId))
    if (!raw) return []
    return JSON.parse(raw) as OfflineQueueItem[]
  } catch {
    return []
  }
}

function saveQueue(userId: number, items: OfflineQueueItem[]): void {
  try {
    if (items.length === 0) {
      localStorage.removeItem(queueKey(userId))
    } else {
      localStorage.setItem(queueKey(userId), JSON.stringify(items))
    }
  } catch {
    // 저장소를 쓸 수 없어도 서비스는 계속 동작해야 한다
  }
}

/** 클라이언트 임시 id로 중복 저장을 막는다(큐에 이미 있으면 다시 넣지 않는다). */
export function queueOfflineRecord(userId: number, payload: RecordCreatePayload, hobbyName: string): OfflineQueueItem {
  const item: OfflineQueueItem = {
    ...payload,
    tempId: `offline-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    hobbyName,
    queuedAt: new Date().toISOString(),
  }
  const items = loadOfflineQueue(userId)
  saveQueue(userId, [...items, item])
  return item
}

function removeFromQueue(userId: number, tempId: string): void {
  saveQueue(userId, loadOfflineQueue(userId).filter((item) => item.tempId !== tempId))
}

const flushingUsers = new Set<number>()

/**
 * 큐에 쌓인 기록을 순서대로 서버에 올린다. 같은 사용자에 대해 동시에 두 번 돌지 않게 막는다
 * (online 이벤트와 화면 재방문이 거의 동시에 일어날 수 있어서).
 * 성공하거나 400(서버가 거절)이면 큐에서 뺀다. 그 밖의 오류(여전히 오프라인 등)는 큐에 남겨 다음 기회에 다시 시도한다.
 */
export async function flushOfflineQueue(
  userId: number,
  onSynced: (dropped: OfflineQueueItem[]) => void,
): Promise<void> {
  if (flushingUsers.has(userId)) return
  const items = loadOfflineQueue(userId)
  if (items.length === 0) return

  flushingUsers.add(userId)
  const dropped: OfflineQueueItem[] = []
  try {
    for (const item of items) {
      const { tempId, hobbyName, queuedAt, ...payload } = item
      void hobbyName
      void queuedAt
      try {
        await createRecord(payload)
        removeFromQueue(userId, tempId)
      } catch (error) {
        if (error instanceof ApiError && error.status >= 400 && error.status < 500) {
          removeFromQueue(userId, tempId)
          dropped.push(item)
        } else {
          // 여전히 연결이 안 되는 등 — 이 항목과 그 뒤는 다음 기회로 남겨 둔다.
          break
        }
      }
    }
  } finally {
    flushingUsers.delete(userId)
  }
  if (dropped.length > 0) {
    onSynced(dropped)
  }
}
