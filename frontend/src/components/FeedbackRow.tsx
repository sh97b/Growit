import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Note } from './Note'
import { ThumbsDownIcon, ThumbsUpIcon } from './icons'

const MESSAGE_DURATION_MS = 1000

export type FeedbackRowStatus = 'positive' | 'negative' | 'edited' | null

type EditHelpers = {
  markSaved: () => void
  cancel: () => void
}

type Props = {
  question: string
  status: FeedbackRowStatus
  pending?: boolean
  onPositive: () => void
  onNegative: () => void
  /** 아이콘(엄지)인지 글자 버튼("맞아요"/"달라요")인지. 기본 thumbs. */
  variant?: 'thumbs' | 'text'
  positiveLabel?: string
  negativeLabel?: string
  thanksMessage?: string
  savedMessage?: string
  /**
   * 있으면 "맞나요?" 모델: 달라요를 누르면 즉시 고치기 영역이 열리고(접히지 않음),
   * 저장(markSaved)해야 "고쳤어요" 후 접힌다. 없으면 "도움됐나요?" 모델: 맞아요·아쉬워요
   * 둘 다 바로 고마워요 메시지 후 접힌다.
   */
  editArea?: (helpers: EditHelpers) => ReactNode
  /** 고치기 영역을 열기 전에(달라요·고치기·다시 고치기 모두) 초안을 채우는 등 준비할 일. */
  onBeforeOpenEdit?: () => void
  /** "맞나요?" 모델에서, 아직 답하지 않았어도 바로 고칠 수 있는 항상 보이는 버튼. */
  editTriggerLabel?: string
  /** editTriggerLabel을 status==='edited'일 때도 계속 보여 줄지(기본은 그때 숨기고 editedActionLabel로 대체). */
  editTriggerAlwaysVisible?: boolean
  /** "맞나요?" 모델에서 status==='edited'일 때 접힌 줄 아래 계속 보이는 안내. */
  editedMessage?: string
  editedActionLabel?: string
  /** "도움됐나요?" 모델에서 status==='negative'일 때 계속 보이는 안내 링크(예: 기록 상세에서 고치기). */
  negativeLink?: { label: string; onAction: () => void }
  errorMessage?: string | null
}

/**
 * 모든 AI 결과 피드백 줄(태그·한 줄 요약·변화 해석·지표 해석·회고 등)이 같은 동작 방식을 쓰게
 * 묶은 공용 부품(U4). editArea 유무로 "맞나요?"(고치기 가능)와 "도움됐나요?"(고치기 없음) 두
 * 모델을 모두 표현한다.
 */
