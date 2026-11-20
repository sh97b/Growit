import { AiLabel } from './AiLabel'
import type { RecordAnalysisChange } from '../lib/records'

/** change(구체적 변화)가 없고 support(응원)만 있으면 "달라진 점" 대신 이 이름을 쓴다. */
export function ChangeLabel({ change }: { change: RecordAnalysisChange }) {
  return <AiLabel>{change.text ? '달라진 점' : '응원 한마디'}</AiLabel>
}

/**
 * 달라진 점 카드 안의 문장들. 예전 결과(text만 있음)는 예전 모양 그대로 큰 세리프 한 문장으로 보이고,
 * support가 있으면 연한 구분선 아래에 한 단계 작은 글씨로 붙는다. 긴 문장도 줄바꿈되게 break-words를 쓴다.
 */
export function ChangeTexts({ change, large = false }: { change: RecordAnalysisChange; large?: boolean }) {
  const supportOnly = !change.text
  return (
    <div className="flex min-w-0 flex-col gap-3">
      {change.text ? (
        <p className={['break-words font-serif text-lg leading-[1.55]', large ? 'lg:text-[1.5rem]' : ''].join(' ')}>{change.text}</p>
      ) : null}
      {change.support ? (
        <p
          className={[
            'break-words font-serif leading-[1.6] text-base-ink',
            supportOnly
              ? ['text-lg', large ? 'lg:text-[1.5rem]' : ''].join(' ')
              : 'border-t border-[color-mix(in_srgb,var(--color-base)_20%,white)] pt-3 text-base',
          ].join(' ')}
        >
          {change.support}
        </p>
      ) : null}
    </div>
  )
}
