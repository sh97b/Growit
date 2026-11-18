import { useQuery } from '@tanstack/react-query'
import { fetchLatestNextAction } from '../lib/records'

/** 가장 최근 기록의 "다음에 해 볼 일"(홈 카드와 기록 작성 화면의 지난 제안 칩이 같이 쓴다). */
export function useLatestNextAction() {
  return useQuery({ queryKey: ['records', 'next-action', 'latest'], queryFn: fetchLatestNextAction })
}
