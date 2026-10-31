import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { deleteRecord, fetchRecord, updateRecord, type RecordUpdatePayload } from '../lib/records'

export function useRecord(id: number) {
  return useQuery({ queryKey: ['records', id], queryFn: () => fetchRecord(id) })
}

function useInvalidateRecords() {
  const queryClient = useQueryClient()
  return () => {
    queryClient.invalidateQueries({ queryKey: ['records'] })
    queryClient.invalidateQueries({ queryKey: ['hobbies'] })
  }
}

export function useUpdateRecord() {
  const invalidate = useInvalidateRecords()
  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: RecordUpdatePayload }) => updateRecord(id, payload),
    onSuccess: invalidate,
  })
}

export function useDeleteRecord() {
  const invalidate = useInvalidateRecords()
  return useMutation({
    mutationFn: (id: number) => deleteRecord(id),
    onSuccess: invalidate,
  })
}
