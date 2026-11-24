import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { formatMonthDay } from '../lib/date'
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
export function NextActionCard({ action, footer }: { action: NextAction; footer?: ReactNode }) {
  return (
    <div className="flex flex-col gap-2 rounded-2xl bg-surface p-5">
      <AiLabel>다음에 해 볼 일</AiLabel>
      <NextActionDetails action={action} />
      {footer ? <div className="mt-1 border-t border-line pt-3">{footer}</div> : null}
    </div>
  )
}

/**
 * 홈 회고 카드 안의 블록. N 이전의 "다음 작은 행동" 카드(CoachActionCard)와 같은 모양을 그대로 쓰고
 * 문구와 데이터만 바꿨다: 연한 배경의 둥근 카드, 상단 칩, 큰 세리프 제목, "N분 · 이유" 줄, 근거 칩.
 */
export function HomeNextActionBlock({ action, dense = false }: { action: NextAction; dense?: boolean }) {
  const detail = action.reason ? `${action.minutes}분 · ${action.reason}` : `${action.minutes}분`
  if (dense) {
    // 웹 홈: 제목 20px 최대 2줄, 이유 1줄, 근거 칩 한 줄(날짜+내용 말줄임), 안쪽 여백 축소.
    return (
      <div className="flex flex-col gap-1 rounded-xl border border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_9%,white)] p-3">
        <AiLabel>다음에 해 볼 일</AiLabel>
        <p className="line-clamp-2 font-serif text-[20px] font-semibold leading-[1.3]">{action.text}</p>
        <span className="truncate text-xs text-ink-2" title={detail}>
          {detail}
        </span>
        {action.evidence ? (
          <Link
            to={`/records/${action.evidence.id}`}
            title={action.evidence.content}
            className="inline-flex h-7 min-w-0 max-w-full items-center gap-2 rounded-lg border border-line-2 bg-white px-2.5 text-xs text-ink no-underline"
          >
            <span className="flex-none font-semibold text-base-ink">{formatMonthDay(action.evidence.recordDate)}</span>
            <span className="min-w-0 truncate text-ink-2">{action.evidence.content}</span>
          </Link>
        ) : null}
      </div>
    )
  }
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
