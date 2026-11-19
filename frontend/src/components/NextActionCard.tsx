import { AiLabel } from './AiLabel'
import { EvidenceRow } from './CoachResultView'
import type { NextAction } from '../lib/records'

/** 근거 기록 칩은 회고 카드와 같은 모양을 쓴다. 근거가 없는 예전 결과는 칩 줄을 생략한다. */
export function NextActionDetails({ action }: { action: NextAction }) {
  return (
    <>
      <p className="text-body-min leading-[1.6] text-ink">{action.text}</p>
      <span className="text-caption-min text-ink-2">
        약 {action.minutes}분{action.reason ? ` · ${action.reason}` : ''}
      </span>
      {action.evidence ? (
        <EvidenceRow
          evidence={[
            { recordId: action.evidence.id, recordDate: action.evidence.recordDate, content: action.evidence.content },
          ]}
        />
      ) : null}
    </>
  )
}

/** 저장 직후 화면의 "다음에 해 볼 일". 버튼 없이 문장·이유·근거 칩만 담백하게 보여 준다(AI 글이라 산세리프). */
export function NextActionCard({ action }: { action: NextAction }) {
  return (
    <div className="flex flex-col gap-2 rounded-2xl bg-surface p-5">
      <AiLabel>다음에 해 볼 일</AiLabel>
      <NextActionDetails action={action} />
    </div>
  )
}

/**
 * 홈 회고 카드 안의 블록. N 이전의 "다음 작은 행동" 카드(CoachActionCard)와 같은 모양을 그대로 쓰고
 * 문구와 데이터만 바꿨다: 연한 배경의 둥근 카드, 상단 칩, 큰 세리프 제목, "N분 · 이유" 줄, 근거 칩.
 */
export function HomeNextActionBlock({ action }: { action: NextAction }) {
  const detail = action.reason ? `${action.minutes}분 · ${action.reason}` : `${action.minutes}분`
  return (
    <div className="flex flex-col gap-3.5 rounded-2xl border border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_9%,white)] p-5">
      <AiLabel>다음에 해 볼 일</AiLabel>
      <p className="text-section-title">{action.text}</p>
      <span className="text-caption-min text-ink-2">{detail}</span>
      {action.evidence ? (
        <EvidenceRow
          evidence={[
            { recordId: action.evidence.id, recordDate: action.evidence.recordDate, content: action.evidence.content },
          ]}
        />
      ) : null}
    </div>
  )
}
