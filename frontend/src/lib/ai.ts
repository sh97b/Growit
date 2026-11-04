import { apiRequest } from './api'

export function deleteAiData() {
  return apiRequest<void>('/api/ai/data', { method: 'DELETE' })
}

export type AiUsage = { enabled: boolean; limit: number; used: number; remaining: number }

export function fetchAiUsage() {
  return apiRequest<AiUsage>('/api/ai/usage')
}

export type CompareTo = '1W' | '2W' | '1M' | '3M'

export type CompareRecordCard = {
  id: number
  recordDate: string
  content: string
  tags: string[]
  feeling: string | null
}

export type CompareMetrics = {
  recordedDays: { past: number; current: number }
  totalMinutes: { past: number; current: number }
  topTags: { tag: string; count: number }[]
}

export type CompareResult = {
  locked: boolean
  requiredCount: number | null
  currentCount: number | null
  id: number | null
  pastDate: string | null
  currentDate: string | null
  createdAt: string | null
  reused: boolean
  interpretation: string | null
  pastRecord: CompareRecordCard | null
  currentRecord: CompareRecordCard | null
  metrics: CompareMetrics | null
  feedback: 'HELPFUL' | 'NOT_HELPFUL' | null
}

export function fetchCompare(payload: { hobbyId: number; compareTo: CompareTo }) {
  return apiRequest<CompareResult>('/api/ai/compare', { method: 'POST', body: payload })
}

export function submitAiResultFeedback<T>(id: number, value: 'HELPFUL' | 'NOT_HELPFUL') {
  return apiRequest<T>(`/api/ai/results/${id}/feedback`, { method: 'PUT', body: { value } })
}

export type CoachPeriod = 'THIS_WEEK' | '2W' | '1M'

export type CoachEvidenceItem = { recordId: number; recordDate: string; content: string }

export type CoachTextItem = { text: string; evidence: CoachEvidenceItem[] }

export type CoachAction = { title: string; minutes: number; reason: string; evidence: CoachEvidenceItem[] }

export type CoachResult = {
  locked: boolean
  requiredCount: number | null
  currentCount: number | null
  id: number | null
  mode: 'NORMAL' | 'COMEBACK' | null
  startDate: string | null
  endDate: string | null
  createdAt: string | null
  reused: boolean
  periodSummary: string | null
  changes: CoachTextItem[]
  repeatedDifficulties: CoachTextItem[]
  nextAction: CoachAction | null
  comebackAction: CoachAction | null
  recordsRead: number | null
  feedback: 'HELPFUL' | 'NOT_HELPFUL' | null
  reaction: 'TRIED' | 'REST_TODAY' | 'TOO_HARD' | null
  cardState: 'ACTIVE' | 'CELEBRATE' | 'RESTING' | 'EXPIRED' | null
}

export function fetchCoach(payload: { hobbyId?: number; period: CoachPeriod }) {
  return apiRequest<CoachResult>('/api/ai/coach', { method: 'POST', body: payload })
}

export function fetchCoachResult(id: number) {
  return apiRequest<CoachResult>(`/api/ai/results/${id}`)
}

export function fetchCoachHistory(params: { page?: number; size?: number } = {}) {
  const query = new URLSearchParams({ type: 'COACH' })
  if (params.page != null) query.set('page', String(params.page))
  if (params.size != null) query.set('size', String(params.size))
  return apiRequest<CoachResult[]>(`/api/ai/results?${query.toString()}`)
}

export function submitCoachReaction(id: number, value: 'TRIED' | 'REST_TODAY' | 'TOO_HARD') {
  return apiRequest<CoachResult>(`/api/ai/results/${id}/reaction`, { method: 'PUT', body: { value } })
}

/** "너무 어려워요"를 실제로 반영한다. 결과당 1회만 되고, 성공하면 reaction이 비워져 다시 반응할 수 있다. */
export function simplifyCoachAction(id: number) {
  return apiRequest<CoachResult>(`/api/ai/results/${id}/simplify`, { method: 'POST' })
}

/** 홈에서는 AI를 새로 부르지 않고 이미 저장된 최신 회고만 읽는다. 없으면(204) undefined. */
export function fetchLatestCoach() {
  return apiRequest<CoachResult | undefined>('/api/ai/results/latest?type=COACH')
}
