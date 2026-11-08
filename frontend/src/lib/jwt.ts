/** 서버 서명은 검증하지 않는다 - 저장소 정리 때 "누구 것을 남길지" 고르는 용도일 뿐, 인증 판단에는 쓰지 않는다. */
export function decodeTokenUserId(token: string | null): number | null {
  if (!token) return null
  try {
    const payload = token.split('.')[1]
    if (!payload) return null
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/')
    const json = JSON.parse(atob(normalized)) as { sub?: string }
    const id = Number(json.sub)
    return Number.isFinite(id) ? id : null
  } catch {
    return null
  }
}
