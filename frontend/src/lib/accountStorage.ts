/** 계정을 삭제할 때만 쓴다. growit_로 시작하는 모든 저장 값(토큰, 임시 글, 온보딩 상태 등)을 지운다. */
export function clearAllGrowitStorage(): void {
  for (const storage of [localStorage, sessionStorage]) {
    try {
      const keysToRemove: string[] = []
      for (let i = 0; i < storage.length; i++) {
        const key = storage.key(i)
        if (key?.startsWith('growit')) {
          keysToRemove.push(key)
        }
      }
      keysToRemove.forEach((key) => storage.removeItem(key))
    } catch {
      // 저장소를 쓸 수 없어도 계정 삭제 자체는 계속 진행한다
    }
  }
}
