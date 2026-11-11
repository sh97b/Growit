import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ChevronLeftIcon, ChevronRightIcon } from '../components/icons'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
import { Pagination } from '../components/Pagination'
import { useIsDesktop } from '../hooks/useIsDesktop'
import { fetchCoachHistory } from '../lib/ai'
import { formatMonthDay } from '../lib/date'

export function CoachHistoryPage() {
  const [page, setPage] = useState(0)
  const isDesktop = useIsDesktop()
  // 세 기준 해상도에서 페이지 스크롤이 없도록 맞춘 크기(U2).
  const pageSize = isDesktop ? 8 : 6
  const { data, isLoading, isError } = useQuery({
    queryKey: ['ai', 'coach', 'history', page, pageSize],
    queryFn: () => fetchCoachHistory({ page, size: pageSize }),
  })
  const items = data?.items ?? []
  const totalPages = Math.ceil((data?.totalCount ?? 0) / pageSize)

  return (
    <div className="flex flex-col gap-5 lg:max-w-[920px]">
      <nav className="text-sm text-ink-2">
        <Link to="/coach" className="inline-flex items-center gap-1 text-ink-2 no-underline">
          <ChevronLeftIcon width={16} height={16} />
          AI 회고
        </Link>
      </nav>
      <h1 className="text-page-title">이전 회고</h1>

      {isLoading ? <Loading /> : null}
      {isError ? <Note variant="sand">잠시 후 다시 시도해 주세요</Note> : null}

      {data && items.length === 0 ? <Note variant="calm">아직 받은 회고가 없어요.</Note> : null}

      {items.length > 0 ? (
        <div className="flex flex-col">
          {items.map((item) => (
            <Link
              key={item.id}
              to={`/coach/history/${item.id}`}
              className="flex items-center gap-3 border-t border-line py-4 text-ink no-underline last:border-b"
            >
              <span className="w-[108px] flex-none text-[13px] font-semibold tabular-nums text-ink-2">
                {item.startDate && item.endDate ? `${formatMonthDay(item.startDate)} – ${formatMonthDay(item.endDate)}` : ''}
              </span>
              <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                <span className="truncate font-serif text-[15px] font-semibold">
                  {item.periodSummary ?? '회고 결과'}
                </span>
                <span className="text-[13px] text-ink-2">기록 {item.recordsRead}개를 읽었어요</span>
              </span>
              <ChevronRightIcon width={18} height={18} className="flex-none text-ink-2" />
            </Link>
          ))}
        </div>
      ) : null}

      <Pagination page={page} totalPages={totalPages} onChange={setPage} />

      <Link
        to="/coach"
        className="inline-flex min-h-11 w-full items-center justify-center rounded-lg bg-base px-5 text-[15px] font-medium text-white no-underline lg:w-fit"
      >
        새 회고 받기
      </Link>
    </div>
  )
}
