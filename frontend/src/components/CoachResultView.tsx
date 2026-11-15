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

/** P2: 회고 상세의 섹션 카드 한 겹(흰 배경, 옅은 테두리, 모서리 토큰, 안쪽 여백 1.25rem). */
export const RESULT_CARD_CLASS = 'rounded-2xl border border-line bg-white p-5'

/*
 * 항목은 카드 안에서 배경 없는 평문(16px, 줄 높이 1.7)과 그 아래 근거 칩 한 줄로만 구성한다(카드
 * 안에 또 테두리 카드를 두지 않는다). 섹션 제목은 섹션 제목 토큰(20px)을 쓴다.
 */
function TextItemBlock({ title, items }: { title: string; items: CoachTextItem[] }) {
  if (items.length === 0) return null
  return (
    <div className={`flex flex-col gap-3 ${RESULT_CARD_CLASS}`}>
      <h2 className="text-section-title">{title}</h2>
      {items.map((item, index) => (
        <div key={index} className="flex flex-col gap-2">
          <p className="text-body-min leading-[1.7] text-ink">{item.text}</p>
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
  hideReactions = false,
}: {
  result: CoachResult
  comeback: boolean
  onResultChange: (updated: CoachResult) => void
  /** 홈의 만료(EXPIRED) 회고 카드처럼, 행동 내용은 보여주되 반응 버튼만 숨길 때 쓴다(P1 5단계). */
  hideReactions?: boolean
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
    <div className="flex flex-col gap-3.5 rounded-2xl border border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_9%,white)] p-5">
      <AiLabel>{comeback ? '가볍게 다시 시작하려면' : '다음 작은 행동'}</AiLabel>
      <p className="text-section-title">{action.title}</p>
      <span className="text-caption-min text-ink-2">{action.minutes}분 · {action.reason}</span>
      <EvidenceRow evidence={action.evidence} />
      {hideReactions ? null : result.reaction === 'TRIED' || result.reaction === 'REST_TODAY' ? (
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

  // P1 4단계: 한 열 문서 구성(최대 44rem) — 메타 줄 → AI 회고 한 줄(카드 배경 없음) → 섹션들 →
  // 다음 작은 행동(유일한 강조 카드) → 맨 아래 피드백. O1(C.5)의 세로 배치 순서를 그대로 쓰되
  // 장식적인 큰 글씨와 카드 안의 카드를 없애 가독성을 높인다.
  return (
    <div className="flex flex-col gap-4 lg:max-w-[48rem]">
      <div className="flex flex-wrap items-center gap-2 text-caption-min text-ink-2">
        {result.startDate && result.endDate ? (
          <span>
            {formatMonthDay(result.startDate)} – {formatMonthDay(result.endDate)} · 전체 분야
            {result.recordsRead != null ? ` · 기록 ${result.recordsRead}개 읽음` : ''}
          </span>
        ) : null}
        {result.isNew ? (
          <span className="inline-flex h-5 items-center rounded-md bg-[color-mix(in_srgb,var(--color-base)_16%,white)] px-1.5 text-[0.6875rem] font-medium text-base-ink">
            새로 도착
          </span>
        ) : null}
      </div>

      <p className={`font-serif text-section-title leading-[1.7] text-ink ${RESULT_CARD_CLASS}`}>{result.periodSummary}</p>

      <TextItemBlock title="새로 해 본 것 · 나아진 점" items={result.changes} />
      <TextItemBlock title="아직 남은 어려움" items={result.repeatedDifficulties} />

      <CoachActionCard result={result} comeback={result.mode === 'COMEBACK'} onResultChange={onResultChange} />

      <FeedbackRow
        question="이 회고가 도움됐나요?"
        status={result.feedback === 'HELPFUL' ? 'positive' : result.feedback === 'NOT_HELPFUL' ? 'negative' : null}
        pending={feedbackMutation.isPending}
        onPositive={() => handleFeedback('HELPFUL')}
        onNegative={() => handleFeedback('NOT_HELPFUL')}
        errorMessage={feedbackError}
      />
    </div>
  )
}
