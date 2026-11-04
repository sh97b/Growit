import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { AiLabel } from './AiLabel'
import { Note } from './Note'
import { ThumbsDownIcon, ThumbsUpIcon } from './icons'
import {
  submitAiResultFeedback,
  submitCoachReaction,
  type CoachAction,
  type CoachEvidenceItem,
  type CoachResult,
  type CoachTextItem,
} from '../lib/ai'
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
          className="inline-flex min-h-11 max-w-full items-center gap-2 rounded-lg border border-line-2 bg-white px-3 text-[13px] text-ink no-underline"
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
      <h2 className="font-serif text-lg font-semibold">{title}</h2>
      {items.map((item, index) => (
        <div key={index} className="flex flex-col gap-2 rounded-xl bg-surface p-4">
          <p className="text-ink">{item.text}</p>
          <EvidenceRow evidence={item.evidence} />
        </div>
      ))}
    </div>
  )
}

function ActionBlock({ action, coachId, mode }: { action: CoachAction; coachId: number | null; mode: string | null }) {
  const [reaction, setReaction] = useState<CoachResult['reaction']>(null)
  const reactionMutation = useMutation({
    mutationFn: ({ value }: { value: 'TRIED' | 'REST_TODAY' | 'TOO_HARD' }) => submitCoachReaction(coachId!, value),
  })

  function handleReaction(value: 'TRIED' | 'REST_TODAY' | 'TOO_HARD') {
    if (!coachId || reactionMutation.isPending) return
    const previous = reaction
    setReaction(value)
    reactionMutation.mutate({ value }, { onError: () => setReaction(previous) })
  }

  return (
    <div className="flex flex-col gap-3.5 rounded-2xl border border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_9%,white)] p-7">
      <AiLabel>{mode === 'COMEBACK' ? '가볍게 다시 시작하려면' : '다음 작은 행동'}</AiLabel>
      <p className="font-serif text-lg leading-[1.55] lg:text-[24px]">{action.title}</p>
      <span className="text-sm text-ink-2">{action.minutes}분 · {action.reason}</span>
      <EvidenceRow evidence={action.evidence} />
      {reaction ? (
        <p className="text-sm text-ink-2">
          {reaction === 'TRIED' ? '해봤어요' : reaction === 'REST_TODAY' ? '오늘은 쉴게요' : '더 작게 바꿨어요'}
        </p>
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
            onClick={() => handleReaction('TOO_HARD')}
            className="inline-flex min-h-11 items-center justify-center rounded-lg border border-line-2 bg-white px-4 text-sm font-medium text-ink hover:bg-[color-mix(in_srgb,var(--color-ink)_6%,white)]"
          >
            너무 어려워요
          </button>
        </div>
      )}
    </div>
  )
}

export function CoachResultView({ result }: { result: CoachResult }) {
  const [feedback, setFeedback] = useState(result.feedback)
  const feedbackMutation = useMutation({
    mutationFn: ({ value }: { value: 'HELPFUL' | 'NOT_HELPFUL' }) =>
      submitAiResultFeedback<CoachResult>(result.id!, value),
  })

  function handleFeedback(value: 'HELPFUL' | 'NOT_HELPFUL') {
    if (!result.id || feedbackMutation.isPending) return
    const previous = feedback
    setFeedback(value)
    feedbackMutation.mutate({ value }, { onError: () => setFeedback(previous) })
  }

  const action = result.mode === 'COMEBACK' ? result.comebackAction : result.nextAction

  return (
    <div className="flex flex-col gap-5">
      {result.startDate && result.endDate ? (
        <span className="text-[13px] font-semibold text-ink-2">
          {formatMonthDay(result.startDate)} – {formatMonthDay(result.endDate)}
        </span>
      ) : null}

      <div className="flex flex-col gap-3.5 rounded-2xl bg-surface p-7">
        <AiLabel>AI 회고 한 줄</AiLabel>
        <p className="font-serif text-lg leading-[1.55] lg:text-[24px]">{result.periodSummary}</p>
        <div className="flex items-center justify-between gap-3 border-t border-line pt-2">
          <span className="text-sm text-ink">이 회고가 도움됐나요?</span>
          <div className="flex gap-2">
            <button
              type="button"
              aria-label="도움이 됐어요"
              aria-pressed={feedback === 'HELPFUL'}
              onClick={() => handleFeedback('HELPFUL')}
              className={[
                'inline-flex h-11 w-11 items-center justify-center rounded-lg border text-ink hover:bg-[color-mix(in_srgb,var(--color-ink)_6%,white)]',
                feedback === 'HELPFUL'
                  ? 'border-base bg-[color-mix(in_srgb,var(--color-base)_12%,white)]'
                  : 'border-line-2 bg-white',
              ].join(' ')}
            >
              <ThumbsUpIcon width={20} height={20} />
            </button>
            <button
              type="button"
              aria-label="아쉬워요"
              aria-pressed={feedback === 'NOT_HELPFUL'}
              onClick={() => handleFeedback('NOT_HELPFUL')}
              className={[
                'inline-flex h-11 w-11 items-center justify-center rounded-lg border text-ink hover:bg-[color-mix(in_srgb,var(--color-ink)_6%,white)]',
                feedback === 'NOT_HELPFUL'
                  ? 'border-base bg-[color-mix(in_srgb,var(--color-base)_12%,white)]'
                  : 'border-line-2 bg-white',
              ].join(' ')}
            >
              <ThumbsDownIcon width={20} height={20} />
            </button>
          </div>
        </div>
      </div>

      <TextItemBlock title="새로 해 본 것 · 나아진 점" items={result.changes} />
      <TextItemBlock title="아직 남은 어려움" items={result.repeatedDifficulties} />

      {action ? <ActionBlock action={action} coachId={result.id} mode={result.mode} /> : null}

      {result.recordsRead != null ? (
        <p className="text-sm text-ink-2">기록 {result.recordsRead}개를 읽었어요</p>
      ) : null}

      {feedbackMutation.isError ? <Note variant="sand">잠시 후 다시 시도해 주세요</Note> : null}
    </div>
  )
}
