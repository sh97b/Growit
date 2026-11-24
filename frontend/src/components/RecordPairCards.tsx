import { Link } from 'react-router-dom'

export type RecordPairCard = {
  id?: number
  label: string
  content: string
  maxLength?: number
}

function truncate(text: string, maxLength: number): string {
  return text.length > maxLength ? `${text.slice(0, maxLength)}…` : text
}

function CardBody({ card, lines, stack = false }: { card: RecordPairCard; lines?: number; stack?: boolean }) {
  return (
    <>
      <span className="text-caption-min font-semibold text-ink-2">{card.label}</span>
      <p className={['break-words font-serif text-ink', stack ? 'ch-pair-text' : lines === 3 ? 'line-clamp-3 text-[18px] leading-[1.5]' : 'text-lg'].join(' ')}>
        {truncate(card.content, card.maxLength ?? 300)}
      </p>
    </>
  )
}

/**
 * 나의 변화·저장 직후에서 쓰는 과거·현재 나란히 카드 부품. id가 있으면 그 기록 상세로 눌러 들어갈
 * 수 있다. T2 4번: 오른쪽(최신 쪽) 카드만 연한 초록 tint·테두리로 은은하게 강조하고 위에 작은
 * 배지를 둔다(왼쪽은 흰 카드 그대로). rightBadge로 문맥에 맞는 말을 넣는다(저장 직후="오늘",
 * 나의 변화="최근" — currentRecord가 꼭 오늘 날짜는 아니라서).
 */
export function RecordPairCards({
  left,
  right,
  rightBadge = '오늘',
  lines,
  stack = false,
}: {
  left: RecordPairCard | null
  right: RecordPairCard | null
  rightBadge?: string
  /** 웹 변화 탭: 두 카드를 세로로 쌓고 부모 높이를 나눠 갖는다(글자는 부모 높이에 비례해 clamp). */
  stack?: boolean
  /** 3이면 본문을 최대 3줄로 말줄임하고 두 카드를 같은 높이로 맞춘다(나의 변화 웹). */
  lines?: number
}) {
  if (!left && !right) return null
  return (
    <div className={stack ? 'ch-pair flex min-h-0 flex-col' : 'grid grid-cols-1 gap-4 lg:grid-cols-2 lg:items-stretch'}>
      {left ? <RecordPairCardItem card={left} lines={lines} stack={stack} /> : null}
      {right ? <RecordPairCardItem card={right} emphasized badge={rightBadge} lines={lines} stack={stack} /> : null}
    </div>
  )
}

function RecordPairCardItem({
  card,
  emphasized = false,
  badge,
  lines,
  stack = false,
}: {
  card: RecordPairCard
  emphasized?: boolean
  badge?: string
  lines?: number
  stack?: boolean
}) {
  const className = [
    'flex min-w-0 flex-1 flex-col gap-2 rounded-xl border p-4 text-ink no-underline',
    lines === 3 ? 'max-h-[170px] overflow-hidden' : '',
    stack ? 'ch-pair-card' : '',
    emphasized
      ? 'border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_9%,white)]'
      : 'border-line bg-white',
  ].join(' ')
  const cardElement =
    card.id == null ? (
      <div className={className}>
        <CardBody card={card} lines={lines} stack={stack} />
      </div>
    ) : (
      <Link to={`/records/${card.id}`} className={className}>
        <CardBody card={card} lines={lines} stack={stack} />
      </Link>
    )
  if (!emphasized || !badge || lines === 3 || stack) return cardElement
  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      <span className="w-fit rounded-full bg-[color-mix(in_srgb,var(--color-base)_18%,white)] px-2 py-0.5 text-caption-min font-medium text-base-ink">
        {badge}
      </span>
      {cardElement}
    </div>
  )
}
