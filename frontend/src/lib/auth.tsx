import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { apiRequest, clearToken, getToken, setToken } from './api'
import { clearOnboardingPending, markOnboardingPending } from './onboarding'

type LoginResponse = { accessToken: string }

type AuthContextValue = {
  token: string | null
  isAuthenticated: boolean
  login: (email: string, password: string) => Promise<void>
  signup: (email: string, password: string) => Promise<void>
  logout: () => void
  setSessionToken: (token: string) => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setTokenState] = useState<string | null>(() => getToken())

  const login = useCallback(async (email: string, password: string) => {
    const result = await apiRequest<LoginResponse>('/api/auth/login', {
      method: 'POST',
      body: { email, password },
      auth: false,
    })
    setToken(result.accessToken)
    setTokenState(result.accessToken)
  }, [])

  const signup = useCallback(async (email: string, password: string) => {
    await apiRequest<void>('/api/auth/signup', {
      method: 'POST',
      body: { email, password },
      auth: false,
    })
    const result = await apiRequest<LoginResponse>('/api/auth/login', {
      method: 'POST',
      body: { email, password },
      auth: false,
    })
    // 로그인 상태가 되는 순간 GuestRoute가 홈으로 보내버리기 전에, 먼저 온보딩 대기 표시를 남긴다.
    markOnboardingPending()
    setToken(result.accessToken)
    setTokenState(result.accessToken)
  }, [])

  const logout = useCallback(() => {
    clearToken()
    clearOnboardingPending()
    setTokenState(null)
  }, [])

  // 비밀번호를 바꾸면 서버가 새 토큰을 바로 돌려준다 - 다시 로그인하지 않고 그 토큰으로 이어서 쓴다.
  const setSessionToken = useCallback((newToken: string) => {
    setToken(newToken)
    setTokenState(newToken)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ token, isAuthenticated: token !== null, login, signup, logout, setSessionToken }),
    [token, login, signup, logout, setSessionToken],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth는 AuthProvider 안에서만 쓸 수 있어요')
  }
  return context
}
