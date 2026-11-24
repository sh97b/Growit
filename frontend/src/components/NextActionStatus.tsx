import { useMutation, useQueryClient } from '@tanstack/react-query'
import { NextActionCard } from './NextActionCard'
import { ApiError } from '../lib/api'
import { setNextActionState, type NextAction, type NextActionState, type RecordDetail } from '../lib/records'

const OPTIONS: { value: NextActionState; label: string }[] = [
  { value: 'TRIED', label: '해 봤어요' },
  { value: null, label: '아직 안 했어요' },
  { value: 'HOLD', label: '보류할래요' },
]

/** 기록 상세의 "다음에 해 볼 일" 카드와 상태 선택(고르는 즉시 저장하고, 현재 선택을 표시한다). */
export function NextActionStatus({ record, action }: { record: RecordDetail; action: NextAction }) {
  const queryClient = useQueryClient()
  const mutation = useMutation({
    mutationFn: (value: NextActionState) => setNextActionState(record.id, value),
    onMutate: async (value) => {
      const queryKey = ['records', record.id]
      await queryClient.cancelQueries({ queryKey })
      const previous = queryClient.getQueryData<RecordDetail>(queryKey)
      queryClient.setQueryData<RecordDetail>(queryKey, (old) =>
        old && old.analysis?.nextAction
          ? { ...old, analysis: { ...old.analysis, nextAction: { ...old.analysis.nextAction, state: value } } }
          : old,
      )
      return { previous }
    },
    onError: (_error, _value, context) => {
      if (context?.previous) queryClient.setQueryData(['records', record.id], context.previous)
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['records', 'next-action'] })
    },
  })

  const buttons = (
    <div className="flex flex-col gap-2">
      <div role="radiogroup" aria-label="다음에 해 볼 일 상태" className="flex flex-wrap gap-2">
        {OPTIONS.map((option) => {
          const selected = (action.state ?? null) === option.value
          return (
            <button
              key={option.label}
              type="button"
              role="radio"
              aria-checked={selected}
              onClick={() => (selected ? undefined : mutation.mutate(option.value))}
              className={[
                'inline-flex min-h-11 flex-1 basis-[7rem] items-center justify-center rounded-lg border px-3 t-sub font-medium whitespace-nowrap lg:flex-none lg:basis-auto lg:px-4',
                selected
                  ? 'border-base bg-[color-mix(in_srgb,var(--color-base)_14%,white)] font-semibold text-ink'
                  : 'border-line-2 bg-white text-ink',
              ].join(' ')}
            >
              {option.label}
            </button>
          )
        })}
      </div>
      {mutation.isError ? (
        <p role="alert" className="text-caption-min text-ink-2">
          {mutation.error instanceof ApiError ? mutation.error.message : '지금은 저장하지 못했어요. 잠시 후 다시 눌러 주세요.'}
        </p>
      ) : null}
    </div>
  )

  return <NextActionCard action={action} footer={buttons} />
}
