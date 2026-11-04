import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { CoachResultView } from '../components/CoachResultView'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
import { useCurrentUser } from '../hooks/useCurrentUser'
import { ApiError } from '../lib/api'
import { fetchCoach, type CoachPeriod, type CoachResult } from '../lib/ai'
import { pickClass } from '../lib/recordForm'

const PERIODS: { value: CoachPeriod; label: string }[] = [
  { value: 'THIS_WEEK', label: '이번 주' },
  { value: '2W', label: '최근 2주' },
  { value: '1M', label: '한 달' },
]

export function CoachPage() {
  const { data: currentUser, isLoading: userLoading } = useCurrentUser()
  const [period, setPeriod] = useState<CoachPeriod>('2W')
  const [result, setResult] = useState<CoachResult | null>(null)

  const mutation = useMutation({
    mutationFn: () => fetchCoach({ period }),
    onSuccess: (data) => setResult(data),
  })

  if (userLoading) {
    return <Loading />
  }

  if (!currentUser?.aiEnabled) {
    return (
      <div className="flex flex-col gap-4">
        <h1 className="font-serif text-xl font-semibold lg:text-[26px]">AI 회고</h1>
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

  return (
    <div className="flex flex-col gap-5 lg:max-w-[920px]">
      <div className="flex items-center justify-between gap-3">
        <h1 className="font-serif text-xl font-semibold lg:text-[26px]">AI 회고</h1>
        <Link to="/coach/history" className="text-sm font-medium text-base-ink underline underline-offset-4">
          이전 회고 목록
        </Link>
      </div>

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

      {!result ? (
        <button
          type="button"
          disabled={mutation.isPending}
          onClick={() => mutation.mutate()}
          className="inline-flex min-h-11 w-fit items-center justify-center rounded-lg bg-base px-5 text-[15px] font-medium text-white disabled:bg-line disabled:text-[#5F594F]"
        >
          회고 받기
        </button>
      ) : null}

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

      {result?.locked ? (
        <div className="flex flex-col items-start gap-3 rounded-2xl bg-surface p-7">
          <p className="text-ink">기록이 5개 쌓이면 첫 회고를 받아요</p>
          <p className="text-sm font-medium text-ink-2">{result.currentCount ?? 0}/{result.requiredCount ?? 5}</p>
          <Link to="/records/new" className="inline-flex min-h-11 items-center text-sm font-medium text-base-ink underline underline-offset-4">
            기록하기
          </Link>
        </div>
      ) : null}

      {result && !result.locked ? <CoachResultView result={result} /> : null}
    </div>
  )
}
