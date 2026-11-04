import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ChevronLeftIcon } from '../components/icons'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
import { fetchCoachHistory } from '../lib/ai'
import { formatMonthDay } from '../lib/date'

export function CoachHistoryPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['ai', 'coach', 'history'],
    queryFn: () => fetchCoachHistory({ size: 20 }),
  })

  return (
    <div className="flex flex-col gap-5 lg:max-w-[920px]">
      <nav className="text-sm text-ink-2">
        <Link to="/coach" className="inline-flex items-center gap-1 text-ink-2 no-underline">
          <ChevronLeftIcon width={16} height={16} />
          AI 회고
        </Link>
      </nav>
      <div className="flex items-center justify-between gap-3">
        <h1 className="font-serif text-xl font-semibold lg:text-[26px]">이전 회고 목록</h1>
        <Link to="/coach" className="text-sm font-medium text-base-ink underline underline-offset-4">
          새 회고 받기
        </Link>
      </div>

      {isLoading ? <Loading /> : null}
      {isError ? <Note variant="sand">잠시 후 다시 시도해 주세요</Note> : null}

      {data && data.length === 0 ? <Note variant="calm">아직 받은 회고가 없어요.</Note> : null}

      {data && data.length > 0 ? (
        <div className="flex flex-col">
          {data.map((item) => (
            <Link
              key={item.id}
              to={`/coach/history/${item.id}`}
              className="flex flex-col gap-1 border-t border-line py-4 text-ink no-underline last:border-b"
            >
              <span className="font-serif text-lg">
                {item.startDate && item.endDate ? `${formatMonthDay(item.startDate)} – ${formatMonthDay(item.endDate)}` : ''}
              </span>
              <span className="text-sm text-ink-2">기록 {item.recordsRead}개를 읽었어요</span>
            </Link>
          ))}
        </div>
      ) : null}
    </div>
  )
}
