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
  goalText: string
  targetDate: string | null
  steps: RoadmapStep[]
}

export type MoveDirection = 'UP' | 'DOWN'

/** 진행 중인 로드맵이 없으면 서버가 JSON null을 준다. */
export function fetchRoadmap(hobbyId: number) {
  return apiRequest<Roadmap | null>(`/api/hobbies/${hobbyId}/roadmap`)
}

export function createRoadmap(hobbyId: number, targetDate?: string | null) {
  return apiRequest<Roadmap>(`/api/hobbies/${hobbyId}/roadmap`, {
    method: 'POST',
    body: targetDate ? { targetDate } : {},
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
