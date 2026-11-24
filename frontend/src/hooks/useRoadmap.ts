import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  addRoadmapItem,
  addRoadmapStep,
  archiveRoadmap,
  deleteRoadmap,
  fetchArchivedRoadmaps,
  fetchRoadmapById,
  restoreRoadmap,
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

export function archivedKey(hobbyId: number) {
  return ['roadmaps', 'archived', hobbyId] as const
}

export function useArchivedRoadmaps(hobbyId: number) {
  return useQuery({
    queryKey: archivedKey(hobbyId),
    queryFn: () => fetchArchivedRoadmaps(hobbyId),
    enabled: Number.isFinite(hobbyId) && hobbyId > 0,
  })
}

/** 보관된 로드맵 한 개(읽기 전용 보기). */
export function useRoadmapById(id: number | null) {
  return useQuery({
    queryKey: ['roadmaps', 'detail', id],
    queryFn: () => fetchRoadmapById(id!),
    enabled: id != null,
  })
}

/** 복원하면 진행 중 로드맵이 바뀌므로 진행 중 캐시를 갱신하고 보관함 목록은 다시 받는다. */
export function useRestoreRoadmap(hobbyId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, swap }: { id: number; swap: boolean }) => restoreRoadmap(id, swap),
    onSuccess: (roadmap) => {
      queryClient.setQueryData(roadmapKey(hobbyId), roadmap)
      queryClient.invalidateQueries({ queryKey: archivedKey(hobbyId) })
      queryClient.invalidateQueries({ queryKey: ['roadmaps', 'detail'] })
    },
  })
}

export function useDeleteArchivedRoadmap(hobbyId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => deleteRoadmap(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: archivedKey(hobbyId) })
      queryClient.invalidateQueries({ queryKey: ['roadmaps', 'detail'] })
    },
  })
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
  return useRoadmapMutation(hobbyId, ({ targetDate, mode }: { targetDate: string | null; mode?: 'AI' | 'MANUAL' }) =>
    createRoadmap(hobbyId, targetDate, mode),
  )
}

export function useArchiveRoadmap(hobbyId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (roadmapId: number) => archiveRoadmap(roadmapId),
    onSuccess: () => {
      queryClient.setQueryData(roadmapKey(hobbyId), null)
      queryClient.invalidateQueries({ queryKey: archivedKey(hobbyId) })
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
    ({ id, patch }: { id: number; patch: { title?: string; periodHint?: string | null; move?: MoveDirection; position?: number } }) =>
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
  return useRoadmapMutation(hobbyId, ({ id, patch }: { id: number; patch: { text?: string; move?: MoveDirection; position?: number } }) =>
    updateRoadmapItem(id, patch),
  )
}

export function useDeleteItem(hobbyId: number) {
  return useRoadmapMutation(hobbyId, (id: number) => deleteRoadmapItem(id))
}

export function useSetItemDone(hobbyId: number) {
  return useRoadmapMutation(hobbyId, ({ id, done }: { id: number; done: boolean }) => setRoadmapItemDone(id, done))
}
