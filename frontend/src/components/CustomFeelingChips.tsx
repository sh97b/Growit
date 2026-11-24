import { useState } from 'react'
import { useCreateCustomFeeling, useCustomFeelings, useDeleteCustomFeeling } from '../hooks/useCustomFeelings'
import { ApiError } from '../lib/api'
import {
  CUSTOM_FEELING_MAX_COUNT,
  CUSTOM_FEELING_MAX_LENGTH,
  DIRECTION_OPTIONS,
  validateCustomFeelingLabel,
  type FeelingDirection,
} from '../lib/feelings'
import { Button } from './Button'

/**
 * 감정 칩 줄에 이어 붙는 "직접 추가한 감정" 칩들과 [+ 직접 추가]. 칩 줄(flex-wrap) 안에 그대로 놓아 쓴다.
 * 추가 폼은 새 줄(basis-full)로 펼쳐진다. 지워도 이미 쓴 기록의 감정은 그대로 남는다(서버가 기록에 복사해 둠).
 */
export function CustomFeelingChips({
  selectedId,
  onSelect,
}: {
  selectedId: number | null
  onSelect: (id: number | null) => void
}) {
  const query = useCustomFeelings()
  const create = useCreateCustomFeeling()
  const remove = useDeleteCustomFeeling()
  const [adding, setAdding] = useState(false)
  const [label, setLabel] = useState('')
  const [direction, setDirection] = useState<FeelingDirection | null>(null)
  const [error, setError] = useState<string | null>(null)

  const feelings = query.data ?? []
  const full = feelings.length >= CUSTOM_FEELING_MAX_COUNT

  function resetForm() {
    setAdding(false)
    setLabel('')
    setDirection(null)
    setError(null)
  }

  async function handleAdd() {
    const problem = validateCustomFeelingLabel(label)
    if (problem) return setError(problem)
    if (direction == null) return setError('기분 방향을 골라 주세요')
    setError(null)
    try {
      const created = await create.mutateAsync({ label, direction })
      onSelect(created.id)
      resetForm()
    } catch (caught) {
      setError(caught instanceof ApiError && caught.status < 500 ? caught.message : '지금은 추가하지 못했어요. 잠시 후 다시 시도해 주세요.')
    }
  }

  function handleDelete(id: number, name: string) {
    if (!window.confirm(`"${name}"을(를) 목록에서 지울까요? 이미 쓴 기록에는 그대로 남아요.`)) return
    remove.mutate(id, { onSuccess: () => selectedId === id && onSelect(null) })
  }

  return (
    <>
      {feelings.map((feeling) => (
        <span
          key={feeling.id}
          className={[
            'inline-flex min-h-11 items-center rounded-full bg-tag-feel-bg t-sub font-medium text-tag-feel-fg',
            selectedId === feeling.id ? 'shadow-[inset_0_0_0_2px_var(--color-tag-feel-fg)]' : '',
          ].join(' ')}
        >
          <button
            type="button"
            aria-pressed={selectedId === feeling.id}
            onClick={() => onSelect(selectedId === feeling.id ? null : feeling.id)}
            className="inline-flex min-h-11 items-center rounded-full pl-3 pr-1.5"
          >
            {feeling.label}
          </button>
          <button
            type="button"
            aria-label={`${feeling.label} 삭제`}
            onClick={() => handleDelete(feeling.id, feeling.label)}
            className="inline-flex min-h-11 min-w-8 items-center justify-center rounded-full pr-2 text-ink-3"
          >
            <span aria-hidden="true">×</span>
          </button>
        </span>
      ))}
      {!adding && !full ? (
        <button
          type="button"
          onClick={() => setAdding(true)}
          className="inline-flex min-h-11 items-center justify-center rounded-full border border-dashed border-line-2 bg-white px-3 t-sub font-medium text-ink-2"
        >
          + 직접 추가
        </button>
      ) : null}
      {full && !adding ? <span className="text-caption-min text-ink-3">직접 추가한 감정은 {CUSTOM_FEELING_MAX_COUNT}개까지예요</span> : null}
      {adding ? (
        <div className="flex w-full basis-full flex-col gap-2 rounded-xl border border-line bg-white p-3">
          <label htmlFor="custom-feeling-label" className="text-caption-min font-medium text-ink-2">
            감정 이름 (1~{CUSTOM_FEELING_MAX_LENGTH}자)
          </label>
          <input
            id="custom-feeling-label"
            value={label}
            maxLength={CUSTOM_FEELING_MAX_LENGTH}
            autoComplete="off"
            onChange={(event) => setLabel(event.target.value)}
            className="h-11 w-full rounded-lg border border-input bg-white px-3 text-body-min text-ink outline-none"
          />
          <fieldset className="m-0 border-0 p-0">
            <legend className="mb-1 text-caption-min font-medium text-ink-2">기분 방향</legend>
            <div className="flex flex-wrap gap-1.5">
              {DIRECTION_OPTIONS.map((option) => (
                <label
                  key={option.value}
                  className={[
                    'inline-flex min-h-11 cursor-pointer items-center rounded-lg border px-3 t-sub font-medium has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-base',
                    direction === option.value ? 'border-base bg-[color-mix(in_srgb,var(--color-base)_12%,white)] text-ink' : 'border-line-2 bg-white text-ink',
                  ].join(' ')}
                >
                  <input
                    type="radio"
                    name="custom-feeling-direction"
                    value={option.value}
                    checked={direction === option.value}
                    onChange={() => setDirection(option.value)}
                    className="sr-only"
                  />
                  {option.label}
                </label>
              ))}
            </div>
          </fieldset>
          {error ? (
            <p role="alert" className="text-caption-min text-[#6B3F0F]">
              {error}
            </p>
          ) : null}
          <div className="flex gap-2">
            <Button type="button" size="sm" onClick={handleAdd} disabled={create.isPending}>
              추가
            </Button>
            <Button type="button" size="sm" variant="secondary" onClick={resetForm}>
              취소
            </Button>
          </div>
        </div>
      ) : null}
    </>
  )
}
