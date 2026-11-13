import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Loading } from './Loading'
import { useHobbies } from '../hooks/useHobbies'
import { formatMonthDay, todayLocalDate } from '../lib/date'
import { fetchRecords } from '../lib/records'

/**
 * 최근 기록 카드 목록(홈 부품). 기록 작성·수정 사이드(LAYOUT-V2 3단계)에서도 재사용한다.
 * showAllLink가 true면 아래에 "지난 기록 모두 보기" 링크를 둔다(홈에서는 보이고, 좁은 사이드
 * 칼럼에서는 과해 보일 수 있어 끌 수 있게 했다).
 */
export function RecentRecordsCard({ limit = 3, showAllLink = true }: { limit?: number; showAllLink?: boolean }) {
  const listQuery = useQuery({ queryKey: ['records', 'home-recent'], queryFn: () => fetchRecords({ size: 3 }) })
  const { data: hobbies } = useHobbies(true)

  if (listQuery.isLoading) {
    return <Loading />
  }

  if (listQuery.isError || !listQuery.data || listQuery.data.items.length === 0) {
    return null
  }

  const hobbyNameById = new Map((hobbies ?? []).map((hobby) => [hobby.id, hobby.name]))
  const today = todayLocalDate()
  const visibleItems = listQuery.data.items.slice(0, limit)

  return (
    <div className="flex flex-col gap-3">
      <h2 className="text-card-title">최근 기록</h2>
      <div className="flex flex-col">
        {visibleItems.map((record) => {
          const firstTag = record.analysis?.tags[0]
          const isToday = record.recordDate === today
          return (
            <Link
              key={record.id}
              to={`/records/${record.id}`}
              className="flex flex-col gap-1.5 border-t border-line py-4 text-ink no-underline last:border-b"
            >
              <span className="flex items-center justify-between gap-2">
                <span className="text-caption-min font-semibold text-ink-2">
                  {isToday ? '오늘' : formatMonthDay(record.recordDate)} · {hobbyNameById.get(record.hobbyId) ?? ''}
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
      {showAllLink ? (
        <Link
          to="/records"
          className="inline-flex min-h-11 w-fit items-center text-sm font-medium text-base-ink underline underline-offset-4"
        >
          지난 기록 모두 보기
        </Link>
      ) : null}
    </div>
  )
}
