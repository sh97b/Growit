import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { AiLabel } from '../components/AiLabel'
import { EvidenceChip } from '../components/EvidenceChip'
import { FeedbackRow } from '../components/FeedbackRow'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
import { RecordPairCards } from '../components/RecordPairCards'
import { useCurrentUser } from '../hooks/useCurrentUser'
import { useHobbies } from '../hooks/useHobbies'
import { ApiError } from '../lib/api'
import { fetchCompare, submitAiResultFeedback, type CompareResult, type CompareTo } from '../lib/ai'
import { formatDurationMinutes, formatMonthDay } from '../lib/date'
import { pickClass } from '../lib/recordForm'

const PERIODS: { value: CompareTo; label: string }[] = [
  { value: '1W', label: '1주 전' },
  { value: '2W', label: '2주 전' },
  { value: '1M', label: '한 달 전' },
  { value: '3M', label: '3개월 전' },
]

export function ChangesPage() {
  const { data: currentUser, isLoading: userLoading } = useCurrentUser()
  const { data: activeHobbies, isLoading: hobbiesLoading } = useHobbies(false)
  const [hobbyId, setHobbyId] = useState<number | null>(null)
  const [compareTo, setCompareTo] = useState<CompareTo>('1M')
  const [result, setResult] = useState<CompareResult | null>(null)
  const [feedbackError, setFeedbackError] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: () => fetchCompare({ hobbyId: effectiveHobbyId!, compareTo }),
    onSuccess: (data) => setResult(data),
  })

  const feedbackMutation = useMutation({
    mutationFn: ({ id, value }: { id: number; value: 'HELPFUL' | 'NOT_HELPFUL' }) =>
      submitAiResultFeedback<CompareResult>(id, value),
  })

  if (userLoading || hobbiesLoading) {
    return <Loading />
  }

  // hobbyId가 null이거나(처음 진입) 더 이상 활성 분야 목록에 없으면(분야가 보관되는 등) 첫 활성 분야로
  // 보정한다. 화면에 보이는 선택과 요청에 실제로 쓰이는 값이 반드시 같은 값에서 나오게 한다.
  const hobby = activeHobbies?.find((h) => h.id === hobbyId) ?? activeHobbies?.[0] ?? null
  const effectiveHobbyId = hobby?.id ?? null

  if (!currentUser?.aiEnabled) {
    return (
      <div className="flex flex-col gap-4">
        <h1 className="text-page-title">나의 변화</h1>
        <Note variant="calm">
          <span>
            AI 정리가 꺼져 있어요. 켜면 기록을 비교해 달라진 점을 보여드려요.{' '}
            <Link to="/settings/ai" className="font-medium text-base-ink underline underline-offset-4">
              켜기
            </Link>
          </span>
        </Note>
      </div>
    )
  }

  if (!activeHobbies || activeHobbies.length === 0) {
    return (
      <div className="flex flex-col gap-4">
        <h1 className="text-page-title">나의 변화</h1>
        <Note variant="calm">활동 분야를 먼저 만들어 주세요.</Note>
      </div>
    )
  }

  function handleFeedback(value: 'HELPFUL' | 'NOT_HELPFUL') {
    if (!result?.id || feedbackMutation.isPending) return
    setFeedbackError(null)
    setResult((prev) => (prev ? { ...prev, feedback: value } : prev))
    feedbackMutation.mutate(
      { id: result.id, value },
      {
        onError: (mutationError) => {
          setResult((prev) => (prev ? { ...prev, feedback: result.feedback } : prev))
          setFeedbackError(mutationError instanceof ApiError ? mutationError.message : '잠시 후 다시 시도해 주세요')
        },
      },
    )
  }

  const selectors = (
    <>
      <div className="flex flex-wrap gap-2">
        {activeHobbies.map((h) => (
          <button
            key={h.id}
            type="button"
            aria-pressed={effectiveHobbyId === h.id}
            onClick={() => {
              setHobbyId(h.id)
              setResult(null)
            }}
            className={pickClass(effectiveHobbyId === h.id)}
          >
            {h.name}
          </button>
        ))}
      </div>

      <div className="flex flex-wrap gap-2">
        {PERIODS.map((period) => (
          <button
            key={period.value}
            type="button"
            aria-pressed={compareTo === period.value}
            onClick={() => {
              setCompareTo(period.value)
              setResult(null)
            }}
            className={pickClass(compareTo === period.value)}
          >
            {period.label}
          </button>
        ))}
      </div>
    </>
  )

  return (
    <div className="flex flex-col gap-5">
      <h1 className="text-page-title">나의 변화</h1>

      <div className="flex flex-col gap-5 lg:flex-row lg:flex-wrap lg:items-center lg:justify-between">{selectors}</div>

        {!result ? (
        <button
          type="button"
          disabled={mutation.isPending || effectiveHobbyId == null}
          onClick={() => mutation.mutate()}
          className="inline-flex min-h-11 w-fit items-center justify-center rounded-lg bg-base px-5 text-[15px] font-medium text-white disabled:bg-line disabled:text-[#5F594F]"
        >
          변화 보기
        </button>
      ) : null}

      {mutation.isPending ? (
        <div className="flex flex-col gap-3.5 rounded-2xl bg-surface p-7">
          <AiLabel>기록을 천천히 읽고 있어요</AiLabel>
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
          <p className="text-ink">기록이 3개 쌓이면 비교가 열려요</p>
          <p className="text-sm font-medium text-ink-2">{result.currentCount ?? 0}/{result.requiredCount ?? 3}</p>
          <Link to="/records/new" className="inline-flex min-h-11 items-center text-sm font-medium text-base-ink underline underline-offset-4">
            기록하기
          </Link>
        </div>
      ) : null}

      {result && !result.locked ? (
        <div className="flex flex-col gap-5">
          <RecordPairCards
            left={
              result.pastRecord
                ? { id: result.pastRecord.id, label: `${result.pastDate ? formatMonthDay(result.pastDate) : ''} 전후`, content: result.pastRecord.content }
                : null
            }
            right={
              result.currentRecord
                ? { id: result.currentRecord.id, label: `최근 · ${result.currentDate ? formatMonthDay(result.currentDate) : ''}`, content: result.currentRecord.content }
                : null
            }
          />

          <div className="flex flex-col gap-3.5 rounded-2xl border border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_9%,white)] p-7">
            <AiLabel>변화 해석</AiLabel>
            <p className="font-serif text-lg leading-[1.55]">{result.interpretation}</p>
            <div className="flex flex-wrap gap-2">
              {result.pastRecord ? <EvidenceChip record={result.pastRecord} /> : null}
              {result.currentRecord ? <EvidenceChip record={result.currentRecord} /> : null}
            </div>
            <FeedbackRow
              question="이 해석이 맞나요?"
              status={result.feedback === 'HELPFUL' ? 'positive' : result.feedback === 'NOT_HELPFUL' ? 'negative' : null}
              pending={feedbackMutation.isPending}
              onPositive={() => handleFeedback('HELPFUL')}
              onNegative={() => handleFeedback('NOT_HELPFUL')}
              errorMessage={feedbackError}
            />
          </div>

          {result.metrics ? (
            <div className="flex flex-col gap-4">
              <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
                <div className="flex flex-col gap-1 rounded-xl bg-surface p-4">
                  <span className="text-sm text-ink-2">기록한 날</span>
                  <span className="font-serif text-lg text-ink">
                    {result.metrics.recordedDays.past}일 → {result.metrics.recordedDays.current}일
                  </span>
                </div>
                {result.metrics.totalMinutes.past === 0 && result.metrics.totalMinutes.current === 0 ? null : (
                  <div className="flex flex-col gap-1 rounded-xl bg-surface p-4">
                    <span className="text-sm text-ink-2">쌓인 시간</span>
                    <span className="font-serif text-lg text-ink">
                      {result.metrics.totalMinutes.past === 0 ? '기록 안 함' : formatDurationMinutes(result.metrics.totalMinutes.past)}
                      {' → '}
                      {result.metrics.totalMinutes.current === 0 ? '기록 안 함' : formatDurationMinutes(result.metrics.totalMinutes.current)}
                    </span>
                  </div>
                )}
              </div>
              {result.metrics.topTags && result.metrics.topTags.length > 0 ? (
                <div className="flex flex-col gap-2 rounded-xl bg-surface p-4">
                  <span className="text-sm text-ink-2">자주 나온 주제</span>
                  <div className="flex flex-wrap gap-2">
                    {result.metrics.topTags.map((topic) => (
                      <span
                        key={topic.tag}
                        className="inline-flex h-7 items-center rounded-md bg-tag-topic-bg px-2.5 text-sm font-medium text-tag-topic-fg"
                      >
                        {topic.tag} · {topic.count}
                      </span>
                    ))}
                  </div>
                </div>
              ) : null}
            </div>
          ) : null}

          <button
            type="button"
            onClick={() => setResult(null)}
            className="inline-flex min-h-11 w-fit items-center text-sm font-medium text-base-ink underline underline-offset-4"
          >
            다시 비교하기
          </button>
        </div>
      ) : null}
    </div>
  )
}
