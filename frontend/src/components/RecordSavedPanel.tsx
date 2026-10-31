import { Button } from './Button'
import { CheckIcon } from './icons'
import { formatMonthDay } from '../lib/date'
import type { RecordItem } from '../lib/records'

export function RecordSavedPanel({
  record,
  hobbyName,
  isFirstRecord,
  onHome,
  onViewDetail,
  onEdit,
}: {
  record: RecordItem
  hobbyName: string
  isFirstRecord: boolean
  onHome: () => void
  onViewDetail: () => void
  onEdit: () => void
}) {
  return (
    <div className="flex flex-col gap-6 lg:max-w-[760px]">
      <div className="flex flex-wrap items-center gap-2.5">
        <span className="flex h-8 w-8 flex-none items-center justify-center rounded-full bg-[color-mix(in_srgb,var(--color-base)_12%,white)] text-base-ink">
          <CheckIcon width={18} height={18} />
        </span>
        <h1 className="font-serif text-xl font-semibold lg:text-[30px]">기록을 저장했어요</h1>
        <span className="text-[13px] text-ink-2">
          {formatMonthDay(record.recordDate)} · {hobbyName}
        </span>
      </div>

      {isFirstRecord ? (
        <div className="flex flex-col gap-3.5 rounded-2xl border border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_9%,white)] p-7">
          <span className="inline-flex w-fit items-center gap-1.5 rounded-md bg-[color-mix(in_srgb,var(--color-base)_12%,white)] px-2.5 py-1 text-[13px] font-semibold text-ink">
            첫 기록이에요
          </span>
          <p className="font-serif text-lg leading-[1.55] lg:text-[24px]">
            오늘 쓴 기록이 첫 기록이에요. 다음 기록에서 이어서 볼게요.
          </p>
        </div>
      ) : (
        <div className="flex flex-col gap-1 rounded-2xl bg-surface p-6">
          <p className="text-ink">AI 정리가 꺼져 있어요. 켜면 이전 기록과 이어서 한 줄을 보여드려요.</p>
          <span className="self-start text-sm font-medium text-ink-3">켜기 (준비 중)</span>
        </div>
      )}

      <div className="flex flex-col gap-2 lg:flex-row lg:self-start">
        <Button type="button" block onClick={onHome} className="lg:w-auto">
          홈으로
        </Button>
        <Button type="button" variant="secondary" block onClick={onViewDetail} className="lg:w-auto">
          기록 상세 보기
        </Button>
        <Button type="button" variant="secondary" block onClick={onEdit} className="lg:w-auto">
          고치기
        </Button>
      </div>
    </div>
  )
}
