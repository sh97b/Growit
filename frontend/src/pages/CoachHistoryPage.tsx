import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ChevronLeftIcon, ChevronRightIcon } from '../components/icons'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
import { Pagination } from '../components/Pagination'
import { useFitPageSize, useStablePageOnResize } from '../hooks/useFitPageSize'
import { useIsDesktop } from '../hooks/useIsDesktop'
import { fetchCoachHistory } from '../lib/ai'
import { formatMonthDay } from '../lib/date'

// 회고 목록 한 줄의 최소 높이(패딩+두 줄+테두리, rem). F3: 화면 높이에 맞춰 개수를 정한다.
const ROW_MIN_HEIGHT_REM = 4.5

export function CoachHistoryPage() {
  const [page, setPage] = useState(0)
  const isDesktop = useIsDesktop()
  // 고정 개수 대신 목록 영역의 실제 높이에 맞춰 한 페이지 개수를 계산한다(F3).
  // 모바일은 페이지 스크롤을 유지하므로 6개를 그대로 쓴다.
  const { containerRef, pageSize: fitPageSize } = useFitPageSize({
    itemHeightRem: ROW_MIN_HEIGHT_REM,
    min: 2,
    max: 12,
  })
  const pageSize = isDesktop ? fitPageSize : 6
  useStablePageOnResize(page, setPage, pageSize)
  const { data, isLoading, isError } = useQuery({
    queryKey: ['ai', 'coach', 'history', page, pageSize],
    queryFn: () => fetchCoachHistory({ page, size: pageSize }),
  })
  const items = data?.items ?? []
  const totalPages = Math.ceil((data?.totalCount ?? 0) / pageSize)

  return (
    <div className="flex flex-col gap-5 lg:mx-auto lg:min-h-0 lg:flex-1 lg:max-w-[57.5rem]">
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
        <div ref={containerRef} className="scrollbar-thin-fade flex flex-col lg:flex-1 lg:min-h-0 lg:overflow-y-auto">
          {items.map((item) => (
            <Link
              key={item.id}
              to={`/coach/history/${item.id}`}
              className="flex items-center gap-3 border-t border-line py-4 text-ink no-underline last:border-b"
            >
              <span className="w-[6.75rem] flex-none text-caption-min font-semibold tabular-nums text-ink-2">
                {item.startDate && item.endDate ? `${formatMonthDay(item.startDate)} – ${formatMonthDay(item.endDate)}` : ''}
              </span>
              <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                <span className="truncate font-serif text-body-min font-semibold">
                  {item.periodSummary ?? '회고 결과'}
                </span>
                <span className="text-caption-min text-ink-2">기록 {item.recordsRead}개를 읽었어요</span>
              </span>
              <ChevronRightIcon width={18} height={18} className="flex-none text-ink-2" />
            </Link>
          ))}
        </div>
      ) : null}

      <Pagination page={page} totalPages={totalPages} onChange={setPage} />

      <Link
        to="/coach"
        className="inline-flex min-h-11 w-full items-center justify-center rounded-lg bg-base px-5 text-body-min font-medium text-white no-underline lg:w-fit"
      >
        새 회고 받기
      </Link>
    </div>
  )
}
