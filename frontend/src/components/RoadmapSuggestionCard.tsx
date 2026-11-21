import { useState } from 'react'
import { Button } from './Button'
import { useAnswerRoadmapSuggestion } from '../hooks/useRecords'
import { useSetItemDone } from '../hooks/useRoadmap'
import { ApiError } from '../lib/api'
import type { RecordDetail } from '../lib/records'

/**
 * 오늘 기록이 로드맵 항목을 해 본 것으로 보일 때만 나오는 작은 카드. 자동으로 체크하지 않고 사용자가 고른다.
 * 체크한 뒤에 단계가 모두 끝났으면 차분한 한 줄을 이 자리에 남긴다(카드가 답을 받아 사라져도 그 한 줄은 유지).
 */
export function RoadmapSuggestionCard({ record }: { record: RecordDetail }) {
  const suggestion = record.analysis?.roadmapSuggestion ?? null
  const setDone = useSetItemDone(record.hobbyId)
  const answer = useAnswerRoadmapSuggestion()
  const [doneMessage, setDoneMessage] = useState<string | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  function check() {
    if (!suggestion) return
    setErrorMessage(null)
    setDone.mutate(
      { id: suggestion.itemId, done: true },
      {
        onSuccess: (roadmap) => {
          setDoneMessage(roadmap.completedStepMessage ?? '체크해 뒀어요.')
          answer.mutate({ recordId: record.id, value: 'CHECKED' })
        },
        onError: (error) => setErrorMessage(error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요'),
      },
    )
  }

  function dismiss() {
    answer.mutate({ recordId: record.id, value: 'DISMISSED' })
  }

  if (doneMessage) {
    return (
      <p role="status" className="rounded-2xl bg-surface px-5 py-4 text-body-min leading-[1.6] text-ink">
        {doneMessage}
      </p>
    )
  }
  if (!suggestion) return null

  return (
    <div className="flex flex-col gap-3 rounded-2xl bg-surface p-5">
      <p className="break-words text-body-min leading-[1.6] text-ink">
        &apos;{suggestion.text}&apos; 항목을 해 본 것 같아요. 체크해 둘까요?
      </p>
      <p className="text-caption-min text-ink-2">{suggestion.stepTitle}</p>
      <div className="flex flex-col gap-2 lg:flex-row">
        <Button type="button" size="sm" className="min-h-11 lg:min-h-9" disabled={setDone.isPending} onClick={check}>
          체크해 둘게요
        </Button>
        <Button
          type="button"
          size="sm"
          variant="secondary"
          className="min-h-11 lg:min-h-9"
          disabled={answer.isPending}
          onClick={dismiss}
        >
          아니에요
        </Button>
      </div>
      {errorMessage ? <p className="text-caption-min text-ink-2">{errorMessage}</p> : null}
    </div>
  )
}
