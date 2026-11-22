import { useQuery } from '@tanstack/react-query'
import { fetchGrowth, type GrowthPeriod } from '../lib/growth'

export function useGrowth(period: GrowthPeriod, hobbyId: number | null) {
  return useQuery({
    queryKey: ['growth', period, hobbyId],
    queryFn: () => fetchGrowth({ period, hobbyId }),
  })
}
