import { AiLabel } from './AiLabel'
import type { NextAction } from '../lib/records'

/** 저장 직후 화면의 "다음에 해 볼 일". 버튼 없이 문장과 걸리는 시간만 담백하게 보여 준다(AI 글이라 산세리프). */
export function NextActionCard({ action }: { action: NextAction }) {
  return (
    <div className="flex flex-col gap-2 rounded-2xl bg-surface p-5">
      <AiLabel>다음에 해 볼 일</AiLabel>
      <p className="text-body-min leading-[1.6] text-ink">{action.text}</p>
      <span className="text-caption-min text-ink-2">약 {action.minutes}분</span>
    </div>
  )
}
