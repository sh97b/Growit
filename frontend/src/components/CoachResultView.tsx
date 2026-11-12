import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { AiLabel } from './AiLabel'
import { FeedbackRow } from './FeedbackRow'
import { Note } from './Note'
import { ApiError } from '../lib/api'
import {
  simplifyCoachAction,
  submitAiResultFeedback,
  submitCoachReaction,
  type CoachEvidenceItem,
  type CoachResult,
  type CoachTextItem,
} from '../lib/ai'
import { reactionLine } from '../lib/coachReaction'
import { formatMonthDay } from '../lib/date'

export function EvidenceRow({ evidence }: { evidence: CoachEvidenceItem[] }) {
  if (evidence.length === 0) return null
  return (
    <div className="flex flex-wrap gap-2">
      {evidence.map((item) => (
        <Link
          key={item.recordId}
          to={`/records/${item.recordId}`}
          title={item.content}
          className="inline-flex min-h-11 max-w-full items-center gap-2 rounded-lg border border-line-2 bg-white px-3 text-caption-min text-ink no-underline"
        >
          <span className="flex-none font-semibold text-base-ink">{formatMonthDay(item.recordDate)}</span>
          <span className="min-w-0 overflow-hidden text-ellipsis whitespace-nowrap text-ink-2">{item.content}</span>
        </Link>
      ))}
    </div>
  )
}

function TextItemBlock({ title, items }: { title: string; items: CoachTextItem[] }) {
  if (items.length === 0) return null
  return (
    <div className="flex flex-col gap-2.5">
      <h2 className="text-card-title">{title}</h2>
      {items.map((item, index) => (
        <div key={index} className="flex flex-col gap-2 rounded-xl bg-surface p-4">
          <p className="text-ink">{item.text}</p>
          <EvidenceRow evidence={item.evidence} />
        </div>
      ))}
    </div>
  )
}

/**
 * 다음 작은 행동(또는 복귀 행동) 카드. 해봤어요·오늘은 쉴게요는 반응만 남기는 낙관적 갱신이고,
 * 너무 어려워요는 실제로 더 작은 행동을 받아오는 호출(POST .../simplify)이라 결과가 통째로 바뀐다.
 * 그래서 상태를 이 컴포넌트 안에 두지 않고 result 전체를 부모에게서 받아 onResultChange로만 바꾼다.
 */
export function CoachActionCard({
  result,
  comeback,
  onResultChange,
}: {
  result: CoachResult
  comeback: boolean
  onResultChange: (updated: CoachResult) => void
}) {
  const action = comeback ? result.comebackAction : result.nextAction
  const [simplifyError, setSimplifyError] = useState<string | null>(null)
  const reactionPendingRef = useRef(false)
  const simplifyPendingRef = useRef(false)

  const reactionMutation = useMutation({
    mutationFn: ({ value }: { value: 'TRIED' | 'REST_TODAY' }) => submitCoachReaction(result.id!, value),
  })
  const simplifyMutation = useMutation({
    mutationFn: () => simplifyCoachAction(result.id!),
  })

  if (!action) return null

  function handleReaction(value: 'TRIED' | 'REST_TODAY') {
    if (!result.id || reactionPendingRef.current) return
    reactionPendingRef.current = true
    const previous = result.reaction
    onResultChange({ ...result, reaction: value })
    reactionMutation.mutate(
      { value },
      {
        onSettled: () => {
          reactionPendingRef.current = false
        },
        onError: () => onResultChange({ ...result, reaction: previous }),
      },
    )
  }

  function handleSimplify() {
    if (!result.id || simplifyPendingRef.current) return
    simplifyPendingRef.current = true
    setSimplifyError(null)
    simplifyMutation.mutate(undefined, {
      onSuccess: (updated) => onResultChange(updated),
      onError: (error) => {
        if (error instanceof ApiError && error.code === 'AI_ALREADY_SIMPLIFIED') {
          // 이미 한 번 줄인 결과에서 또 너무 어렵다고 한 경우: 더 줄이는 대신 쉬어가는 상태로 끝맺는다.
          setSimplifyError(error.message)
          submitCoachReaction(result.id!, 'TOO_HARD').then((updated) => onResultChange(updated))
          return
        }
        setSimplifyError(error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요')
      },
      onSettled: () => {
        simplifyPendingRef.current = false
      },
    })
  }

  return (
    <div className="flex flex-col gap-3.5 rounded-2xl border border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_9%,white)] p-7">
      <AiLabel>{comeback ? '가볍게 다시 시작하려면' : '다음 작은 행동'}</AiLabel>
      <p className="font-serif text-lg leading-[1.55] lg:text-[1.5rem]">{action.title}</p>
      <span className="text-sm text-ink-2">{action.minutes}분 · {action.reason}</span>
      <EvidenceRow evidence={action.evidence} />
      {result.reaction === 'TRIED' || result.reaction === 'REST_TODAY' ? (
        <p className="text-sm text-ink-2">{reactionLine(result.reaction, result.id ?? 0)}</p>
      ) : (
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => handleReaction('TRIED')}
            className="inline-flex min-h-11 items-center justify-center rounded-lg border border-line-2 bg-white px-4 text-sm font-medium text-ink hover:bg-[color-mix(in_srgb,var(--color-ink)_6%,white)]"
          >
            해봤어요
          </button>
          <button
            type="button"
            onClick={() => handleReaction('REST_TODAY')}
            className="inline-flex min-h-11 items-center justify-center rounded-lg border border-line-2 bg-white px-4 text-sm font-medium text-ink hover:bg-[color-mix(in_srgb,var(--color-ink)_6%,white)]"
          >
            오늘은 쉴게요
          </button>
          <button
            type="button"
            onClick={handleSimplify}
            className="inline-flex min-h-11 items-center justify-center rounded-lg border border-line-2 bg-white px-4 text-sm font-medium text-ink hover:bg-[color-mix(in_srgb,var(--color-ink)_6%,white)]"
          >
            너무 어려워요
          </button>
        </div>
      )}
      {simplifyError ? <Note variant="sand">{simplifyError}</Note> : null}
    </div>
  )
}

