import { lazy, Suspense, type ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../lib/auth'

const LandingPage = lazy(() => import('../pages/LandingPage'))

/**
 * "/"는 비로그인이면 랜딩(AppShell 밖, 지연 로딩)을, 그 밖의 보호된 주소는 비로그인이면 기존대로
 * /login으로 보낸다. isAuthenticated는 토큰을 동기적으로 읽어 두는 값이라(lib/auth.tsx) 깜박일
 * "확인 중" 상태 자체가 없다.
 */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth()
  const location = useLocation()
  if (!isAuthenticated) {
    if (location.pathname === '/') {
      return (
        <Suspense fallback={null}>
          <LandingPage />
        </Suspense>
      )
    }
    return <Navigate to="/login" replace />
  }
  return children
}
