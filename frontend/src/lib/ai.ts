import { apiRequest } from './api'

export function deleteAiData() {
  return apiRequest<void>('/api/ai/data', { method: 'DELETE' })
}
