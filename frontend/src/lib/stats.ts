import { apiRequest } from './api'

export type StatsSummary = {
  recordedDays: number
  totalMinutes: number
  hobbyCount: number
  lastRecordedDate: string | null
  daysSinceLastRecord: number | null
  todayRecorded: boolean
  today: string
}

export type ActivityMapEntry = {
  date: string
  hobbyId: number
  count: number
  minutes: number
}

export function fetchSummary() {
  return apiRequest<StatsSummary>('/api/stats/summary')
}

export function fetchActivityMap(params: { from: string; to: string; hobbyId?: number }) {
  const query = new URLSearchParams({ from: params.from, to: params.to })
  if (params.hobbyId != null) query.set('hobbyId', String(params.hobbyId))
  return apiRequest<ActivityMapEntry[]>(`/api/stats/activity-map?${query.toString()}`)
}
