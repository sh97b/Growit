const ONBOARDING_KEY = 'growit_onboarding'

/** 새로고침해도 유지되지만 탭을 닫으면 사라지는 "온보딩 대기" 표시. 가입 직후부터 온보딩을 끝내거나 건너뛸 때까지만 켜 둔다. */
export function markOnboardingPending(): void {
  try {
    sessionStorage.setItem(ONBOARDING_KEY, '1')
  } catch {
    // 저장소를 쓸 수 없어도 서비스는 계속 동작해야 한다
  }
}

export function isOnboardingPending(): boolean {
  try {
    return sessionStorage.getItem(ONBOARDING_KEY) === '1'
  } catch {
    return false
  }
}

export function clearOnboardingPending(): void {
  try {
    sessionStorage.removeItem(ONBOARDING_KEY)
  } catch {
    // 저장소를 쓸 수 없어도 서비스는 계속 동작해야 한다
  }
}
