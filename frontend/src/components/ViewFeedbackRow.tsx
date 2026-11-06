import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { editViewText, submitViewFeedback, type AiViewResult } from '../lib/ai'
import { pickClass } from '../lib/recordForm'

type Props = {
  result: AiViewResult
  question: string
  onChange: (updated: AiViewResult) => void
}

/** "맞아요/달라요"는 배타적이고 낙관적으로 갱신된다. "달라요"를 누르면 고치기 입력이 함께 열린다. */
export function ViewFeedbackRow({ result, question, onChange }: Props) {
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState('')

  const feedbackMutation = useMutation({
    mutationFn: ({ id, value }: { id: number; value: 'CONFIRMED' | 'DISPUTED' }) => submitViewFeedback(id, value),
  })

  const editMutation = useMutation({
    mutationFn: ({ id, text }: { id: number; text: string }) => editViewText(id, text),
    onSuccess: (updated) => {
      onChange(updated)
      setEditing(false)
    },
  })

  function handleFeedback(value: 'CONFIRMED' | 'DISPUTED') {
    if (!result.id) return
    const previousFeedback = result.feedback
    onChange({ ...result, feedback: value })
    if (value === 'DISPUTED') {
      setDraft(result.displayText ?? '')
      setEditing(true)
    }
    feedbackMutation.mutate(
      { id: result.id, value },
      { onError: () => onChange({ ...result, feedback: previousFeedback }) },
    )
  }

  return (
    <div className="flex flex-col gap-2 border-t border-[color-mix(in_srgb,var(--color-base)_28%,white)] pt-3">
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-sm text-ink">{question}</span>
        <button
          type="button"
          aria-pressed={result.feedback === 'CONFIRMED'}
          onClick={() => handleFeedback('CONFIRMED')}
          className={pickClass(result.feedback === 'CONFIRMED')}
        >
          맞아요
        </button>
        <button
          type="button"
          aria-pressed={result.feedback === 'DISPUTED' || result.feedback === 'EDITED'}
          onClick={() => handleFeedback('DISPUTED')}
          className={pickClass(result.feedback === 'DISPUTED' || result.feedback === 'EDITED')}
        >
          달라요
        </button>
        {!editing ? (
          <button
            type="button"
            onClick={() => {
              setDraft(result.displayText ?? '')
              setEditing(true)
            }}
            className="inline-flex min-h-11 items-center text-sm font-medium text-base-ink underline underline-offset-4"
          >
            고치기
          </button>
        ) : null}
      </div>

      {editing ? (
        <div className="flex flex-col gap-2">
          <input
            type="text"
            value={draft}
            maxLength={80}
            onChange={(e) => setDraft(e.target.value)}
            className="h-11 w-full rounded-lg border border-input px-3 font-serif text-base text-ink"
          />
          <div className="flex gap-2">
            <button
              type="button"
              disabled={!result.id || draft.trim().length === 0 || editMutation.isPending}
              onClick={() => result.id && editMutation.mutate({ id: result.id, text: draft.trim() })}
              className="inline-flex min-h-9 items-center justify-center rounded-lg bg-base px-4 text-sm font-medium text-white disabled:bg-line disabled:text-[#5F594F]"
            >
              저장
            </button>
            <button
              type="button"
              onClick={() => setEditing(false)}
              className="inline-flex min-h-9 items-center text-sm font-medium text-ink-2"
            >
              취소
            </button>
          </div>
        </div>
      ) : result.feedback === 'EDITED' ? (
        <p className="text-sm text-ink-2">내가 고친 문장이에요</p>
      ) : null}
    </div>
  )
}
