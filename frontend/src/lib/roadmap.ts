import { apiRequest } from './api'

export type RoadmapItem = {
  id: number
  position: number
  text: string
  done: boolean
  doneAt: string | null
  source: 'AI' | 'USER'
}

export type RoadmapStep = {
  id: number
  position: number
  title: string
  periodHint: string | null
  items: RoadmapItem[]
}

export type Roadmap = {
  id: number
  hobbyId: number
  /** ACTIVE 또는 ARCHIVED(보관된 로드맵은 읽기 전용). */
  state: 'ACTIVE' | 'ARCHIVED'
  goalText: string
  targetDate: string | null
  steps: RoadmapStep[]
  /** 항목을 방금 체크해 그 단계가 모두 끝났을 때만 있는 한 줄(그 응답에서만 쓴다). */
  completedStepMessage?: string | null
}

export type MoveDirection = 'UP' | 'DOWN'

/** 보관함 카드 한 장. archivedAt은 보관한 시각(서버가 마지막 수정 시각으로 준다). */
export type RoadmapSummary = {
  id: number
  goalText: string
  createdAt: string
  archivedAt: string
  stepCount: number
  itemCount: number
  doneCount: number
}

/** 최근 보관한 순으로 최대 20개. */
export function fetchArchivedRoadmaps(hobbyId: number) {
  return apiRequest<RoadmapSummary[]>(`/api/hobbies/${hobbyId}/roadmaps?state=ARCHIVED`)
}

/** 진행 중·보관된 로드맵 모두(본인 것만). */
export function fetchRoadmapById(id: number) {
  return apiRequest<Roadmap>(`/api/roadmaps/${id}`)
}

/** 같은 분야에 진행 중인 로드맵이 있으면 swap이 true일 때만 맞바꾸고, 아니면 409(ROADMAP_ACTIVE_EXISTS). */
export function restoreRoadmap(id: number, swap: boolean) {
  return apiRequest<Roadmap>(`/api/roadmaps/${id}/restore`, { method: 'POST', body: swap ? { swap: true } : {} })
}

/** 보관된 로드맵만 지울 수 있다(되돌릴 수 없다). */
export function deleteRoadmap(id: number) {
  return apiRequest<void>(`/api/roadmaps/${id}`, { method: 'DELETE' })
}

/** 진행 중인 로드맵이 없으면 서버가 JSON null을 준다. */
export function fetchRoadmap(hobbyId: number) {
  return apiRequest<Roadmap | null>(`/api/hobbies/${hobbyId}/roadmap`)
}

/** mode가 'MANUAL'이면 AI 없이 단계·항목이 없는 빈 로드맵을 만든다. */
export function createRoadmap(hobbyId: number, targetDate?: string | null, mode?: 'AI' | 'MANUAL') {
  return apiRequest<Roadmap>(`/api/hobbies/${hobbyId}/roadmap`, {
    method: 'POST',
    body: { ...(targetDate ? { targetDate } : {}), ...(mode === 'MANUAL' ? { mode } : {}) },
  })
}

export function updateRoadmapTarget(id: number, targetDate: string | null) {
  return apiRequest<Roadmap>(`/api/roadmaps/${id}`, { method: 'PATCH', body: { targetDate } })
}

export function archiveRoadmap(id: number) {
  return apiRequest<void>(`/api/roadmaps/${id}/archive`, { method: 'POST' })
}

export function addRoadmapStep(roadmapId: number, title: string) {
  return apiRequest<Roadmap>(`/api/roadmaps/${roadmapId}/steps`, { method: 'POST', body: { title } })
}

export function updateRoadmapStep(
  id: number,
  patch: { title?: string; periodHint?: string | null; move?: MoveDirection },
) {
  return apiRequest<Roadmap>(`/api/roadmap-steps/${id}`, { method: 'PATCH', body: patch })
}

export function deleteRoadmapStep(id: number) {
  return apiRequest<Roadmap>(`/api/roadmap-steps/${id}`, { method: 'DELETE' })
}

export function addRoadmapItem(stepId: number, text: string) {
  return apiRequest<Roadmap>(`/api/roadmap-steps/${stepId}/items`, { method: 'POST', body: { text } })
}

export function updateRoadmapItem(id: number, patch: { text?: string; move?: MoveDirection }) {
  return apiRequest<Roadmap>(`/api/roadmap-items/${id}`, { method: 'PATCH', body: patch })
}

export function deleteRoadmapItem(id: number) {
  return apiRequest<Roadmap>(`/api/roadmap-items/${id}`, { method: 'DELETE' })
}

export function setRoadmapItemDone(id: number, done: boolean) {
  return apiRequest<Roadmap>(`/api/roadmap-items/${id}/done`, { method: 'PUT', body: { done } })
}
