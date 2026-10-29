import { apiRequest } from './api'

export type Hobby = {
  id: number
  name: string
  goal: string | null
  recordCount: number
  archived: boolean
}

export type HobbyPatch = {
  name?: string
  goal?: string
  archived?: boolean
}

export function fetchHobbies(includeArchived = false) {
  const query = includeArchived ? '?includeArchived=true' : ''
  return apiRequest<Hobby[]>(`/api/hobbies${query}`)
}

export function createHobby(name: string, goal?: string) {
  return apiRequest<Hobby>('/api/hobbies', {
    method: 'POST',
    body: { name, goal },
  })
}

export function updateHobby(id: number, patch: HobbyPatch) {
  return apiRequest<Hobby>(`/api/hobbies/${id}`, {
    method: 'PATCH',
    body: patch,
  })
}
