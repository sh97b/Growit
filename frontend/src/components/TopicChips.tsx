import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchRecords } from '../lib/records'

/**
 * 디자인에 없는 추가(U3): 최근 기록 30개의 주제 태그 상위 4개를 칩으로 보여 준다.
 * 홈·기록 작성/수정 사이드(LAYOUT-V2)에서 재사용한다.
 */
export function TopicChips() {
  const query = useQuery({ queryKey: ['records', 'topic-chips'], queryFn: () => fetchRecords({ size: 30 }) })

  if (query.isLoading || !query.data) return null

  const counts = new Map<string, number>()
  for (const record of query.data.items) {
    const tag = record.analysis?.tags[0]
    if (tag) counts.set(tag, (counts.get(tag) ?? 0) + 1)
  }
  const topTags = [...counts.entries()].sort((a, b) => b[1] - a[1]).slice(0, 4)

  if (topTags.length === 0) return null

  return (
    <div className="flex flex-col gap-2">
      <h2 className="text-card-title">자주 하는 주제</h2>
      <div className="flex flex-wrap gap-2">
        {topTags.map(([tag]) => (
          <Link
            key={tag}
            to={`/records/new?topic=${encodeURIComponent(tag)}`}
            className="inline-flex h-9 items-center rounded-md bg-tag-topic-bg px-3 text-sm font-medium text-tag-topic-fg no-underline"
          >
            {tag}
          </Link>
        ))}
      </div>
    </div>
  )
}
