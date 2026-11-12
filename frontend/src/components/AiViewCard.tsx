import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { AiLabel } from './AiLabel'
import { EvidenceChip } from './EvidenceChip'
import { FeedbackRow } from './FeedbackRow'
import { Note } from './Note'
import { ApiError } from '../lib/api'
import { editViewText, submitViewFeedback, type AiViewResult } from '../lib/ai'

type Props = {
  label: string
  question: string
  isLoading: boolean
  isError: boolean
  error: unknown
  data: AiViewResult | undefined
  onRetry: () => void
  onChange: (updated: AiViewResult) => void
  lockedNote?: string
  emptyNote?: string
}

/** 이 주의 한 줄·지표 해석이 함께 쓰는 생성 중·실패·LOCKED·EMPTY·결과 카드. */
export function AiViewCard({
  label,
  question,
  isLoading,
  isError,
  error,
  data,
  onRetry,
  onChange,
  lockedNote,
  emptyNote,
}: Props) {
  if (isLoading) {
    return (
      <div className="flex flex-col gap-3.5 rounded-2xl bg-surface p-6">
        <AiLabel>기록을 천천히 읽고 있어요</AiLabel>
        <div className="h-3.5 w-[80%] animate-pulse rounded-full bg-rest" />
      </div>
    )
  }

  if (isError) {
    return (
      <div className="flex flex-col gap-3">
        <Note variant="sand">{error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요'}</Note>
        <button
          type="button"
          onClick={onRetry}
          className="inline-flex min-h-11 w-fit items-center text-sm font-medium text-base-ink underline underline-offset-4"
        >
          다시 받기
        </button>
      </div>
    )
  }

  if (!data) {
    return null
  }

  if (data.locked) {
    return (
      <Note variant="calm">
        {lockedNote ?? `기록이 있는 주가 ${data.requiredWeeks ?? 2}주 모이면 해석이 열려요 (${data.currentWeeks ?? 0}/${data.requiredWeeks ?? 2})`}
      </Note>
    )
  }

  if (data.empty) {
    return <Note variant="calm">{emptyNote ?? '이번 주는 쉬어 간 주예요.'}</Note>
  }

  const hasRemaining = Boolean(data.remainingText)

  return (
    <div className="flex flex-col gap-3.5 rounded-2xl border border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_9%,white)] p-6">
      <AiLabel>{label}</AiLabel>

      <div className="flex flex-col gap-1.5">
        {hasRemaining ? <span className="text-caption-min text-ink-2">이어진 것</span> : null}
        <p className="font-serif text-lg leading-[1.55]">{data.displayText}</p>
        <div className="flex flex-wrap gap-2">
          {data.evidence.map((record) => (
            <EvidenceChip key={record.id} record={record} />
          ))}
        </div>
      </div>

      {hasRemaining ? (
        <div className="flex flex-col gap-1.5">
          <span className="text-caption-min text-ink-2">아직 남은 어려움</span>
          <p className="font-serif text-lg leading-[1.55]">{data.remainingText}</p>
          <div className="flex flex-wrap gap-2">
            {data.remainingEvidence.map((record) => (
              <EvidenceChip key={record.id} record={record} />
            ))}
          </div>
        </div>
      ) : null}

      <ViewFeedbackSection result={data} question={question} onChange={onChange} />
    </div>
  )
}

/** "맞나요?" 모델(FeedbackRow의 editArea 사용): 달라요를 누르면 바로 고치기 입력이 열린다. */
function ViewFeedbackSection({
  result,
  question,
  onChange,
}: {
  result: AiViewResult
  question: string
  onChange: (updated: AiViewResult) => void
}) {
  const [draft, setDraft] = useState('')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const feedbackMutation = useMutation({
    mutationFn: ({ id, value }: { id: number; value: 'CONFIRMED' | 'DISPUTED' }) => submitViewFeedback(id, value),
  })
  const editMutation = useMutation({
    mutationFn: ({ id, text }: { id: number; text: string }) => editViewText(id, text),
  })

  function submitFeedback(value: 'CONFIRMED' | 'DISPUTED') {
    if (!result.id) return
    setErrorMessage(null)
    const previous = result.feedback
    onChange({ ...result, feedback: value })
    feedbackMutation.mutate(
      { id: result.id, value },
      {
        onError: (mutationError) => {
          onChange({ ...result, feedback: previous })
          setErrorMessage(mutationError instanceof ApiError ? mutationError.message : '잠시 후 다시 시도해 주세요')
        },
      },
    )
  }

  const status = result.feedback === 'CONFIRMED' ? 'positive' : result.feedback === 'DISPUTED' ? 'negative' : result.feedback === 'EDITED' ? 'edited' : null

  return (
    <FeedbackRow
      question={question}
      variant="text"
      status={status}
      pending={feedbackMutation.isPending || editMutation.isPending}
      onPositive={() => submitFeedback('CONFIRMED')}
      onNegative={() => submitFeedback('DISPUTED')}
      onBeforeOpenEdit={() => setDraft(result.displayText ?? '')}
      editTriggerLabel="고치기"
      editTriggerAlwaysVisible
      editedMessage="내가 고친 문장이에요"
      errorMessage={errorMessage}
      editArea={({ markSaved, cancel }) => (
        <div className="flex flex-col gap-2">
          <input
            type="text"
            value={draft}
            maxLength={80}
            autoFocus
            onChange={(event) => setDraft(event.target.value)}
            className="h-11 w-full rounded-lg border border-input px-3 font-serif text-base text-ink"
          />
          <div className="flex gap-2">
            <button
              type="button"
              disabled={!result.id || draft.trim().length === 0 || editMutation.isPending}
              onClick={() =>
                result.id &&
                editMutation.mutate(
                  { id: result.id, text: draft.trim() },
                  {
                    onSuccess: (updated) => {
                      onChange(updated)
                      markSaved()
                    },
                    onError: (mutationError) =>
                      setErrorMessage(mutationError instanceof ApiError ? mutationError.message : '잠시 후 다시 시도해 주세요'),
                  },
                )
              }
              className="inline-flex min-h-9 items-center justify-center rounded-lg bg-base px-4 text-sm font-medium text-white disabled:bg-line disabled:text-[#5F594F]"
            >
              저장
            </button>
            <button type="button" onClick={cancel} className="inline-flex min-h-9 items-center text-sm font-medium text-ink-2">
              취소
            </button>
          </div>
        </div>
      )}
    />
  )
}
