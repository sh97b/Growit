import { useState } from 'react'
import { FeedbackRow } from './FeedbackRow'
import { ApiError } from '../lib/api'
import { useSubmitAnalysisFeedback } from '../hooks/useRecords'
import type { RecordAnalysis, RecordDetail } from '../lib/records'

/** "이 한 줄이 도움됐나요?" 질문 줄. 공용 FeedbackRow(U4)의 "도움됐나요?" 모델(editArea 없음)을 쓴다. */
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

  const status = analysis.changeFeedback === 'HELPFUL' ? 'positive' : analysis.changeFeedback === 'NOT_HELPFUL' ? 'negative' : null

  return (
    <FeedbackRow
      question="이 한 줄이 도움됐나요?"
      status={status}
      pending={feedback.isPending}
      onPositive={() => submit('HELPFUL')}
      onNegative={() => submit('NOT_HELPFUL')}
      negativeLink={onNotHelpfulAction ? { label: notHelpfulLinkLabel, onAction: onNotHelpfulAction } : undefined}
      errorMessage={errorMessage}
    />
  )
}
