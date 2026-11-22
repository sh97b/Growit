import { apiRequest } from './api'
import { addDays, todayLocalDate } from './date'

export type GrowthPeriod = 'MONTH' | 'THREE_MONTHS' | 'ALL'

export const PERIOD_OPTIONS: { value: GrowthPeriod; label: string }[] = [
  { value: 'MONTH', label: '이번 달' },
  { value: 'THREE_MONTHS', label: '최근 3개월' },
  { value: 'ALL', label: '전체(최근 400일)' },
]

export type GrowthDirection = 'GOOD' | 'NEUTRAL' | 'HARD'

export type Growth = {
  summary: { recordedDays: number; records: number; minutes: number; firstRecordDate: string | null }
  weekly: { start: string; days: number; minutes: number }[]
  byHobby: {
    hobbyId: number
    name: string
    records: number
    minutes: number
    firstDate: string | null
    archived: boolean
  }[]
  /** 월~일 순서, 기록 수. */
  weekday: number[]
  feelings: {
    buckets: { start: string; end: string; good: number; neutral: number; hard: number; none: number }[]
    byFeeling: { label: string; direction: GrowthDirection; builtIn: boolean; count: number }[]
    totals: { good: number; neutral: number; hard: number; none: number }
  }
  topics: { tag: string; count: number }[]
  milestones: { recordId: number; date: string; hobbyName: string; preview: string }[]
}

/** 기간을 날짜 범위로 바꾼다. 전체는 from을 보내지 않아 서버가 상한(400일) 안에서 정한다. */
export function periodRange(period: GrowthPeriod): { from?: string; to: string } {
  const to = todayLocalDate()
  if (period === 'MONTH') return { from: `${to.slice(0, 8)}01`, to }
  if (period === 'THREE_MONTHS') return { from: addDays(to, -90), to }
  return { to }
}

export function fetchGrowth(params: { period: GrowthPeriod; hobbyId: number | null }) {
  const { from, to } = periodRange(params.period)
  const query = new URLSearchParams({ to })
  if (from) query.set('from', from)
  if (params.hobbyId != null) query.set('hobbyId', String(params.hobbyId))
  return apiRequest<Growth>(`/api/stats/growth?${query.toString()}`)
}

const STORAGE_KEY = 'growit.growth.filter'

export type GrowthFilter = { hobbyId: number | null; period: GrowthPeriod }

export function readGrowthFilter(): GrowthFilter {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as Partial<GrowthFilter>
      const period = PERIOD_OPTIONS.some((option) => option.value === parsed.period) ? parsed.period! : 'THREE_MONTHS'
      const hobbyId = typeof parsed.hobbyId === 'number' ? parsed.hobbyId : null
      return { hobbyId, period }
    }
  } catch {
    // 저장소를 못 읽으면 기본값으로 시작한다.
  }
  return { hobbyId: null, period: 'THREE_MONTHS' }
}

export function writeGrowthFilter(filter: GrowthFilter) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(filter))
  } catch {
    // 기억만 포기한다.
  }
}
