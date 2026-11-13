import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CoachResultView } from '../components/CoachResultView'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
import { Full, PageHeader } from '../components/PageLayout'
import { useCurrentUser } from '../hooks/useCurrentUser'
import { useHobbies } from '../hooks/useHobbies'
import { ApiError } from '../lib/api'
import { fetchCoach, fetchLatestCoach, markCoachViewed, type CoachPeriod, type CoachResult } from '../lib/ai'
import { pickClass } from '../lib/recordForm'

const PERIODS: { value: CoachPeriod; label: string }[] = [
  { value: 'THIS_WEEK', label: '이번 주' },
  { value: '2W', label: '최근 2주' },
  { value: '1M', label: '한 달' },
]

const LATEST_VALID_DAYS = 7

function isRecentEnough(createdAt: string | null): boolean {
  if (!createdAt) return false
  const ageMs = Date.now() - new Date(createdAt).getTime()
  return ageMs < LATEST_VALID_DAYS * 24 * 60 * 60 * 1000
}

export function CoachPage() {
  const queryClient = useQueryClient()
  const { data: currentUser, isLoading: userLoading } = useCurrentUser()
  const { data: activeHobbies, isLoading: hobbiesLoading } = useHobbies(false)
  const [hobbyId, setHobbyId] = useState<number | null>(null)
  const [period, setPeriod] = useState<CoachPeriod>('2W')
  const [result, setResult] = useState<CoachResult | null>(null)
  const viewedIds = useRef(new Set<number>())

  // 전체(null)를 기본으로 하고, 특정 분야를 고르면 그 값을 쓴다. 분야 목록이 바뀌어 더 이상
  // 활성 목록에 없는 분야가 선택돼 있으면(보관 등) 전체로 되돌린다.
  const effectiveHobbyId =
    hobbyId != null && activeHobbies?.some((hobby) => hobby.id === hobbyId) ? hobbyId : null

  const latestQuery = useQuery({
    queryKey: ['ai', 'coach', 'latest'],
    queryFn: fetchLatestCoach,
    enabled: !!currentUser?.aiEnabled,
  })

  const mutation = useMutation({
    mutationFn: () => fetchCoach({ hobbyId: effectiveHobbyId ?? undefined, period }),
    onSuccess: (data) => setResult(data),
  })

  const shownResult = result ?? (latestQuery.data && isRecentEnough(latestQuery.data.createdAt) ? latestQuery.data : null)

  // 자동 회고를 열면 한 번만 viewed 처리한다(이 화면에 머무는 동안 중복 호출 방지).
  useEffect(() => {
    if (!shownResult?.isNew || shownResult.id == null || viewedIds.current.has(shownResult.id)) return
    viewedIds.current.add(shownResult.id)
    markCoachViewed(shownResult.id)
      .then((updated) => {
        queryClient.setQueryData(['ai', 'coach', 'latest'], updated)
        setResult((prev) => (prev && prev.id === updated.id ? updated : prev))
      })
      .catch(() => {
        viewedIds.current.delete(shownResult.id!)
      })
  }, [shownResult?.id, shownResult?.isNew, queryClient])

  if (userLoading || hobbiesLoading || latestQuery.isLoading) {
    return <Loading />
  }

  if (!currentUser?.aiEnabled) {
    return (
      <div className="flex flex-col gap-4">
        <PageHeader title="AI 회고" />
        <Note variant="calm">
          <span>
            AI 정리가 꺼져 있어요. 켜면 기간을 돌아보는 회고를 받을 수 있어요.{' '}
            <Link to="/settings/ai" className="font-medium text-base-ink underline underline-offset-4">
              켜기
            </Link>
          </span>
        </Note>
      </div>
    )
  }

  const selectors = (
    <>
      {activeHobbies && activeHobbies.length > 0 ? (
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            aria-pressed={effectiveHobbyId === null}
            onClick={() => {
              setHobbyId(null)
              setResult(null)
            }}
            className={pickClass(effectiveHobbyId === null)}
          >
            전체
          </button>
          {activeHobbies.map((hobby) => (
            <button
              key={hobby.id}
              type="button"
              aria-pressed={effectiveHobbyId === hobby.id}
              onClick={() => {
                setHobbyId(hobby.id)
                setResult(null)
              }}
              className={pickClass(effectiveHobbyId === hobby.id)}
            >
              {hobby.name}
            </button>
          ))}
        </div>
      ) : null}

      <div className="flex flex-wrap gap-2">
        {PERIODS.map((item) => (
          <button
            key={item.value}
            type="button"
            aria-pressed={period === item.value}
            onClick={() => {
              setPeriod(item.value)
              setResult(null)
            }}
            className={pickClass(period === item.value)}
          >
            {item.label}
          </button>
        ))}
      </div>
    </>
  )

  return (
    <Full>
      <PageHeader title="AI 회고" />

      <div className="flex flex-col gap-5 lg:flex-row lg:flex-wrap lg:items-center lg:justify-between">{selectors}</div>

      {!shownResult ? (
        <button
          type="button"
          disabled={mutation.isPending}
          onClick={() => mutation.mutate()}
          className="inline-flex min-h-11 w-fit items-center justify-center rounded-lg bg-base px-5 text-body-min font-medium text-white disabled:bg-line disabled:text-[#5F594F]"
        >
          회고 받기
        </button>
      ) : (
        <button
          type="button"
          disabled={mutation.isPending}
          onClick={() => mutation.mutate()}
          className="inline-flex min-h-11 w-fit items-center justify-center rounded-lg border border-line-2 bg-white px-5 text-body-min font-medium text-ink disabled:bg-line disabled:text-[#5F594F]"
        >
          새 회고 받기
        </button>
      )}

      {mutation.isPending ? (
        <div className="flex flex-col gap-3.5 rounded-2xl bg-surface p-7">
          <p className="text-ink">기록을 천천히 읽고 있어요</p>
          <div className="h-3.5 w-[80%] animate-pulse rounded-full bg-rest" />
          <div className="h-3.5 w-full animate-pulse rounded-full bg-rest" />
        </div>
      ) : null}

      {mutation.isError ? (
        <div className="flex flex-col gap-3">
          <Note variant="sand">
            {mutation.error instanceof ApiError ? mutation.error.message : '잠시 후 다시 시도해 주세요'}
          </Note>
          <button
            type="button"
            onClick={() => mutation.mutate()}
            className="inline-flex min-h-11 w-fit items-center text-sm font-medium text-base-ink underline underline-offset-4"
          >
            다시 받기
          </button>
        </div>
      ) : null}

      {shownResult?.locked ? (
        <div className="flex flex-col items-start gap-3 rounded-2xl bg-surface p-7">
          <p className="text-ink">기록이 5개 쌓이면 첫 회고를 받아요</p>
          <p className="text-sm font-medium text-ink-2">{shownResult.currentCount ?? 0}/{shownResult.requiredCount ?? 5}</p>
          <Link to="/records/new" className="inline-flex min-h-11 items-center text-sm font-medium text-base-ink underline underline-offset-4">
            기록하기
          </Link>
        </div>
      ) : null}

      {shownResult && !shownResult.locked ? (
        <CoachResultView result={shownResult} onResultChange={(updated) => setResult(updated)} />
      ) : null}
    </Full>
  )
}
