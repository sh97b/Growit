import { Link, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeftIcon } from '../components/icons'
import { CoachResultView } from '../components/CoachResultView'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
import { ApiError } from '../lib/api'
import { fetchCoachResult, type CoachResult } from '../lib/ai'

export function CoachHistoryDetailPage() {
  const { id } = useParams()
  const resultId = Number(id)
  const queryClient = useQueryClient()
  const queryKey = ['ai', 'results', resultId]
  const { data, isLoading, isError, error } = useQuery({
    queryKey,
    queryFn: () => fetchCoachResult(resultId),
  })

  return (
    <div className="flex flex-col gap-5 lg:max-w-[57.5rem]">
      <nav className="text-sm text-ink-2">
        <Link to="/coach/history" className="inline-flex items-center gap-1 text-ink-2 no-underline">
          <ChevronLeftIcon width={16} height={16} />
          이전 회고 목록
        </Link>
      </nav>

      {isLoading ? <Loading /> : null}
      {isError ? (
        <Note variant="sand">
          {error instanceof ApiError && error.status === 404
            ? '회고를 찾을 수 없어요.'
            : '잠시 후 다시 시도해 주세요'}
        </Note>
      ) : null}
      {data ? (
        <CoachResultView
          result={data}
          onResultChange={(updated: CoachResult) => queryClient.setQueryData(queryKey, updated)}
        />
      ) : null}
    </div>
  )
}
