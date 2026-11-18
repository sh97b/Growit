import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchDotPresets, fetchDotPreview, fetchDotsCurrent, startDotGoal, stopDotGoal } from '../lib/dots'

/**
 * 쿼리 키를 ['stats', ...]로 시작하게 둬서, 기록을 저장한 뒤 이미 하는 ['stats'] 무효화에 같이 다시 불러진다.
 * 서버 쪽 복구(저장할 때 실패한 칠하기 보정)를 일으키는 쓰기 있는 GET이라 캐시 시간을 짧게 두고,
 * 창을 다시 볼 때마다 부르지는 않는다. 실패는 화면에 알리지 않고(호출하는 쪽이 data만 본다) 그냥 넘어간다.
 */
export function useDotsCurrent() {
  return useQuery({
    queryKey: ['stats', 'dots', 'current'],
    queryFn: fetchDotsCurrent,
    staleTime: 30_000,
    refetchOnWindowFocus: false,
    retry: false,
  })
}

/** 기본 그림 목록은 서버에 박혀 있어 바뀌지 않는다. 시작 흐름을 열 때만 불러온다. */
export function useDotPresets(enabled: boolean) {
  return useQuery({
    queryKey: ['dots', 'presets'],
    queryFn: fetchDotPresets,
    staleTime: Infinity,
    enabled,
  })
}

/** 같은 그림·목표 일수의 미리보기는 늘 같은 결과라 다시 불러오지 않는다. */
export function useDotPreviews(keys: string[], goalDays: number | null, enabled: boolean) {
  return useQueries({
    queries: keys.map((key) => ({
      queryKey: ['dots', 'preview', key, goalDays],
      queryFn: () => fetchDotPreview(key, goalDays as number),
      staleTime: Infinity,
      enabled: enabled && goalDays != null,
      networkMode: 'always' as const,
      retry: false,
    })),
  })
}

export function useStartDot() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ presetKey, goalDays }: { presetKey: string; goalDays: number }) => startDotGoal(presetKey, goalDays),
    // 연결이 없을 때 요청을 멈춰 두고 "시작하는 중"으로 남지 않게 바로 실패시켜 안내하고, 자동 재시도는 하지 않는다
    // (응답만 놓친 경우 다시 보내면 이미 시작됐다는 409가 나서 오히려 헷갈린다).
    networkMode: 'always',
    retry: false,
    // 이미 진행 중인 그림이 있어 409가 나도(다른 기기에서 시작한 경우) 화면이 실제 상태를 보게 다시 불러온다.
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['stats', 'dots'] }),
  })
}

export function useStopDot() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (goalId: number) => stopDotGoal(goalId),
    networkMode: 'always',
    retry: false,
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['stats', 'dots'] }),
  })
}
