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

/**
 * T2 3번: 재분석·피드백·태그 고치기 응답(reanalyze/submitFeedback/editTags)의 RecordResponse는
 * 백엔드가 이웃 기록을 계산하지 않고 항상 previousRecord·nextRecord를 null로 보낸다
 * (RecordService.toDetailResponse(record) 단일 인자 오버로드, 2·3번째 인자를 null로 고정 호출).
 * 예전에는 이 응답으로 캐시를 통째로 덮어써서, 이전 기록이 있던 저장 직후 화면에서 "도움돼요"
 * 같은 AI 피드백을 누르면 previousRecord가 null로 바뀌어 "첫 기록이에요"로 잘못 바뀌었다.
 * 이제 이웃 정보만은 기존 캐시 값을 그대로 유지한다(이 세 API는 애초에 이웃을 갱신하지 않으므로).
 */
function useApplyAnalysisResult() {
  const queryClient = useQueryClient()
  return (record: RecordDetail) => {
    queryClient.setQueryData<RecordDetail>(['records', record.id], (old) => ({
      ...record,
      previousRecord: old ? old.previousRecord : record.previousRecord,
      nextRecord: old ? old.nextRecord : record.nextRecord,
    }))
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

/**
 * 누르는 즉시 선택 표시를 바꾸는 낙관적 갱신을 쓴다. 실패하면 onMutate가 저장해 둔 이전 값으로 되돌린다
 * (버튼 쪽은 isPending으로 화면을 회색 처리하지 않고, 중복 클릭만 내부에서 무시한다).
 */
export function useSubmitAnalysisFeedback() {
  const queryClient = useQueryClient()
  const apply = useApplyAnalysisResult()
  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: AnalysisFeedbackPayload }) =>
      submitAnalysisFeedback(id, payload),
    onMutate: async ({ id, payload }) => {
      const queryKey = ['records', id]
      await queryClient.cancelQueries({ queryKey })
      const previous = queryClient.getQueryData<RecordDetail>(queryKey)
      queryClient.setQueryData<RecordDetail>(queryKey, (old) => {
        if (!old || !old.analysis) return old
        return {
          ...old,
          analysis: {
            ...old.analysis,
            ...(payload.change !== undefined ? { changeFeedback: payload.change } : {}),
            ...(payload.tags !== undefined ? { tagsFeedback: payload.tags } : {}),
          },
        }
      })
      return { previous, id }
    },
    onError: (_error, _variables, context) => {
      if (context) {
        queryClient.setQueryData(['records', context.id], context.previous)
      }
    },
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
