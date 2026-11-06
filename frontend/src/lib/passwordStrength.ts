export type PasswordStrength = 'weak' | 'medium' | 'strong'

// 서버 블록 목록(500개 이상)을 그대로 옮기지 않고, 가장 흔한 것만 가볍게 거른다(막지는 않고 힌트만 준다).
const COMMON_PASSWORDS = new Set([
  'password',
  '12345678',
  '123456789',
  'qwerty123',
  'password1',
  'password123',
  'iloveyou',
  'admin123',
  '1q2w3e4r',
  'qwertyui',
  'asdfghjk',
  'letmein123',
  'dkssud123',
])

function isSingleCharacterRepeated(password: string): boolean {
  return [...password].every((ch) => ch === password[0])
}

function isSimpleSequence(password: string): boolean {
  if (password.length < 2) return false
  let ascending = true
  let descending = true
  for (let i = 1; i < password.length; i++) {
    const diff = password.charCodeAt(i) - password.charCodeAt(i - 1)
    if (diff !== 1) ascending = false
    if (diff !== -1) descending = false
  }
  return ascending || descending
}

/** 서버 정책(PasswordPolicy)을 가볍게 흉내만 낸다. 막지는 않고 강도 표시에만 쓴다. */
export function estimatePasswordStrength(password: string, email: string): PasswordStrength {
  if (password.length < 8) return 'weak'

  const lower = password.toLowerCase()
  const localPart = (email.split('@')[0] ?? '').toLowerCase()
  if (COMMON_PASSWORDS.has(lower)) return 'weak'
  if (localPart.length >= 3 && lower.includes(localPart)) return 'weak'
  if (isSingleCharacterRepeated(password)) return 'weak'
  if (isSimpleSequence(lower)) return 'weak'

  let variety = 0
  if (/[a-z]/.test(password)) variety++
  if (/[A-Z]/.test(password)) variety++
  if (/[0-9]/.test(password)) variety++
  if (/[^a-zA-Z0-9]/.test(password)) variety++

  if (password.length >= 12 && variety >= 3) return 'strong'
  if (password.length >= 10 && variety >= 2) return 'medium'
  if (variety >= 2) return 'medium'
  return 'weak'
}

export const PASSWORD_STRENGTH_LABEL: Record<PasswordStrength, string> = {
  weak: '약함',
  medium: '보통',
  strong: '강함',
}