export function FeedbackRow({
  question,
  status,
  pending = false,
  onPositive,
  onNegative,
  variant = 'thumbs',
  positiveLabel,
  negativeLabel,
  thanksMessage = '피드백 고마워요',
  savedMessage = '고쳤어요',
  editArea,
  onBeforeOpenEdit,
  editTriggerLabel,
  editTriggerAlwaysVisible = false,
  editedMessage,
  editedActionLabel,
  negativeLink,
  errorMessage,
}: Props) {
  const [transientMessage, setTransientMessage] = useState<string | null>(null)
  const [editingOverride, setEditingOverride] = useState<boolean | null>(null)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current)
    }
  }, [])

  // 실패하면(부모가 errorMessage를 채우면) 낙관적으로 바꿨던 상태를 모두 되돌린다.
  useEffect(() => {
    if (!errorMessage) return
    if (timerRef.current) clearTimeout(timerRef.current)
    setTransientMessage(null)
    setEditingOverride(null)
  }, [errorMessage])

  const editing = editArea ? (editingOverride ?? status === 'negative') : false

  function showMessage(message: string) {
    setTransientMessage(message)
    if (timerRef.current) clearTimeout(timerRef.current)
    timerRef.current = setTimeout(() => setTransientMessage(null), MESSAGE_DURATION_MS)
  }

  function openEdit() {
    onBeforeOpenEdit?.()
    setEditingOverride(true)
  }

  function handlePositive() {
    if (pending) return
    if (editArea) setEditingOverride(false)
    showMessage(thanksMessage)
    onPositive()
  }

  function handleNegative() {
    if (pending) return
    if (editArea) {
      openEdit()
      onNegative()
      return
    }
    showMessage(thanksMessage)
    onNegative()
  }

  function markSaved() {
    showMessage(savedMessage)
    setEditingOverride(false)
  }

  function cancelEdit() {
    setEditingOverride(false)
  }

  const answeredFinal = editArea ? status === 'positive' || status === 'edited' : status === 'positive' || status === 'negative'
  const collapsed = answeredFinal && !editing && !transientMessage

  return (
    <div className="flex flex-col gap-3">
      <div className={['grid-collapse', collapsed ? 'is-collapsed' : ''].join(' ')}>
        <div>
          <div className="flex flex-col gap-3 border-t border-[color-mix(in_srgb,var(--color-base)_28%,white)] pt-2">
            {transientMessage ? (
              <span className="text-sm text-ink-2">{transientMessage}</span>
            ) : (
              <div className="flex items-center justify-between gap-3">
                <span className="text-sm text-ink">{question}</span>
                <div className="flex items-center gap-2">
                  {variant === 'thumbs' ? (
                    <>
                      <button
                        type="button"
                        aria-label={positiveLabel ?? '도움이 됐어요'}
                        aria-pressed={status === 'positive'}
                        onClick={handlePositive}
                        className={[
                          'inline-flex h-11 w-11 items-center justify-center rounded-lg border text-ink hover:bg-[color-mix(in_srgb,var(--color-ink)_6%,white)]',
                          status === 'positive'
                            ? 'border-base bg-[color-mix(in_srgb,var(--color-base)_12%,white)]'
                            : 'border-line-2 bg-white',
                        ].join(' ')}
                      >
                        <ThumbsUpIcon width={20} height={20} />
                      </button>
                      <button
                        type="button"
                        aria-label={negativeLabel ?? '아쉬워요'}
                        aria-pressed={status === 'negative' || status === 'edited'}
                        onClick={handleNegative}
                        className={[
                          'inline-flex h-11 w-11 items-center justify-center rounded-lg border text-ink hover:bg-[color-mix(in_srgb,var(--color-ink)_6%,white)]',
                          status === 'negative' || status === 'edited'
                            ? 'border-base bg-[color-mix(in_srgb,var(--color-base)_12%,white)]'
                            : 'border-line-2 bg-white',
                        ].join(' ')}
                      >
                        <ThumbsDownIcon width={20} height={20} />
                      </button>
                    </>
                  ) : (
                    <>
                      <button
                        type="button"
                        aria-pressed={status === 'positive'}
                        onClick={handlePositive}
                        className={[
                          'inline-flex min-h-10 items-center justify-center rounded-lg border px-4 text-sm font-medium text-ink hover:bg-[color-mix(in_srgb,var(--color-ink)_6%,white)]',
                          status === 'positive'
                            ? 'border-base bg-[color-mix(in_srgb,var(--color-base)_12%,white)]'
                            : 'border-line-2 bg-white',
                        ].join(' ')}
                      >
                        {positiveLabel ?? '맞아요'}
                      </button>
                      <button
                        type="button"
                        aria-pressed={status === 'negative' || status === 'edited'}
                        onClick={handleNegative}
                        className={[
                          'inline-flex min-h-10 items-center justify-center rounded-lg border px-4 text-sm font-medium text-ink hover:bg-[color-mix(in_srgb,var(--color-ink)_6%,white)]',
                          status === 'negative' || status === 'edited'
                            ? 'border-base bg-[color-mix(in_srgb,var(--color-base)_12%,white)]'
                            : 'border-line-2 bg-white',
                        ].join(' ')}
                      >
                        {negativeLabel ?? '달라요'}
                      </button>
                    </>
                  )}
                  {editArea && editTriggerLabel && !editing && (editTriggerAlwaysVisible || status !== 'edited') ? (
                    <button
                      type="button"
                      onClick={openEdit}
                      className="inline-flex min-h-11 items-center text-sm font-medium text-base-ink underline underline-offset-4"
                    >
                      {editTriggerLabel}
                    </button>
                  ) : null}
                </div>
              </div>
            )}

            {editArea && editing && !transientMessage ? editArea({ markSaved, cancel: cancelEdit }) : null}
          </div>
        </div>
      </div>

      {editArea && status === 'edited' && !editing ? (
        <div className="flex flex-wrap items-center gap-2">
          {editedMessage ? <span className="text-sm text-ink-2">{editedMessage}</span> : null}
          {editedActionLabel ? (
            <button
              type="button"
              onClick={openEdit}
              className="inline-flex min-h-11 items-center text-sm font-medium text-base-ink underline underline-offset-4"
            >
              {editedActionLabel}
            </button>
          ) : null}
        </div>
      ) : null}

      {!editArea && status === 'negative' && negativeLink ? (
        <button
          type="button"
          onClick={negativeLink.onAction}
          className="inline-flex min-h-11 w-fit items-center gap-1.5 self-start text-sm font-medium text-base-ink underline underline-offset-4"
        >
          {negativeLink.label}
        </button>
      ) : null}

      {errorMessage ? <Note variant="sand">{errorMessage}</Note> : null}
    </div>
  )
}
