import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { apiRequest, clearToken, getToken, setToken } from './api'

type LoginResponse = { accessToken: string }

type AuthContextValue = {
  token: string | null
  isAuthenticated: boolean
  login: (username: string, password: string) => Promise<void>
  signup: (username: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setTokenState] = useState<string | null>(() => getToken())

  const login = useCallback(async (username: string, password: string) => {
    const result = await apiRequest<LoginResponse>('/api/auth/login', {
      method: 'POST',
      body: { username, password },
      auth: false,
    })
    setToken(result.accessToken)
    setTokenState(result.accessToken)
  }, [])

  const signup = useCallback(
    async (username: string, password: string) => {
      await apiRequest<void>('/api/auth/signup', {
        method: 'POST',
        body: { username, password },
        auth: false,
      })
      await login(username, password)
    },
    [login],
  )

  const logout = useCallback(() => {
    clearToken()
    setTokenState(null)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ token, isAuthenticated: token !== null, login, signup, logout }),
    [token, login, signup, logout],
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
