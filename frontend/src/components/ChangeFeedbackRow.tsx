import { useState } from 'react'
import { ThumbsDownIcon, ThumbsUpIcon } from './icons'
import { ApiError } from '../lib/api'
import { useSubmitAnalysisFeedback } from '../hooks/useRecords'
import type { RecordAnalysis, RecordDetail } from '../lib/records'

const buttonBase =
  'inline-flex h-10 w-10 items-center justify-center rounded-lg border text-ink-2 hover:bg-[color-mix(in_srgb,var(--color-ink)_6%,white)] disabled:opacity-60'
const selectedClass = 'border-base bg-[color-mix(in_srgb,var(--color-base)_12%,white)] text-ink'
const idleClass = 'border-transparent bg-transparent'

/** 카드 오른쪽 아래 모서리에 두는 작은 👍/👎 아이콘 버튼 두 개. 질문 문구와 구분선은 두지 않는다. */
export function ChangeFeedbackRow({ record, analysis }: { record: RecordDetail; analysis: RecordAnalysis }) {
  const feedback = useSubmitAnalysisFeedback()
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  function submit(value: 'HELPFUL' | 'NOT_HELPFUL') {
    setErrorMessage(null)
    feedback.mutate(
      { id: record.id, payload: { change: value } },
      {
        onError: (mutationError) => {
          setErrorMessage(mutationError instanceof ApiError ? mutationError.message : '잠시 후 다시 시도해 주세요')
        },
      },
    )
  }

  const helpful = analysis.changeFeedback === 'HELPFUL'
  const notHelpful = analysis.changeFeedback === 'NOT_HELPFUL'

  return (
    <div className="flex flex-col items-end gap-1">
      <div className="flex items-center gap-1">
        <button
          type="button"
          aria-label="도움이 됐어요"
          aria-pressed={helpful}
          disabled={feedback.isPending}
          onClick={() => submit('HELPFUL')}
          className={[buttonBase, helpful ? selectedClass : idleClass].join(' ')}
        >
          <ThumbsUpIcon width={18} height={18} />
        </button>
        <button
          type="button"
          aria-label="도움이 안 됐어요"
          aria-pressed={notHelpful}
          disabled={feedback.isPending}
          onClick={() => submit('NOT_HELPFUL')}
          className={[buttonBase, notHelpful ? selectedClass : idleClass].join(' ')}
        >
          <ThumbsDownIcon width={18} height={18} />
        </button>
      </div>
      {errorMessage ? <p className="text-caption-min text-ink-2">{errorMessage}</p> : null}
    </div>
  )
}
