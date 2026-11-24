import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchRecords } from '../lib/records'

/**
 * 디자인에 없는 추가(U3): 최근 기록 30개의 주제 태그 상위 4개를 칩으로 보여 준다.
 * 홈·기록 작성/수정 사이드(LAYOUT-V2)에서 재사용한다.
 */
/** dense는 웹 홈용 한 줄 모양(제목과 칩이 한 줄, 로딩·없음에도 같은 줄이 남는다). */
export function TopicChips({ dense = false }: { dense?: boolean }) {
  const query = useQuery({ queryKey: ['records', 'topic-chips'], queryFn: () => fetchRecords({ size: 30 }) })

  if (dense && (query.isLoading || !query.data)) {
    return <div className="h-12 rounded-lg bg-surface motion-safe:animate-pulse" />
  }
  if (query.isLoading || !query.data) return null

  const counts = new Map<string, number>()
  for (const record of query.data.items) {
    const tag = record.analysis?.tags[0]
    if (tag) counts.set(tag, (counts.get(tag) ?? 0) + 1)
  }
  const topTags = [...counts.entries()].sort((a, b) => b[1] - a[1]).slice(0, 4)

  if (dense) {
    // 넉넉함·보통: 제목 위·칩 아래, 컴팩트: 제목과 칩이 한 줄(CSS 변수 --tp-dir).
    return (
      <div className="home-topics">
        <h2 className="flex-none text-card-title">자주 하는 주제</h2>
        {topTags.length === 0 ? (
          <span className="t-sub truncate text-ink-2">기록이 쌓이면 자주 하는 주제가 보여요</span>
        ) : (
          <div className="flex min-w-0 flex-wrap gap-2 overflow-hidden">
            {topTags.map(([tag]) => (
              <Link
                key={tag}
                to={`/records/new?topic=${encodeURIComponent(tag)}`}
                className="home-chip t-sub inline-flex flex-none items-center rounded-md bg-tag-topic-bg px-3 font-medium text-tag-topic-fg no-underline"
              >
                {tag}
              </Link>
            ))}
          </div>
        )}
      </div>
    )
  }

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
