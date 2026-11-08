import type { QueryClient } from '@tanstack/react-query'

let registeredQueryClient: QueryClient | null = null

/** api.ts처럼 React 바깥(401 처리)에서도 쿼리 캐시를 지울 수 있도록 앱 시작 시 한 번 등록해 둔다. */
export function registerQueryClient(client: QueryClient): void {
  registeredQueryClient = client
}

const DRAFT_PREFIX = 'growit_draft_'

/**
 * 로그인·가입·로그아웃·계정 삭제·401 자동 로그아웃처럼 "사용자가 바뀌는" 모든 순간에 부른다.
 * 비밀번호 변경(토큰만 교체, 사용자는 그대로)은 부르지 않는다.
 * keepUserId가 있으면 그 사용자의 기록 임시 글(growit_draft_{id})만 남기고, 없으면 전부 지운다.
 * sessionStorage의 growit_ 키(온보딩 임시 상태 등)는 항상 전부 지운다 - 다음 사용자와 공유할 이유가 없다.
 */
export function resetSessionState(keepUserId: number | null): void {
  if (registeredQueryClient) {
    registeredQueryClient.cancelQueries().catch(() => {
      // 진행 중이던 요청은 취소만 하면 되고, 실패해도 무시한다
    })
    registeredQueryClient.clear()
  }

  try {
    const keysToRemove: string[] = []
    for (let i = 0; i < sessionStorage.length; i++) {
      const key = sessionStorage.key(i)
      if (key?.startsWith('growit')) {
        keysToRemove.push(key)
      }
    }
    keysToRemove.forEach((key) => sessionStorage.removeItem(key))
  } catch {
    // 저장소를 쓸 수 없어도 계속 진행한다
  }

  try {
    const keepKey = keepUserId != null ? `${DRAFT_PREFIX}${keepUserId}` : null
    const keysToRemove: string[] = []
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i)
      if (key?.startsWith(DRAFT_PREFIX) && key !== keepKey) {
        keysToRemove.push(key)
      }
    }
    keysToRemove.forEach((key) => localStorage.removeItem(key))
  } catch {
    // 저장소를 쓸 수 없어도 계속 진행한다
  }
}
