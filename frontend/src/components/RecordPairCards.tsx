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

function CardBody({ card }: { card: RecordPairCard }) {
  return (
    <>
      <span className="t-sub font-semibold text-ink-2">{card.label}</span>
      <p className="t-body-lg ch-pair-body break-words font-serif text-ink">{truncate(card.content, card.maxLength ?? 300)}</p>
    </>
  )
}

/**
 * 나의 변화·저장 직후에서 쓰는 과거·현재 나란히 카드 부품. id가 있으면 그 기록 상세로 눌러 들어갈 수 있다.
 * 글자는 토큰(라벨 sub, 본문 body-lg 최대 4줄)이고 카드 높이는 내용 기준(최대 180px)이다. 폭 1024 이상은 좌우 두 장, 아래는 세로로 쌓는다.
 * 오른쪽(최신 쪽) 카드만 연한 초록 tint·테두리로 은은하게 강조한다.
 */
export function RecordPairCards({
  left,
  right,
}: {
  left: RecordPairCard | null
  right: RecordPairCard | null
  /** 예전 호출과의 호환용(지금은 쓰지 않는다). */
  rightBadge?: string
}) {
  if (!left && !right) return null
  return (
    <div className="grid grid-cols-1 gap-4 lg:grid-cols-2 lg:items-start">
      {left ? <RecordPairCardItem card={left} /> : null}
      {right ? <RecordPairCardItem card={right} emphasized /> : null}
    </div>
  )
}

function RecordPairCardItem({ card, emphasized = false }: { card: RecordPairCard; emphasized?: boolean }) {
  const className = [
    'rp-card flex min-w-0 flex-col gap-2 overflow-hidden rounded-xl border p-4 text-ink no-underline lg:max-h-[180px]',
    emphasized
      ? 'border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_9%,white)]'
      : 'border-line bg-white',
  ].join(' ')
  return card.id == null ? (
    <div className={className}>
      <CardBody card={card} />
    </div>
  ) : (
    <Link to={`/records/${card.id}`} className={className}>
      <CardBody card={card} />
    </Link>
  )
}
