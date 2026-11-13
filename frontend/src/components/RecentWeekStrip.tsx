import { useQuery } from '@tanstack/react-query'
import { Loading } from './Loading'
import { WeekStrip, type WeekStripDay } from './WeekStrip'
import { addDays, todayLocalDate } from '../lib/date'
import { fetchActivityMap } from '../lib/stats'

/** 최근 7일 줄(홈 부품). 저장 직후 화면 사이드(LAYOUT-V2 3단계)에서도 재사용한다. */
export function RecentWeekStrip() {
  const today = todayLocalDate()
  const from = addDays(today, -6)
  const query = useQuery({
    queryKey: ['stats', 'activity-map', from, today],
    queryFn: () => fetchActivityMap({ from, to: today }),
  })

  if (query.isLoading) {
    return <Loading />
  }

  const datesWithRecord = new Set((query.data ?? []).map((entry) => entry.date))
  const days: WeekStripDay[] = Array.from({ length: 7 }, (_, index) => {
    const date = addDays(from, index)
    return { date, isToday: date === today, hasRecord: datesWithRecord.has(date) }
  })

  return <WeekStrip days={days} />
}
