export type OnboardingDraft = {
  userId: number
  step: number
  name: string
  selectedHobbies: string[]
  lockedHobbyNames: string[]
  goalDrafts: Record<number, string>
}

const KEY = 'growit_onboarding_draft'

export function loadOnboardingDraft(): OnboardingDraft | null {
  try {
    const raw = sessionStorage.getItem(KEY)
    if (!raw) return null
    return JSON.parse(raw) as OnboardingDraft
  } catch {
    return null
  }
}

export function saveOnboardingDraft(draft: OnboardingDraft): void {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(draft))
  } catch {
    // 저장소를 쓸 수 없어도 서비스는 계속 동작해야 한다
  }
}

export function clearOnboardingDraft(): void {
  try {
    sessionStorage.removeItem(KEY)
  } catch {
    // 저장소를 쓸 수 없어도 서비스는 계속 동작해야 한다
  }
}
