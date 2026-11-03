import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchCurrentUser, updateMe } from '../lib/users'

export function useCurrentUser() {
  return useQuery({ queryKey: ['currentUser'], queryFn: fetchCurrentUser })
}

export function useUpdateDisplayName() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (displayName: string) => updateMe({ displayName }),
    onSuccess: (user) => {
      queryClient.setQueryData(['currentUser'], user)
    },
  })
}

/** aiEnabled만 바꿀 때도 displayName을 함께 보내야 하는 API 제약 때문에 현재 이름을 같이 받는다. */
export function useUpdateAiEnabled() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ displayName, aiEnabled }: { displayName: string; aiEnabled: boolean }) =>
      updateMe({ displayName, aiEnabled }),
    onSuccess: (user) => {
      queryClient.setQueryData(['currentUser'], user)
    },
  })
}
