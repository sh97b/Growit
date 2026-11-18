import { apiRequest } from './api'

export type DotCell = { x: number; y: number; color: number }

export type DotGoal = {
  id: number
  title: string
  sourceType: 'PRESET' | 'PHOTO'
  presetKey: string | null
  goalDays: number
  filledCount: number
  width: number
  height: number
  palette: string[]
  /** 채워지는 순서대로 내려온다. 앞의 filledCount칸이 칠해진 칸이다. */
  cells: DotCell[]
  startedOn: string
  state: 'ACTIVE' | 'COMPLETED' | 'STOPPED'
  completedOn: string | null
  completionSeen: boolean
}

export type DotCurrent = {
  goal: DotGoal | null
  justCompleted: DotGoal | null
}

/** 서버가 빠진 날을 채우는 쓰기가 있는 GET이다. 홈 화면이 열릴 때 한 번 불러 둔다. */
export function fetchDotsCurrent() {
  return apiRequest<DotCurrent>('/api/dots/current')
}

export type ActivityView = 'grass' | 'dot'

const VIEW_KEY = 'growit_home_activity_view'
const SEEN_KEY_PREFIX = 'growit_dot_seen_'

/** 사생활 보호 모드 등에서 localStorage가 막혀 있어도 앱이 깨지지 않게 모든 접근을 감싼다. */
export function readActivityView(): ActivityView {
  try {
    return window.localStorage.getItem(VIEW_KEY) === 'dot' ? 'dot' : 'grass'
  } catch {
    return 'grass'
  }
}

export function writeActivityView(view: ActivityView) {
  try {
    window.localStorage.setItem(VIEW_KEY, view)
  } catch {
    // 저장하지 못해도 이번 화면에서는 선택이 그대로 적용된다.
  }
}

/** 이 기기에서 마지막으로 도트 그림을 봤을 때의 칠한 칸 수. 그 뒤로 늘어난 칸만 살짝 반짝이게 하는 데 쓴다. */
export function readSeenFilledCount(goalId: number): number | null {
  try {
    const raw = window.localStorage.getItem(SEEN_KEY_PREFIX + goalId)
    if (raw == null) return null
    const value = Number(raw)
    return Number.isInteger(value) && value >= 0 ? value : null
  } catch {
    return null
  }
}

export function writeSeenFilledCount(goalId: number, filledCount: number) {
  try {
    window.localStorage.setItem(SEEN_KEY_PREFIX + goalId, String(filledCount))
  } catch {
    // 저장하지 못하면 다음에 반짝임이 없을 뿐이다.
  }
}
