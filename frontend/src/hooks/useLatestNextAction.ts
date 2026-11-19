import { useQuery } from '@tanstack/react-query'
import { fetchLatestNextAction } from '../lib/records'

/**
 * 가장 최근 기록의 "다음에 해 볼 일". hobbyId를 주면 그 분야의 가장 최근 기록만 본다(기록 작성 화면의
 * 지난 제안 칩). 생략하면 전체에서 가장 최근 기록이다(홈 카드).
 */
export function useLatestNextAction(hobbyId?: number | null, options: { enabled?: boolean } = {}) {
  return useQuery({
    queryKey: ['records', 'next-action', 'latest', hobbyId ?? 'all'],
    queryFn: () => fetchLatestNextAction(hobbyId),
    enabled: options.enabled ?? true,
  })
}
