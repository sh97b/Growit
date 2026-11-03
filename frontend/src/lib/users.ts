import { apiRequest } from './api'

export type CurrentUser = {
  id: number
  email: string
  displayName: string | null
  aiEnabled: boolean
  createdAt: string
}

export function fetchCurrentUser() {
  return apiRequest<CurrentUser>('/api/users/me')
}

export type UpdateMePayload = { displayName: string; aiEnabled?: boolean }

export function updateMe(payload: UpdateMePayload) {
  return apiRequest<CurrentUser>('/api/users/me', {
    method: 'PATCH',
    body: payload,
  })
}

export function updateDisplayName(displayName: string) {
  return updateMe({ displayName })
}
