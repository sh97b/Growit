import { useQuery } from '@tanstack/react-query'
import { fetchDotsCurrent } from '../lib/dots'

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