export function CoachResultView({
  result,
  onResultChange,
}: {
  result: CoachResult
  onResultChange: (updated: CoachResult) => void
}) {
  const feedbackPendingRef = useRef(false)
  const [feedbackError, setFeedbackError] = useState<string | null>(null)
  const feedbackMutation = useMutation({
    mutationFn: ({ value }: { value: 'HELPFUL' | 'NOT_HELPFUL' }) =>
      submitAiResultFeedback<CoachResult>(result.id!, value),
  })

  function handleFeedback(value: 'HELPFUL' | 'NOT_HELPFUL') {
    if (!result.id || feedbackPendingRef.current) return
    feedbackPendingRef.current = true
    setFeedbackError(null)
    const previous = result.feedback
    onResultChange({ ...result, feedback: value })
    feedbackMutation.mutate(
      { value },
      {
        onError: (mutationError) => {
          onResultChange({ ...result, feedback: previous })
          setFeedbackError(mutationError instanceof ApiError ? mutationError.message : '잠시 후 다시 시도해 주세요')
        },
        onSettled: () => {
          feedbackPendingRef.current = false
        },
      },
    )
  }

  return (
    <div className="flex flex-col gap-5 lg:grid lg:grid-cols-2 lg:items-start lg:gap-8">
      <div className="flex flex-col gap-5">
        {result.startDate && result.endDate ? (
          <span className="text-caption-min font-semibold text-ink-2">
            {formatMonthDay(result.startDate)} – {formatMonthDay(result.endDate)}
          </span>
        ) : null}

        <div className="flex flex-col gap-3.5 rounded-2xl bg-surface p-7">
          <AiLabel>AI 회고 한 줄</AiLabel>
          <p className="font-serif text-lg leading-[1.55] lg:text-[1.5rem]">{result.periodSummary}</p>
          <FeedbackRow
            question="이 회고가 도움됐나요?"
            status={result.feedback === 'HELPFUL' ? 'positive' : result.feedback === 'NOT_HELPFUL' ? 'negative' : null}
            pending={feedbackMutation.isPending}
            onPositive={() => handleFeedback('HELPFUL')}
            onNegative={() => handleFeedback('NOT_HELPFUL')}
            errorMessage={feedbackError}
          />
        </div>

        <TextItemBlock title="새로 해 본 것 · 나아진 점" items={result.changes} />
        <TextItemBlock title="아직 남은 어려움" items={result.repeatedDifficulties} />
      </div>

      <div className="flex flex-col gap-5">
        <CoachActionCard result={result} comeback={result.mode === 'COMEBACK'} onResultChange={onResultChange} />

        {result.recordsRead != null ? (
          <p className="text-sm text-ink-2">기록 {result.recordsRead}개를 읽었어요</p>
        ) : null}
      </div>
    </div>
  )
}
