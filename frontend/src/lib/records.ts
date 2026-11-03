import { apiRequest } from './api'

export type Feeling = 'PROUD' | 'STUCK' | 'RELIEVED' | 'NEUTRAL'

export type AiStatus = 'NONE' | 'PENDING' | 'DONE' | 'FAILED' | 'SKIPPED'
export type ChangeFeedback = 'HELPFUL' | 'NOT_HELPFUL'
export type TagsFeedbackChoice = 'CONFIRMED' | 'DISPUTED'
export type TagsFeedback = 'CONFIRMED' | 'DISPUTED' | 'EDITED' | null

export type RecordRelated = { id: number; recordDate: string; content: string }

export type RecordAnalysis = {
  title: string | null
  tags: string[]
  relatedRecords: RecordRelated[]
  change: { text: string; evidence: RecordRelated } | null
  tagsFeedback: TagsFeedback
  changeFeedback: ChangeFeedback | null
}

export type RecordAnalysisSummary = { title: string | null; tags: string[] }

export type RecordNeighbor = { id: number; recordDate: string }

export type RecordDetail = {
  id: number
  hobbyId: number
  recordDate: string
  content: string
  minutes: number | null
  difficulty: string | null
  feeling: Feeling | null
  isMilestone: boolean
  aiStatus: AiStatus
  analysis: RecordAnalysis | null
  previousRecord: RecordNeighbor | null
  nextRecord: RecordNeighbor | null
}

export type RecordSummary = {
  id: number
  hobbyId: number
  recordDate: string
  content: string
  minutes: number | null
  difficulty: string | null
  feeling: Feeling | null
  isMilestone: boolean
  aiStatus: AiStatus
  analysis: RecordAnalysisSummary | null
}

export type RecordListResult = { items: RecordSummary[]; totalCount: number }

export function fetchRecords(params: { hobbyId?: number; page?: number; size?: number } = {}) {
  const query = new URLSearchParams()
  if (params.hobbyId != null) query.set('hobbyId', String(params.hobbyId))
  if (params.page != null) query.set('page', String(params.page))
  if (params.size != null) query.set('size', String(params.size))
  const qs = query.toString()
  return apiRequest<RecordListResult>(`/api/records${qs ? `?${qs}` : ''}`)
}

export function fetchRecord(id: number) {
  return apiRequest<RecordDetail>(`/api/records/${id}`)
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
  return apiRequest<RecordDetail>('/api/records', {
    method: 'POST',
    body: payload,
  })
}

export type RecordUpdatePayload = {
  hobbyId?: number
  recordDate?: string
  content?: string
  minutes?: number | null
  difficulty?: string | null
  feeling?: Feeling | null
}

export function updateRecord(id: number, payload: RecordUpdatePayload) {
  return apiRequest<RecordDetail>(`/api/records/${id}`, {
    method: 'PATCH',
    body: payload,
  })
}

export function deleteRecord(id: number) {
  return apiRequest<void>(`/api/records/${id}`, { method: 'DELETE' })
}

export function reanalyzeRecord(id: number) {
  return apiRequest<RecordDetail>(`/api/records/${id}/analysis`, { method: 'POST' })
}

export type AnalysisFeedbackPayload = { change?: ChangeFeedback; tags?: TagsFeedbackChoice }

export function submitAnalysisFeedback(id: number, payload: AnalysisFeedbackPayload) {
  return apiRequest<RecordDetail>(`/api/records/${id}/analysis/feedback`, {
    method: 'PUT',
    body: payload,
  })
}

export function editAnalysisTags(id: number, tags: string[]) {
  return apiRequest<RecordDetail>(`/api/records/${id}/analysis/tags`, {
    method: 'PATCH',
    body: { tags },
  })
}
