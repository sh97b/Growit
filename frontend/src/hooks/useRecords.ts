import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  deleteRecord,
  editAnalysisTags,
  fetchRecord,
  reanalyzeRecord,
  submitAnalysisFeedback,
  updateRecord,
  type AnalysisFeedbackPayload,
  type RecordDetail,
  type RecordUpdatePayload,
} from '../lib/records'

const POLL_INTERVAL_MS = 2000

export function useRecord(id: number) {
  return useQuery({
    queryKey: ['records', id],
    queryFn: () => fetchRecord(id),
    // aiStatus가 PENDING인 동안에만 2초마다 다시 확인하고, 그 외에는 멈춘다(화면을 떠나면 자동으로도 멈춘다).
    refetchInterval: (query) => (query.state.data?.aiStatus === 'PENDING' ? POLL_INTERVAL_MS : false),
  })
}

function useInvalidateRecords() {
  const queryClient = useQueryClient()
  return () => {
    queryClient.invalidateQueries({ queryKey: ['records'] })
    queryClient.invalidateQueries({ queryKey: ['hobbies'] })
    queryClient.invalidateQueries({ queryKey: ['stats'] })
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

function useApplyAnalysisResult() {
  const queryClient = useQueryClient()
  return (record: RecordDetail) => {
    queryClient.setQueryData(['records', record.id], record)
    queryClient.invalidateQueries({ queryKey: ['records', 'list'] })
  }
}

export function useReanalyzeRecord() {
  const apply = useApplyAnalysisResult()
  return useMutation({
    mutationFn: (id: number) => reanalyzeRecord(id),
    onSuccess: apply,
  })
}

export function useSubmitAnalysisFeedback() {
  const apply = useApplyAnalysisResult()
  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: AnalysisFeedbackPayload }) =>
      submitAnalysisFeedback(id, payload),
    onSuccess: apply,
  })
}

export function useEditAnalysisTags() {
  const apply = useApplyAnalysisResult()
  return useMutation({
    mutationFn: ({ id, tags }: { id: number; tags: string[] }) => editAnalysisTags(id, tags),
    onSuccess: apply,
  })
}
