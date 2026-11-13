import { resetSessionState } from './sessionReset'

const TOKEN_KEY = 'growit_token'

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function setToken(token: string): void {
  try {
    localStorage.setItem(TOKEN_KEY, token)
  } catch {
    // 저장소를 쓸 수 없어도 서비스는 계속 동작해야 한다
  }
}

export function clearToken(): void {
  try {
    localStorage.removeItem(TOKEN_KEY)
  } catch {
    // 저장소를 쓸 수 없어도 서비스는 계속 동작해야 한다
  }
}

export class ApiError extends Error {
  code: string
  status: number

  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

type ApiRequestOptions = {
  method?: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE'
  body?: unknown
  auth?: boolean
}

const baseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

// 동시에 여러 요청이 TOKEN_INVALID 401을 받아도 확인 호출은 한 번만 하도록 진행 중인 확인을 공유한다.
let pendingTokenVerification: Promise<boolean> | null = null

/**
 * TOKEN_INVALID 401을 받아도 바로 로그아웃하지 않고, 같은 토큰으로 /api/users/me를 한 번 더 불러
 * 그것도 TOKEN_INVALID 401일 때만 "진짜로 무효"라고 판단한다. 푸시 전송처럼 다른 코드 경로에서 난
 * 오류가 어쩌다 401로 잘못 나가도 로그인 상태를 끊지 않기 위한 안전장치다(2026-11-13 사고).
 * apiRequest를 다시 타면 이 로직이 재귀하므로 원시 fetch로만 확인한다.
 */
function verifyTokenStillInvalid(): Promise<boolean> {
  if (!pendingTokenVerification) {
    pendingTokenVerification = (async () => {
      const token = getToken()
      if (!token) return true
      try {
        const response = await fetch(`${baseUrl}/api/users/me`, {
          headers: { Authorization: `Bearer ${token}` },
        })
        if (response.ok) return false
        if (response.status === 401) {
          const data = (await response.json().catch(() => null)) as { code?: string } | null
          return data?.code === 'TOKEN_INVALID'
        }
        return false
      } catch {
        return false
      }
    })().finally(() => {
      pendingTokenVerification = null
    })
  }
  return pendingTokenVerification
}

export async function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = true } = options
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }

  if (auth) {
    const token = getToken()
    if (token) {
      headers.Authorization = `Bearer ${token}`
    }
  }

  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })

  if (!response.ok) {
    let code = 'UNKNOWN_ERROR'
    let message = '잠시 후 다시 시도해 주세요'
    try {
      const data = (await response.json()) as { code?: string; message?: string }
      code = data.code ?? code
      message = data.message ?? message
    } catch {
      // 오류 응답이 JSON이 아니면 기본 안내를 쓴다
    }

    // 토큰이 없거나 위조·만료된 경우에만 세션을 정리한다. 비밀번호를 잘못 입력했거나(PASSWORD_MISMATCH)
    // 그 밖의 401/403/4xx는 로그인 상태를 그대로 유지하고 서버 message만 안내 상자로 보여준다.
    // TOKEN_INVALID여도 바로 끊지 않고 /api/users/me로 한 번 더 확인해, 다른 요청에서 어쩌다 잘못
    // 섞여 나온 401까지 로그아웃으로 이어지지 않게 한다.
    if (response.status === 401 && code === 'TOKEN_INVALID' && (await verifyTokenStillInvalid())) {
      resetSessionState(null)
      clearToken()
      if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }

    throw new ApiError(response.status, code, message)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}
