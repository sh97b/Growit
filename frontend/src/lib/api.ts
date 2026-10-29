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
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE'
  body?: unknown
  auth?: boolean
}

const baseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

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

  if (response.status === 401) {
    clearToken()
    if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
      window.location.href = '/login'
    }
    throw new ApiError(401, 'UNAUTHORIZED', '로그인이 필요해요')
  }

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
    throw new ApiError(response.status, code, message)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}
