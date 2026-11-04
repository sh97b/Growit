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

export function submitAiResultFeedback(id: number, value: 'HELPFUL' | 'NOT_HELPFUL') {
  return apiRequest<CompareResult>(`/api/ai/results/${id}/feedback`, { method: 'PUT', body: { value } })
}
