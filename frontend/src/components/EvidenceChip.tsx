import { Link } from 'react-router-dom'
import { formatMonthDay } from '../lib/date'
import type { RecordRelated } from '../lib/records'

export function EvidenceChip({ record, block = false }: { record: RecordRelated; block?: boolean }) {
  return (
    <Link
      to={`/records/${record.id}`}
      title={record.content}
      className={[
        'inline-flex min-h-11 max-w-full items-center gap-2 rounded-lg border border-line-2 bg-white px-3 text-caption-min text-ink no-underline',
        block ? 'flex w-full' : '',
      ].join(' ')}
    >
      <span className="flex-none font-semibold text-base-ink">{formatMonthDay(record.recordDate)}</span>
      <span className={['min-w-0 overflow-hidden text-ellipsis whitespace-nowrap text-ink-2', block ? 'flex-1' : ''].join(' ')}>
        {record.content}
      </span>
    </Link>
  )
}
