import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ChevronRightIcon } from './icons'
import { useHobbies } from '../hooks/useHobbies'
import { formatMonthDay, todayLocalDate } from '../lib/date'
import { fetchRecords } from '../lib/records'

/**
 * 웹 홈의 최근 기록 3개(한 줄 요약형 행: 날짜·분야 · 내용 말줄임 · 주제). 행 높이는 홈 열의 밀도 변수(--hrow)를 따르고,
 * 줄이거나 숨기지 않는다. 로딩 중에는 같은 높이의 자리표시, 기록이 없으면 한 줄 안내를 같은 자리에 둔다.
 */
export function HomeRecentCompact() {
  const listQuery = useQuery({ queryKey: ['records', 'home-recent'], queryFn: () => fetchRecords({ size: 3 }) })
  const { data: hobbies } = useHobbies(true)
  const hobbyNameById = new Map((hobbies ?? []).map((hobby) => [hobby.id, hobby.name]))
  const today = todayLocalDate()
  const items = listQuery.data?.items.slice(0, 3) ?? []

  let rows
  if (listQuery.isLoading) {
    rows = [0, 1, 2].map((index) => (
      <div key={index} className="home-rec border-t border-line">
        <div className="h-3.5 w-2/3 rounded-full bg-rest motion-safe:animate-pulse" style={{ gridArea: 'body' }} />
      </div>
    ))
  } else if (items.length === 0) {
    rows = (
      <p className="home-rec border-t border-line text-sm text-ink-2">
        <span style={{ gridArea: 'body' }}>
          {listQuery.isError ? '기록을 지금은 불러오지 못했어요.' : '아직 기록이 없어요. 첫 기록을 남겨 보세요.'}
        </span>
      </p>
    )
  } else {
    // 넉넉함·보통: 날짜·분야 윗줄 + 내용(최대 2줄/1줄) + 태그 칩. 컴팩트: 한 줄 요약형(CSS 변수로 전환).
    rows = items.map((record) => {
      const firstTag = record.analysis?.tags[0]
      return (
        <Link
          key={record.id}
          to={`/records/${record.id}`}
          className="home-rec min-w-0 border-t border-line text-ink no-underline last:border-b"
        >
          <span className="home-rec-cap min-w-0 truncate text-xs font-semibold text-ink-2">
            {record.recordDate === today ? '오늘' : formatMonthDay(record.recordDate)} · {hobbyNameById.get(record.hobbyId) ?? ''}
          </span>
          <span className="home-rec-body min-w-0 break-words font-serif text-[1rem] leading-[1.5] text-ink">{record.content}</span>
          {firstTag ? (
            <span className="home-rec-tag inline-flex h-6 max-w-[7rem] flex-none items-center truncate rounded-md bg-tag-topic-bg px-2 text-xs font-medium text-tag-topic-fg">
              {firstTag}
            </span>
          ) : null}
        </Link>
      )
    })
  }

  return (
    <section aria-label="최근 기록" className="flex flex-col">
      <div className="flex h-8 items-center justify-between gap-3">
        <h2 className="text-card-title">최근 기록</h2>
        <Link
          to="/records"
          className="inline-flex h-8 flex-none items-center gap-0.5 rounded-full px-2 text-sm font-medium text-base-ink no-underline hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]"
        >
          지난 기록 모두 보기
          <ChevronRightIcon width={16} height={16} />
        </Link>
      </div>
      <div className="flex flex-col">{rows}</div>
    </section>
  )
}
