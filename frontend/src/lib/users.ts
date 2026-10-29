import { apiRequest } from './api'

export type CurrentUser = {
  id: number
  email: string
  displayName: string | null
  createdAt: string
}

export function fetchCurrentUser() {
  return apiRequest<CurrentUser>('/api/users/me')
}

export function updateDisplayName(displayName: string) {
  return apiRequest<CurrentUser>('/api/users/me', {
    method: 'PATCH',
    body: { displayName },
  })
}
