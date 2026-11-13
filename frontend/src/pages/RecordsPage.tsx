import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ActivityHeatmap } from '../components/ActivityHeatmap'
import { Loading } from '../components/Loading'
import { Pagination } from '../components/Pagination'
import { PlusIcon } from '../components/icons'
import { useFitPageSize, useStablePageOnResize } from '../hooks/useFitPageSize'
import { useHobbies } from '../hooks/useHobbies'
import { useIsDesktop } from '../hooks/useIsDesktop'
import { formatMonthDay } from '../lib/date'
import { fetchRecords } from '../lib/records'

// 기록 카드 한 칸의 최소 높이(패딩+caption 줄+본문 한 줄+테두리, rem). F3: 화면 높이에 맞춰 개수를 정한다.
const CARD_MIN_HEIGHT_REM = 5.5
const CARD_GAP_REM = 0.75

function filterPillClass(active: boolean): string {
  return [
    'inline-flex min-h-11 items-center justify-center rounded-lg border px-4 text-sm font-medium whitespace-nowrap',
    active ? 'border-base bg-[color-mix(in_srgb,var(--color-base)_12%,white)] text-ink' : 'border-line-2 bg-white text-ink',
  ].join(' ')
}

export function RecordsPage() {
  const [hobbyId, setHobbyId] = useState<number | null>(null)
  const [page, setPage] = useState(0)
  const isDesktop = useIsDesktop()
  // 고정 개수 대신 목록 영역의 실제 높이에 맞춰 한 페이지 개수를 계산한다(F3).
  // 모바일은 페이지 스크롤을 유지하므로 1열 5개를 그대로 쓴다.
  const { containerRef, pageSize: fitPageSize } = useFitPageSize({
    itemHeightRem: CARD_MIN_HEIGHT_REM,
    gapRem: CARD_GAP_REM,
    columns: 2,
    min: 2,
    max: 12,
  })
  const pageSize = isDesktop ? fitPageSize : 5
  useStablePageOnResize(page, setPage, pageSize)
  const { data: activeHobbies } = useHobbies(false)
  const { data: allHobbies } = useHobbies(true)

  const hobbyNameById = new Map((allHobbies ?? []).map((hobby) => [hobby.id, hobby.name]))

  const totalQuery = useQuery({
    queryKey: ['records', 'total'],
    queryFn: () => fetchRecords({ page: 0, size: 1 }),
  })

  const listQuery = useQuery({
    queryKey: ['records', 'list', hobbyId, page, pageSize],
    queryFn: () => fetchRecords({ hobbyId: hobbyId ?? undefined, page, size: pageSize }),
  })

  if (totalQuery.isLoading || listQuery.isLoading) {
    return <Loading />
  }

  const noRecordsAtAll = (totalQuery.data?.totalCount ?? 0) === 0
  const records = listQuery.data?.items ?? []
  const totalCount = listQuery.data?.totalCount ?? 0
  const totalPages = Math.ceil(totalCount / pageSize)

  function handleHobbyChange(nextHobbyId: number | null) {
    setHobbyId(nextHobbyId)
    setPage(0)
  }

  const hobbyFilter = (
    <div className="flex flex-wrap gap-2">
      <button
        type="button"
        aria-pressed={hobbyId === null}
        onClick={() => handleHobbyChange(null)}
        className={filterPillClass(hobbyId === null)}
      >
        전체
      </button>
      {(activeHobbies ?? []).map((hobby) => (
        <button
          key={hobby.id}
          type="button"
          aria-pressed={hobbyId === hobby.id}
          onClick={() => handleHobbyChange(hobby.id)}
          className={filterPillClass(hobbyId === hobby.id)}
        >
          {hobby.name}
        </button>
      ))}
    </div>
  )

  return (
    <div className="flex flex-col gap-5 lg:min-h-0 lg:flex-1">
      <h1 className="text-page-title">지난 기록</h1>

      {noRecordsAtAll ? (
        <div className="flex max-w-[45rem] flex-col items-start gap-4 rounded-2xl bg-surface p-5">
          <p className="text-[1.0625rem] text-ink">아직 기록이 없어요. 첫 기록이 여기에 쌓여요.</p>
          <Link
            to="/records/new"
            className="inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-lg bg-base px-5 text-body-min font-medium text-white no-underline lg:w-auto"
          >
            <PlusIcon width={18} height={18} />
            기록하기
          </Link>
        </div>
      ) : (
        <div className="flex flex-col gap-5 lg:grid lg:min-h-0 lg:flex-1 lg:grid-cols-[minmax(0,1fr)_27.5rem] lg:items-stretch lg:gap-10">
          <div className="flex flex-col gap-5 lg:min-h-0">
            <div className="rounded-2xl bg-surface p-5 lg:hidden">
              <ActivityHeatmap weeks={52} hobbyId={hobbyId} />
            </div>

            {hobbyFilter}

            {totalCount === 0 ? (
              <p className="py-6 text-center text-sm text-ink-2">아직 이 분야의 기록이 없어요.</p>
            ) : (
              <div
                ref={containerRef}
                className="scrollbar-thin-fade grid grid-cols-1 content-start gap-3 lg:grid-cols-2 lg:flex-1 lg:min-h-0 lg:overflow-y-auto"
              >
                {records.map((record) => {
                  const firstTag = record.analysis?.tags[0]
                  return (
                    <Link
                      key={record.id}
                      to={`/records/${record.id}`}
                      className="flex flex-col gap-1.5 rounded-xl border border-line bg-white px-4 py-4 text-ink no-underline"
                    >
                      <span className="flex items-center justify-between gap-2">
                        <span className="text-caption-min font-semibold text-ink-2">
                          {formatMonthDay(record.recordDate)} · {hobbyNameById.get(record.hobbyId) ?? ''}
                        </span>
                        {firstTag ? (
                          <span className="inline-flex h-6 flex-none items-center rounded-md bg-tag-topic-bg px-2 text-caption-min font-medium text-tag-topic-fg">
                            {firstTag}
                          </span>
                        ) : null}
                      </span>
                      <p className="truncate font-serif text-lg text-ink">{record.content}</p>
                    </Link>
                  )
                })}
              </div>
            )}

            <Pagination page={page} totalPages={totalPages} onChange={setPage} />
          </div>

          <div className="hidden rounded-2xl bg-surface p-5 lg:sticky lg:top-6 lg:block">
            <ActivityHeatmap weeks={52} hobbyId={hobbyId} />
          </div>
        </div>
      )}
    </div>
  )
}
