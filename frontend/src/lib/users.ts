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

export type ChangePasswordPayload = { currentPassword: string; newPassword: string }

export function changePassword(payload: ChangePasswordPayload) {
  return apiRequest<{ accessToken: string }>('/api/users/me/password', {
    method: 'PATCH',
    body: payload,
  })
}

export function deleteAccount(password: string) {
  return apiRequest<void>('/api/users/me', {
    method: 'DELETE',
    body: { password },
  })
}
