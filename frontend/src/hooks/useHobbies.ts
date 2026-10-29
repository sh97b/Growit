import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createHobby, fetchHobbies, type HobbyPatch, updateHobby } from '../lib/hobbies'

export function useHobbies(includeArchived = false) {
  return useQuery({
    queryKey: ['hobbies', includeArchived],
    queryFn: () => fetchHobbies(includeArchived),
  })
}

export function useCreateHobby() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ name, goal }: { name: string; goal?: string }) => createHobby(name, goal),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['hobbies'] })
    },
  })
}

export function useUpdateHobby() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, patch }: { id: number; patch: HobbyPatch }) => updateHobby(id, patch),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['hobbies'] })
    },
  })
}
