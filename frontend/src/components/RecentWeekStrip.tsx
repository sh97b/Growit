import { useQuery } from '@tanstack/react-query'
import { Loading } from './Loading'
import { WeekStrip, type WeekStripDay } from './WeekStrip'
import { addDays, startOfWeekLocal, todayLocalDate } from '../lib/date'
import { fetchActivityMap } from '../lib/stats'

/** 이번 주(월~일 고정) 줄. 홈과 저장 직후 화면 사이드에서 쓰며, 데이터는 기존 활동 지도 API를 이번 주 범위로 재사용한다. */
export function RecentWeekStrip({ home = false }: { home?: boolean }) {
  const today = todayLocalDate()
  const [year, month, day] = today.split('-').map(Number)
  const from = startOfWeekLocal(new Date(year, month - 1, day))
  const to = addDays(from, 6)
  const query = useQuery({
    queryKey: ['stats', 'activity-map', from, to],
    queryFn: () => fetchActivityMap({ from, to }),
  })

  if (query.isLoading) {
    return home ? <div className="h-[5.5rem] rounded-xl bg-surface motion-safe:animate-pulse" /> : <Loading />
  }

  const datesWithRecord = new Set((query.data ?? []).map((entry) => entry.date))
  const days: WeekStripDay[] = Array.from({ length: 7 }, (_, index) => {
    const date = addDays(from, index)
    return { date, isToday: date === today, isFuture: date > today, hasRecord: datesWithRecord.has(date) }
  })

  return <WeekStrip days={days} home={home} />
}
