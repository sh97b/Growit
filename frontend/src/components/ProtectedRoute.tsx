import { lazy, Suspense, useEffect, useRef, type ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../lib/auth'
import { fetchHobbies } from '../lib/hobbies'
import { fetchCurrentUser } from '../lib/users'
import { fetchSummary } from '../lib/stats'

const LandingPage = lazy(() => import('../pages/LandingPage'))

/**
 * "/"는 비로그인이면 랜딩(AppShell 밖, 지연 로딩)을, 그 밖의 보호된 주소는 비로그인이면 기존대로
 * /login으로 보낸다. isAuthenticated는 토큰을 동기적으로 읽어 두는 값이라(lib/auth.tsx) 깜박일
 * "확인 중" 상태 자체가 없다.
 */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth()
  const location = useLocation()
  const queryClient = useQueryClient()
  const prefetchedRef = useRef(false)

  // S1 A-3단계: 로그인 상태가 확인되는 즉시(앱 시작 직후) 화면을 가리지 않고 공용 데이터를
  // 미리 불러와 둔다 — 첫 화면 전환에서 로딩 표시가 보이지 않게 한다. 세션당 한 번만.
  useEffect(() => {
    if (!isAuthenticated || prefetchedRef.current) return
    prefetchedRef.current = true
    queryClient.prefetchQuery({ queryKey: ['hobbies', false], queryFn: () => fetchHobbies(false) })
    queryClient.prefetchQuery({ queryKey: ['currentUser'], queryFn: fetchCurrentUser })
    queryClient.prefetchQuery({ queryKey: ['stats', 'summary'], queryFn: fetchSummary })
  }, [isAuthenticated, queryClient])

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
