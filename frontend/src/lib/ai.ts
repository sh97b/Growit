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

export type CompareLockReason = 'NOT_ENOUGH_RECORDS' | 'NO_PAST_RECORD'

/** 근거 기록 한 건(날짜·내용 일부는 서버가 저장된 기록에서 채운다). */
export type CompareEvidence = { id: number; recordDate: string; content: string }
export type CompareInsight = { point: string; because: string; evidence: CompareEvidence[] }
export type CompareIdea = { idea: string; evidence: CompareEvidence[] }

export type CompareResult = {
  locked: boolean
  lockReason: CompareLockReason | null
  requiredCount: number | null
  currentCount: number | null
  oldestRecordDate: string | null
  latestRecordDate: string | null
  id: number | null
  pastDate: string | null
  currentDate: string | null
  createdAt: string | null
  reused: boolean
  baseDate: string | null
  anchoredToLatest: boolean
  pastIsOldest: boolean
  interpretation: string | null
  pastRecord: CompareRecordCard | null
  currentRecord: CompareRecordCard | null
  metrics: CompareMetrics | null
  feedback: 'HELPFUL' | 'NOT_HELPFUL' | null
  /** 2이면 headline·good·gap·next가 있고, 없으면(예전 결과) interpretation 한 문단만 있다. */
  schemaVersion?: number | null
  headline?: string | null
  good?: CompareInsight[] | null
  gap?: CompareInsight[] | null
  next?: CompareIdea[] | null
}

/** 구조화된 결과의 항목 수(good+gap+next). */
export function countCompareItems(result: CompareResult): number {
  return (result.good?.length ?? 0) + (result.gap?.length ?? 0) + (result.next?.length ?? 0)
}

/** 구조화된 변화 해석인지. */
export function isStructuredCompare(result: CompareResult): boolean {
  return result.schemaVersion === 2 && !!result.headline && (result.good?.length ?? 0) > 0
}

/** 변화 탭 기본 데이터(AI 없이 즉시): 이전·최근 기록과 숫자. 해석(CompareResult)과 같은 기록 선정·같은 값이다. */
export type CompareBasis = {
  locked: boolean
  lockReason: CompareLockReason | null
  requiredCount: number | null
  currentCount: number | null
  oldestRecordDate: string | null
  latestRecordDate: string | null
  baseDate: string | null
  anchoredToLatest: boolean
  pastIsOldest: boolean
  pastDate: string | null
  currentDate: string | null
  previous: CompareRecordCard | null
  latest: CompareRecordCard | null
  numbers: CompareMetrics | null
}

export function fetchCompareBasis(payload: { hobbyId: number; compareTo: CompareTo }) {
  return apiRequest<CompareBasis>(`/api/compare/basis?hobbyId=${payload.hobbyId}&compareTo=${payload.compareTo}`)
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

/** 앞으로의 방향 후보 하나와 "이렇게 시작해 볼 수 있어요" 제안(steps). */
export type CoachDirectionPlan = { direction: string; evidence: CoachEvidenceItem[]; steps: string[] }

/** 서버가 기록에서 직접 센 숫자(AI가 만든 값이 아니다). */
export type CoachStats = {
  recordCount: number
  recordedDays: number
  totalMinutes: number
  feelings: { label: string; count: number }[]
}

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
  isNew: boolean
  /** 2면 새 구성(돌아보기·질문·방향 후보), 없으면 예전 모양(다음 작은 행동 포함)이다. */
  schemaVersion?: number | null
  flow?: CoachTextItem[] | null
  resolvedDifficulties?: CoachTextItem[] | null
  questions?: CoachTextItem[] | null
  directions?: CoachTextItem[] | null
  stats?: CoachStats | null
  /** 방향 후보에 시작 제안이 붙은 형태. 이전 새 구성 결과에는 없다(그땐 directions 문장만 있다). */
  directionPlans?: CoachDirectionPlan[] | null
}

export function fetchCoach(payload: { hobbyId?: number; period: CoachPeriod }) {
  return apiRequest<CoachResult>('/api/ai/coach', { method: 'POST', body: payload })
}

export function fetchCoachResult(id: number) {
  return apiRequest<CoachResult>(`/api/ai/results/${id}`)
}

export type CoachListItem = {
  id: number
  createdAt: string
  startDate: string | null
  endDate: string | null
  hobbyId: number | null
  hobbyName: string | null
  summary: string | null
  nextActionTitle: string | null
  isAuto: boolean
  isNew: boolean
  feedback: 'HELPFUL' | 'NOT_HELPFUL' | null
  reaction: 'TRIED' | 'REST_TODAY' | 'TOO_HARD' | null
}

export type CoachListResult = { items: CoachListItem[]; totalCount: number }

export function fetchCoachList(params: {
  page?: number
  size?: number
  hobbyId?: number
  q?: string
  from?: string
  to?: string
} = {}) {
  const query = new URLSearchParams({ type: 'COACH' })
  if (params.page != null) query.set('page', String(params.page))
  if (params.size != null) query.set('size', String(params.size))
  if (params.hobbyId != null) query.set('hobbyId', String(params.hobbyId))
  if (params.q) query.set('q', params.q)
  if (params.from) query.set('from', params.from)
  if (params.to) query.set('to', params.to)
  return apiRequest<CoachListResult>(`/api/ai/results?${query.toString()}`)
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

export type CoachAutoResponse = {
  status: 'GENERATED' | 'EXISTS' | 'NOT_ELIGIBLE' | 'DISABLED' | 'LIMIT'
  result: CoachResult | null
  newRecordsSinceLast: number
  requiredCount: number
  hasAnyCoach: boolean
}

/**
 * 홈·회고 탭이 각자의 빈도로 호출한다(서버가 조건에 맞을 때만 생성, 멱등 — 같은 입력이면 다시 만들지
 * 않고 EXISTS로 그 결과를 돌려준다). 응답 모양이 조회용 CoachResult와 달라 CoachAutoResponse로 받는다.
 */
export function generateAutoCoach() {
  return apiRequest<CoachAutoResponse>('/api/ai/coach/auto', { method: 'POST' })
}

/** 자동 회고를 처음 열어봤다고 표시한다(isNew를 끈다). */
export function markCoachViewed(id: number) {
  return apiRequest<CoachResult>(`/api/ai/results/${id}/viewed`, { method: 'PUT' })
}

export type AiViewResult = {
  locked: boolean
  requiredWeeks: number | null
  currentWeeks: number | null
  empty: boolean
  id: number | null
  startDate: string | null
  endDate: string | null
  createdAt: string | null
  reused: boolean
  text: string | null
  editedText: string | null
  displayText: string | null
  evidence: CompareRecordCard[]
  feedback: 'CONFIRMED' | 'DISPUTED' | 'EDITED' | null
  remainingText: string | null
  remainingEvidence: CompareRecordCard[]
}

export function fetchWeeklyOneLiner(payload: { weekStart: string; hobbyId?: number }) {
  return apiRequest<AiViewResult>('/api/ai/weekly', { method: 'POST', body: payload })
}

export function fetchMetricsInterpretation(payload: { hobbyId?: number; from: string; to: string }) {
  return apiRequest<AiViewResult>('/api/ai/metrics', { method: 'POST', body: payload })
}

export function submitViewFeedback(id: number, value: 'CONFIRMED' | 'DISPUTED') {
  return apiRequest<AiViewResult>(`/api/ai/results/${id}/feedback`, { method: 'PUT', body: { value } })
}

export function editViewText(id: number, text: string) {
  return apiRequest<AiViewResult>(`/api/ai/results/${id}/text`, { method: 'PATCH', body: { text } })
}
