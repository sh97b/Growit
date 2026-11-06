import { useEffect, useRef, useState } from 'react'
import { Note } from './Note'
import { ThumbsDownIcon, ThumbsUpIcon } from './icons'
import { ApiError } from '../lib/api'
import { useSubmitAnalysisFeedback } from '../hooks/useRecords'
import type { RecordAnalysis, RecordDetail } from '../lib/records'

const THANK_YOU_MESSAGE = '피드백 고마워요'
const THANK_YOU_DURATION_MS = 1000

/**
 * "이 한 줄이 도움됐나요?" 질문 줄. 누르면 즉시 선택 표시 → "피드백 고마워요"를 잠깐 보여주고
 * 스스로 접힌다. 이미 답한 기록을 다시 열면(hasInteracted=false) 메시지 없이 처음부터 접혀 있다.
 * "아쉬워요"의 안내 링크는 이 줄과 별개로, 접히지 않고 계속 보인다(디자인 4-b).
 */
export function ChangeFeedbackRow({
  record,
  analysis,
  onNotHelpfulAction,
  notHelpfulLinkLabel = '기록 상세에서 AI 결과 고치기',
}: {
  record: RecordDetail
  analysis: RecordAnalysis
  onNotHelpfulAction?: () => void
  notHelpfulLinkLabel?: string
}) {
  const feedback = useSubmitAnalysisFeedback()
  const [hasInteracted, setHasInteracted] = useState(false)
  const [showThanks, setShowThanks] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const thanksTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    return () => {
      if (thanksTimerRef.current) clearTimeout(thanksTimerRef.current)
    }
  }, [])

  function handleChangeFeedback(value: 'HELPFUL' | 'NOT_HELPFUL') {
    if (feedback.isPending) return
    setErrorMessage(null)
    setHasInteracted(true)
    setShowThanks(true)
    if (thanksTimerRef.current) clearTimeout(thanksTimerRef.current)
    thanksTimerRef.current = setTimeout(() => setShowThanks(false), THANK_YOU_DURATION_MS)
    feedback.mutate(
      { id: record.id, payload: { change: value } },
      {
        onError: (mutationError) => {
          if (thanksTimerRef.current) clearTimeout(thanksTimerRef.current)
          setHasInteracted(false)
          setShowThanks(false)
          setErrorMessage(mutationError instanceof ApiError ? mutationError.message : '잠시 후 다시 시도해 주세요')
        },
      },
    )
  }

  const answered = analysis.changeFeedback != null
  const collapsed = answered && !(hasInteracted && showThanks)

  return (
    <div className="flex flex-col gap-3">
      <div className={['grid-collapse', collapsed ? 'is-collapsed' : ''].join(' ')}>
        <div>
          <div className="flex items-center justify-between gap-3 border-t border-[color-mix(in_srgb,var(--color-base)_28%,white)] pt-2">
            {showThanks ? (
              <span className="text-sm text-ink-2">{THANK_YOU_MESSAGE}</span>
            ) : (
              <>
                <span className="text-sm text-ink">이 한 줄이 도움됐나요?</span>
                <div className="flex gap-2">
                  <button
                    type="button"
                    aria-label="도움이 됐어요"
                    aria-pressed={analysis.changeFeedback === 'HELPFUL'}
                    onClick={() => handleChangeFeedback('HELPFUL')}
                    className={[
                      'inline-flex h-11 w-11 items-center justify-center rounded-lg border text-ink hover:bg-[color-mix(in_srgb,var(--color-ink)_6%,white)]',
                      analysis.changeFeedback === 'HELPFUL'
                        ? 'border-base bg-[color-mix(in_srgb,var(--color-base)_12%,white)]'
                        : 'border-line-2 bg-white',
                    ].join(' ')}
                  >
                    <ThumbsUpIcon width={20} height={20} />
                  </button>
                  <button
                    type="button"
                    aria-label="아쉬워요"
                    aria-pressed={analysis.changeFeedback === 'NOT_HELPFUL'}
                    onClick={() => handleChangeFeedback('NOT_HELPFUL')}
                    className={[
                      'inline-flex h-11 w-11 items-center justify-center rounded-lg border text-ink hover:bg-[color-mix(in_srgb,var(--color-ink)_6%,white)]',
                      analysis.changeFeedback === 'NOT_HELPFUL'
                        ? 'border-base bg-[color-mix(in_srgb,var(--color-base)_12%,white)]'
                        : 'border-line-2 bg-white',
                    ].join(' ')}
                  >
                    <ThumbsDownIcon width={20} height={20} />
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      </div>
      {analysis.changeFeedback === 'NOT_HELPFUL' && onNotHelpfulAction ? (
        <button
          type="button"
          onClick={onNotHelpfulAction}
          className="inline-flex min-h-11 w-fit items-center gap-1.5 self-start text-sm font-medium text-base-ink underline underline-offset-4"
        >
          {notHelpfulLinkLabel}
        </button>
      ) : null}
      {errorMessage ? <Note variant="sand">{errorMessage}</Note> : null}
    </div>
  )
}
