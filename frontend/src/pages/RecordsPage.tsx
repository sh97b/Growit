import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { Button } from '../components/Button'
import { Loading } from '../components/Loading'
import { PlusIcon } from '../components/icons'
import { useHobbies } from '../hooks/useHobbies'
import { formatMonthDay } from '../lib/date'
import { fetchRecords } from '../lib/records'

const PAGE_SIZE = 20

function filterPillClass(active: boolean): string {
  return [
    'inline-flex min-h-11 items-center justify-center rounded-lg border px-4 text-sm font-medium whitespace-nowrap',
    active ? 'border-base bg-[color-mix(in_srgb,var(--color-base)_12%,white)] text-ink' : 'border-line-2 bg-white text-ink',
  ].join(' ')
}

export function RecordsPage() {
  const [hobbyId, setHobbyId] = useState<number | null>(null)
  const { data: activeHobbies } = useHobbies(false)
  const { data: allHobbies } = useHobbies(true)

  const hobbyNameById = new Map((allHobbies ?? []).map((hobby) => [hobby.id, hobby.name]))

  const totalQuery = useQuery({
    queryKey: ['records', 'total'],
    queryFn: () => fetchRecords({ page: 0, size: 1 }),
  })

  const listQuery = useInfiniteQuery({
    queryKey: ['records', 'list', hobbyId],
    queryFn: ({ pageParam }) => fetchRecords({ hobbyId: hobbyId ?? undefined, page: pageParam, size: PAGE_SIZE }),
    initialPageParam: 0,
    getNextPageParam: (lastPage, pages) => {
      const loaded = pages.reduce((sum, page) => sum + page.items.length, 0)
      return loaded < lastPage.totalCount ? pages.length : undefined
    },
  })

  if (totalQuery.isLoading || listQuery.isLoading) {
    return <Loading />
  }

  const noRecordsAtAll = (totalQuery.data?.totalCount ?? 0) === 0
  const records = listQuery.data?.pages.flatMap((page) => page.items) ?? []
  const totalCount = listQuery.data?.pages[0]?.totalCount ?? 0

  return (
    <div className="flex flex-col gap-5 lg:max-w-[840px]">
      <h1 className="font-serif text-xl font-semibold lg:text-[30px]">지난 기록</h1>

      {noRecordsAtAll ? (
        <div className="flex max-w-[720px] flex-col items-start gap-4 rounded-2xl bg-surface p-5">
          <p className="text-[17px] text-ink">아직 기록이 없어요. 첫 기록이 여기에 쌓여요.</p>
          <Link
            to="/records/new"
            className="inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-lg bg-base px-5 text-[15px] font-medium text-white no-underline lg:w-auto"
          >
            <PlusIcon width={18} height={18} />
            기록하기
          </Link>
        </div>
      ) : (
        <>
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              aria-pressed={hobbyId === null}
              onClick={() => setHobbyId(null)}
              className={filterPillClass(hobbyId === null)}
            >
              전체
            </button>
            {(activeHobbies ?? []).map((hobby) => (
              <button
                key={hobby.id}
                type="button"
                aria-pressed={hobbyId === hobby.id}
                onClick={() => setHobbyId(hobby.id)}
                className={filterPillClass(hobbyId === hobby.id)}
              >
                {hobby.name}
              </button>
            ))}
          </div>

          {totalCount === 0 ? (
            <p className="py-6 text-center text-sm text-ink-2">아직 이 분야의 기록이 없어요.</p>
          ) : (
            <div className="flex flex-col">
              {records.map((record) => (
                <Link
                  key={record.id}
                  to={`/records/${record.id}`}
                  className="flex flex-col gap-1.5 border-t border-line py-4 text-ink no-underline last:border-b"
                >
                  <span className="text-[13px] font-semibold text-ink-2">
                    {formatMonthDay(record.recordDate)} · {hobbyNameById.get(record.hobbyId) ?? ''}
                  </span>
                  <p className="truncate font-serif text-lg text-ink">{record.content}</p>
                </Link>
              ))}
            </div>
          )}

          {listQuery.hasNextPage ? (
            <Button
              type="button"
              variant="secondary"
              onClick={() => listQuery.fetchNextPage()}
              disabled={listQuery.isFetchingNextPage}
              className="self-center"
            >
              더 보기
            </Button>
          ) : null}
        </>
      )}
    </div>
  )
}
