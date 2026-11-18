import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createCustomFeeling, deleteCustomFeeling, fetchCustomFeelings, type FeelingDirection } from '../lib/feelings'

export function useCustomFeelings() {
  return useQuery({ queryKey: ['feelings'], queryFn: fetchCustomFeelings, staleTime: 60_000 })
}

export function useCreateCustomFeeling() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ label, direction }: { label: string; direction: FeelingDirection }) => createCustomFeeling(label, direction),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['feelings'] }),
  })
}

export function useDeleteCustomFeeling() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => deleteCustomFeeling(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['feelings'] }),
  })
}
