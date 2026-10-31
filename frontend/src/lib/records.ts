import { apiRequest } from './api'

export type Feeling = 'PROUD' | 'STUCK' | 'RELIEVED' | 'NEUTRAL'

export type RecordItem = {
  id: number
  hobbyId: number
  recordDate: string
  content: string
  minutes: number | null
  difficulty: string | null
  feeling: Feeling | null
  isMilestone: boolean
  analysis: unknown
}

export type RecordListResult = { items: RecordItem[]; totalCount: number }

export function fetchRecords(params: { hobbyId?: number; size?: number } = {}) {
  const query = new URLSearchParams()
  if (params.hobbyId != null) query.set('hobbyId', String(params.hobbyId))
  if (params.size != null) query.set('size', String(params.size))
  const qs = query.toString()
  return apiRequest<RecordListResult>(`/api/records${qs ? `?${qs}` : ''}`)
}

export type RecordCreatePayload = {
  hobbyId: number
  recordDate: string
  content: string
  minutes?: number
  difficulty?: string
  feeling?: Feeling
}

export function createRecord(payload: RecordCreatePayload) {
  return apiRequest<RecordItem>('/api/records', {
    method: 'POST',
    body: payload,
  })
}
