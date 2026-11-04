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
      <span className="text-[13px] font-semibold text-ink-2">{card.label}</span>
      <p className="font-serif text-lg text-ink">{truncate(card.content, card.maxLength ?? 300)}</p>
    </>
  )
}

/** 나의 변화에서 쓰는 과거·현재 나란히 카드 부품. id가 있으면 그 기록 상세로 눌러 들어갈 수 있다. */
export function RecordPairCards({ left, right }: { left: RecordPairCard | null; right: RecordPairCard | null }) {
  if (!left && !right) return null
  return (
    <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
      {left ? <RecordPairCardItem card={left} /> : null}
      {right ? <RecordPairCardItem card={right} /> : null}
    </div>
  )
}

function RecordPairCardItem({ card }: { card: RecordPairCard }) {
  const className = 'flex flex-col gap-2 rounded-xl border border-line bg-white p-4 text-ink no-underline'
  if (card.id == null) {
    return (
      <div className={className}>
        <CardBody card={card} />
      </div>
    )
  }
  return (
    <Link to={`/records/${card.id}`} className={className}>
      <CardBody card={card} />
    </Link>
  )
}
