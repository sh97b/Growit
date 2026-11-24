import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createBlock,
  deleteBlock,
  fetchBlocks,
  generateHowto,
  reorderBlocks,
  updateBlock,
  type BlockPayload,
  type RoadmapBlock,
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

// ---- 항목 블록 ----

export function blocksKey(itemId: number) {
  return ['roadmap', 'blocks', itemId] as const
}

/** 펼친 항목의 블록 목록. 보관된 로드맵에서도 읽을 수 있다. */
export function useBlocks(itemId: number, enabled: boolean) {
  return useQuery({ queryKey: blocksKey(itemId), queryFn: () => fetchBlocks(itemId), enabled })
}

/** 블록을 바꾸면 그 항목의 목록을 다시 받고, 목록의 "블록 N" 배지를 위해 로드맵도 다시 받는다. */
function useBlockMutation<TVariables, TResult>(
  hobbyId: number,
  itemId: number,
  mutationFn: (variables: TVariables) => Promise<TResult>,
) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: blocksKey(itemId) })
      queryClient.invalidateQueries({ queryKey: roadmapKey(hobbyId) })
    },
  })
}

export function useCreateBlock(hobbyId: number, itemId: number) {
  return useBlockMutation(
    hobbyId,
    itemId,
    ({ kind, payload }: { kind: Parameters<typeof createBlock>[1]; payload: BlockPayload }) =>
      createBlock(itemId, kind, payload),
  )
}

export function useUpdateBlock(hobbyId: number, itemId: number) {
  return useBlockMutation(hobbyId, itemId, ({ id, payload }: { id: number; payload: BlockPayload }) =>
    updateBlock(id, payload),
  )
}

export function useDeleteBlock(hobbyId: number, itemId: number) {
  return useBlockMutation(hobbyId, itemId, (id: number) => deleteBlock(id))
}

/** 순서는 저장이 끝나기 전에 서버 응답으로 목록 캐시를 바꾼다(실패하면 그대로). */
export function useReorderBlocks(hobbyId: number, itemId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (ids: number[]) => reorderBlocks(itemId, ids),
    onSuccess: (blocks: RoadmapBlock[]) => {
      queryClient.setQueryData(blocksKey(itemId), blocks)
      queryClient.invalidateQueries({ queryKey: roadmapKey(hobbyId) })
    },
  })
}

export function useGenerateHowto(hobbyId: number, itemId: number) {
  return useBlockMutation(hobbyId, itemId, () => generateHowto(itemId))
}
