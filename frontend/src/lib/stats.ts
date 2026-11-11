import { apiRequest } from './api'

export type StatsSummary = {
  recordedDays: number
  totalMinutes: number
  hobbyCount: number
  lastRecordedDate: string | null
  daysSinceLastRecord: number | null
  todayRecorded: boolean
  today: string
  firstRecordedDate: string | null
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

export type WeekFeelingCounts = { proud: number; stuck: number; relieved: number; neutral: number }

export type WeekHobbySummary = { hobbyId: number; recordedDays: number }

export type WeekTagCount = { tag: string; count: number }

export type WeekSummary = {
  weekStart: string
  recordedDays: number
  recordCount: number
  totalMinutes: number
  hobbies: WeekHobbySummary[]
  feelings: WeekFeelingCounts
  tags: WeekTagCount[]
}

export function fetchWeeks(params: { from: string; to: string; hobbyId?: number }) {
  const query = new URLSearchParams({ from: params.from, to: params.to })
  if (params.hobbyId != null) query.set('hobbyId', String(params.hobbyId))
  return apiRequest<WeekSummary[]>(`/api/stats/weeks?${query.toString()}`)
}

export type WeekDayRecord = {
  id: number
  hobbyId: number
  hobbyName: string
  content: string
  minutes: number | null
  feeling: string | null
}

export type WeekDay = { date: string; records: WeekDayRecord[] }

export type WeekDetail = {
  weekStart: string
  weekEnd: string
  recordedDays: number
  totalMinutes: number
  hobbyCount: number
  days: WeekDay[]
  previousWeekStart: string
  nextWeekStart: string | null
}

export function fetchWeekDetail(weekStart: string, hobbyId?: number) {
  const query = hobbyId != null ? `?hobbyId=${hobbyId}` : ''
  return apiRequest<WeekDetail>(`/api/stats/weeks/${weekStart}${query}`)
}
