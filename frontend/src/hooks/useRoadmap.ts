import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  addRoadmapItem,
  addRoadmapStep,
  archiveRoadmap,
  createRoadmap,
  deleteRoadmapItem,
  deleteRoadmapStep,
  fetchRoadmap,
  setRoadmapItemDone,
  updateRoadmapItem,
  updateRoadmapStep,
  updateRoadmapTarget,
  type MoveDirection,
  type Roadmap,
} from '../lib/roadmap'

export function roadmapKey(hobbyId: number) {
  return ['roadmap', hobbyId] as const
}

export function useRoadmap(hobbyId: number) {
  return useQuery({
    queryKey: roadmapKey(hobbyId),
    queryFn: () => fetchRoadmap(hobbyId),
    enabled: Number.isFinite(hobbyId) && hobbyId > 0,
  })
}

/** 수정 API는 모두 갱신된 로드맵 전체를 돌려주므로 그대로 캐시에 넣는다. */
function useRoadmapMutation<TVariables>(hobbyId: number, mutationFn: (variables: TVariables) => Promise<Roadmap>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: (roadmap) => {
      queryClient.setQueryData(roadmapKey(hobbyId), roadmap)
    },
  })
}

export function useCreateRoadmap(hobbyId: number) {
  return useRoadmapMutation(hobbyId, (targetDate: string | null) => createRoadmap(hobbyId, targetDate))
}

export function useArchiveRoadmap(hobbyId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (roadmapId: number) => archiveRoadmap(roadmapId),
    onSuccess: () => {
      queryClient.setQueryData(roadmapKey(hobbyId), null)
    },
  })
}

export function useUpdateRoadmapTarget(hobbyId: number) {
  return useRoadmapMutation(hobbyId, ({ id, targetDate }: { id: number; targetDate: string | null }) =>
    updateRoadmapTarget(id, targetDate),
  )
}

export function useAddStep(hobbyId: number) {
  return useRoadmapMutation(hobbyId, ({ roadmapId, title }: { roadmapId: number; title: string }) =>
    addRoadmapStep(roadmapId, title),
  )
}

export function useUpdateStep(hobbyId: number) {
  return useRoadmapMutation(
    hobbyId,
    ({ id, patch }: { id: number; patch: { title?: string; periodHint?: string | null; move?: MoveDirection } }) =>
      updateRoadmapStep(id, patch),
  )
}

export function useDeleteStep(hobbyId: number) {
  return useRoadmapMutation(hobbyId, (id: number) => deleteRoadmapStep(id))
}

export function useAddItem(hobbyId: number) {
  return useRoadmapMutation(hobbyId, ({ stepId, text }: { stepId: number; text: string }) =>
    addRoadmapItem(stepId, text),
  )
}

export function useUpdateItem(hobbyId: number) {
  return useRoadmapMutation(hobbyId, ({ id, patch }: { id: number; patch: { text?: string; move?: MoveDirection } }) =>
    updateRoadmapItem(id, patch),
  )
}

export function useDeleteItem(hobbyId: number) {
  return useRoadmapMutation(hobbyId, (id: number) => deleteRoadmapItem(id))
}

export function useSetItemDone(hobbyId: number) {
  return useRoadmapMutation(hobbyId, ({ id, done }: { id: number; done: boolean }) => setRoadmapItemDone(id, done))
}
