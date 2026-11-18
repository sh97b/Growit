import { apiRequest } from './api'

export type FeelingDirection = 'GOOD' | 'NEUTRAL' | 'HARD'

export type CustomFeeling = { id: number; label: string; direction: FeelingDirection }

export const DIRECTION_OPTIONS: { value: FeelingDirection; label: string }[] = [
  { value: 'GOOD', label: '좋아요' },
  { value: 'NEUTRAL', label: '보통이에요' },
  { value: 'HARD', label: '힘들어요' },
]

export const CUSTOM_FEELING_MAX_LENGTH = 8
export const CUSTOM_FEELING_MAX_COUNT = 12
const ALLOWED = /^[가-힣ㄱ-ㅎㅏ-ㅣA-Za-z0-9 ]+$/

/** 서버와 같은 기준으로 먼저 걸러서 불필요한 요청을 줄인다(최종 검증은 서버가 한다). 문제가 없으면 null. */
export function validateCustomFeelingLabel(raw: string): string | null {
  const label = raw.trim()
  if (label === '') return '감정 이름을 입력해 주세요'
  if (label.length > CUSTOM_FEELING_MAX_LENGTH) return `감정 이름은 ${CUSTOM_FEELING_MAX_LENGTH}자 이하로 입력해 주세요`
  if (!ALLOWED.test(label)) return '감정 이름에는 한글, 영문, 숫자, 공백만 쓸 수 있어요'
  return null
}

export function fetchCustomFeelings() {
  return apiRequest<CustomFeeling[]>('/api/feelings')
}

export function createCustomFeeling(label: string, direction: FeelingDirection) {
  return apiRequest<CustomFeeling>('/api/feelings', { method: 'POST', body: { label: label.trim(), direction } })
}

export function deleteCustomFeeling(id: number) {
  return apiRequest<void>(`/api/feelings/${id}`, { method: 'DELETE' })
}
