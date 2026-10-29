import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchCurrentUser, updateDisplayName } from '../lib/users'

export function useCurrentUser() {
  return useQuery({ queryKey: ['currentUser'], queryFn: fetchCurrentUser })
}

export function useUpdateDisplayName() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (displayName: string) => updateDisplayName(displayName),
    onSuccess: (user) => {
      queryClient.setQueryData(['currentUser'], user)
    },
  })
}
