import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import { isOnboardingPending } from '../lib/onboarding'

/** 로그인 상태이면서 가입 직후 남긴 "온보딩 대기" 표시가 있을 때만 연다. 그 외엔 홈으로 보낸다. */
export function OnboardingRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth()
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }
  if (!isOnboardingPending()) {
    return <Navigate to="/" replace />
  }
  return children
}
