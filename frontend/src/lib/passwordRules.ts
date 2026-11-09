export type PasswordRuleKey = 'length' | 'letter' | 'digit' | 'special'

export const PASSWORD_SPECIAL_CHARS = '! @ # $ % ^ & * ( ) - _ = + . , ?'

const ALLOWED_CHARS = /^[A-Za-z0-9!@#$%^&*()\-_=+.,?]*$/
const HAS_LETTER = /[A-Za-z]/
const HAS_DIGIT = /[0-9]/
const HAS_SPECIAL = /[!@#$%^&*()\-_=+.,?]/

export const PASSWORD_RULES: { key: PasswordRuleKey; label: string; test: (password: string) => boolean }[] = [
  { key: 'length', label: '8자 이상 20자 이하', test: (p) => p.length >= 8 && p.length <= 20 },
  { key: 'letter', label: '영문 포함', test: (p) => HAS_LETTER.test(p) },
  { key: 'digit', label: '숫자 포함', test: (p) => HAS_DIGIT.test(p) },
  { key: 'special', label: `특수문자 포함(${PASSWORD_SPECIAL_CHARS})`, test: (p) => HAS_SPECIAL.test(p) },
]

/** 허용되지 않는 문자(한글·공백·이모지 등)가 섞였는지 - 체크 목록과 별도로 바로 안내한다. */
export function hasDisallowedCharacters(password: string): boolean {
  return password.length > 0 && !ALLOWED_CHARS.test(password)
}
