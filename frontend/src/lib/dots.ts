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

export type DotPresetInfo = {
  key: string
  label: string
  width: number
  height: number
  palette: string[]
  pixels: string[]
}

/** 시작하면 서버가 저장할 그림과 완전히 같은 칸 목록. 쓰기 없는 미리보기다. */
export type DotPreview = {
  key: string
  label: string
  goalDays: number
  width: number
  height: number
  palette: string[]
  cells: DotCell[]
}

export const MIN_GOAL_DAYS = 14
export const MAX_GOAL_DAYS = 200

export function fetchDotPresets() {
  return apiRequest<DotPresetInfo[]>('/api/dots/presets')
}

export function fetchDotPreview(key: string, goalDays: number) {
  return apiRequest<DotPreview>(`/api/dots/presets/${encodeURIComponent(key)}/preview?goalDays=${goalDays}`)
}

/** 기본 그림으로 시작하거나(presetKey), 사진에서 만든 격자(mode PHOTO)로 시작한다. 사진 이미지는 보내지 않는다. */
export type DotStartBody =
  | { presetKey: string; goalDays: number }
  | {
      mode: 'PHOTO'
      title: string
      goalDays: number
      width: number
      height: number
      palette: string[]
      cells: { x: number; y: number; c: number }[]
    }

export function startDotGoal(body: DotStartBody) {
  return apiRequest<DotGoal>('/api/dots/goals', { method: 'POST', body })
}

export function stopDotGoal(goalId: number) {
  return apiRequest<void>(`/api/dots/goals/${goalId}/stop`, { method: 'POST' })
}

/** 도감: 완성한 그림만, 최근 완성한 것이 먼저 온다. */
export function fetchDotAlbum() {
  return apiRequest<DotGoal[]>('/api/dots/goals')
}

export function fetchDotGoal(goalId: number) {
  return apiRequest<DotGoal>(`/api/dots/goals/${goalId}`)
}

export function markDotCompletionSeen(goalId: number) {
  return apiRequest<void>(`/api/dots/goals/${goalId}/completion-seen`, { method: 'POST' })
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
