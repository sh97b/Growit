import { useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { formatMonthDay } from '../lib/date'
import type { CompareEvidence, CompareIdea, CompareInsight, CompareResult } from '../lib/ai'

type Tone = 'good' | 'gap' | 'next'

/** 구역별 색(성장 페이지 한지 톤에 맞춘 은은한 초록·호박·파랑 계열). */
const TONE: Record<Tone, { box: string; title: string; marker: string }> = {
  good: {
    box: 'border-[color-mix(in_srgb,var(--color-base)_26%,white)] bg-[color-mix(in_srgb,var(--color-base)_7%,white)]',
    title: 'text-base-ink',
    marker: 'bg-base text-white',
  },
  gap: {
    box: 'border-note-sand-line bg-note-sand-bg',
    title: 'text-note-sand-fg',
    marker: 'bg-[#B98A2E] text-white',
  },
  next: {
    box: 'border-note-slate-line bg-note-slate-bg',
    title: 'text-note-slate-fg',
    marker: 'bg-[#4F6F8F] text-white',
  },
}

const ZONE_TITLE: Record<Tone, string> = { good: '좋아진 점', gap: '더 키워 볼 점', next: '이렇게 해 보면 좋아요' }

function EvidenceButton({ count, open, onToggle, className = '' }: { count: number; open: boolean; onToggle: () => void; className?: string }) {
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-expanded={open}
      className={['t-sub inline-flex h-7 flex-none items-center gap-1 rounded-full px-1 font-medium text-ink-2 hover:text-ink', className].join(' ')}
    >
      근거 {count}개 {open ? '▴' : '▾'}
    </button>
  )
}

function EvidenceList({ evidence }: { evidence: CompareEvidence[] }) {
  return (
    <ul className="flex min-w-0 flex-col gap-1">
      {evidence.map((entry) => (
        <li key={entry.id} className="min-w-0">
          <Link
            to={`/records/${entry.id}`}
            title={entry.content}
            className="t-sub inline-flex min-h-8 max-w-full items-center gap-2 rounded-lg border border-line-2 bg-white px-2.5 text-ink no-underline"
          >
            <span className="flex-none font-semibold text-base-ink">{formatMonthDay(entry.recordDate)}</span>
            <span className="min-w-0 truncate text-ink-2">{entry.content}</span>
          </Link>
        </li>
      ))}
    </ul>
  )
}

function EvidenceFold({ evidence }: { evidence: CompareEvidence[] }) {
  const [open, setOpen] = useState(false)
  if (evidence.length === 0) return null
  return (
    <div className="flex min-w-0 flex-col gap-1">
      <EvidenceButton count={evidence.length} open={open} onToggle={() => setOpen((value) => !value)} className="w-fit" />
      {open ? <EvidenceList evidence={evidence} /> : null}
    </div>
  )
}

function Marker({ tone, children }: { tone: Tone; children: ReactNode }) {
  return (
    <span
      aria-hidden="true"
      className={['mt-0.5 flex h-5 w-5 flex-none items-center justify-center rounded-full t-cap font-semibold', TONE[tone].marker].join(' ')}
    >
      {children}
    </span>
  )
}

function Zone({ tone, minPriority, children }: { tone: Tone; minPriority?: number; children: ReactNode }) {
  const style = TONE[tone]
  return (
    <section
      aria-label={ZONE_TITLE[tone]}
      data-zone={tone}
      data-minp={minPriority}
      className={['flex min-w-0 flex-col gap-2 rounded-xl border p-3', style.box].join(' ')}
    >
      <h4 className={['t-body font-semibold', style.title].join(' ')}>{ZONE_TITLE[tone]}</h4>
      {children}
    </section>
  )
}

/** 항목을 보여 줄 우선순위(앞쪽이 먼저). 낮은 화면에서는 뒤쪽 항목부터 항목 단위로 숨긴다. */
const PRIORITY_ORDER: [Tone, number][] = [
  ['good', 0],
  ['gap', 0],
  ['next', 0],
  ['good', 1],
  ['next', 1],
  ['gap', 1],
  ['good', 2],
]

function InsightItem({ tone, marker, item, priority }: { tone: Tone; marker: string; item: CompareInsight; priority?: number }) {
  const [open, setOpen] = useState(false)
  // 웹 카드(priority 있음): 근거 버튼을 제목 줄 오른쪽에 두어 항목 높이를 한 줄 줄인다.
  const compact = priority != null
  return (
    <li data-priority={priority} className="flex min-w-0 gap-2">
      <Marker tone={tone}>{marker}</Marker>
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <div className="flex min-w-0 items-start justify-between gap-2">
          <p className={[compact ? 't-body-lg' : 't-body', 'min-w-0 break-words font-semibold text-ink'].join(' ')}>{item.point}</p>
          {compact && item.evidence.length > 0 ? (
            <EvidenceButton count={item.evidence.length} open={open} onToggle={() => setOpen((value) => !value)} />
          ) : null}
        </div>
        <p className={['t-body break-words text-ink-2', ''].join(' ')}>{item.because}</p>
        {compact ? open ? <EvidenceList evidence={item.evidence} /> : null : <EvidenceFold evidence={item.evidence} />}
      </div>
    </li>
  )
}

function IdeaItem({ item, priority }: { item: CompareIdea; priority?: number }) {
  const [open, setOpen] = useState(false)
  const compact = priority != null
  return (
    <li data-priority={priority} className="flex min-w-0 gap-2">
      <Marker tone="next">→</Marker>
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <div className="flex min-w-0 items-start justify-between gap-2">
          <p className={[compact ? 't-body-lg' : 't-body', 'min-w-0 break-words font-semibold text-ink'].join(' ')}>{item.idea}</p>
          {compact && item.evidence.length > 0 ? (
            <EvidenceButton count={item.evidence.length} open={open} onToggle={() => setOpen((value) => !value)} />
          ) : null}
        </div>
        {compact ? open ? <EvidenceList evidence={item.evidence} /> : null : <EvidenceFold evidence={item.evidence} />}
      </div>
    </li>
  )
}

/**
 * 구조화된 변화 해석(schemaVersion 2): 큰 세리프 headline 아래에 "좋아진 점"·"더 키워 볼 점"·"이렇게 해 보면 좋아요" 구역.
 * 각 항목은 point를 굵게, because를 보조 글자로 쓰고, 근거 기록(서버가 채운 날짜·내용)은 "근거 N개"로 접어 둔다.
 * (모바일과 [전체 보기] 창에서 쓰는 세로 쌓기 모양. 웹 카드는 CompareInsightZones를 쓴다.)
 */
export function CompareInsights({ result, headlineClass = '' }: { result: CompareResult; headlineClass?: string }) {
  const good = result.good ?? []
  const gap = result.gap ?? []
  const next = result.next ?? []
  return (
    <div className="flex min-w-0 flex-col gap-3">
      <h3 className={['t-key break-words text-ink', headlineClass].join(' ')}>{result.headline}</h3>
      {good.length > 0 ? (
        <Zone tone="good">
          <ol className="flex flex-col gap-2.5">
            {good.map((item, index) => (
              <InsightItem key={`${item.point}-${index}`} tone="good" marker={String(index + 1)} item={item} />
            ))}
          </ol>
        </Zone>
      ) : null}
      {gap.length > 0 ? (
        <Zone tone="gap">
          <ul className="flex flex-col gap-2.5">
            {gap.map((item, index) => (
              <InsightItem key={`${item.point}-${index}`} tone="gap" marker="•" item={item} />
            ))}
          </ul>
        </Zone>
      ) : null}
      {next.length > 0 ? (
        <Zone tone="next">
          <ul className="flex flex-col gap-2.5">
            {next.map((item, index) => (
              <IdeaItem key={`${item.idea}-${index}`} item={item} />
            ))}
          </ul>
        </Zone>
      ) : null}
    </div>
  )
}

/**
 * 웹 변화 탭의 해석 본문: 구역을 세로로 쌓고, 항목마다 data-priority(1이 가장 먼저)를 단다. 높이가 모자라면 CSS 컨테이너 쿼리가
 * 낮은 우선순위 항목부터 항목 단위로 숨긴다(흐림·잘림 없음). 개수가 0인 구역은 그리지 않는다. 우선순위는 실제로 있는 항목끼리 1부터 센다.
 */
export function CompareInsightZones({ result }: { result: CompareResult }) {
  const good = result.good ?? []
  const gap = result.gap ?? []
  const next = result.next ?? []
  const lists: Record<Tone, unknown[]> = { good, gap, next }
  const rank = new Map<string, number>()
  let counter = 1
  for (const [tone, index] of PRIORITY_ORDER) {
    if (lists[tone][index]) rank.set(`${tone}:${index}`, counter++)
  }
  const minOf = (tone: Tone) => {
    const values = lists[tone].map((_, index) => rank.get(`${tone}:${index}`) ?? 99)
    return values.length > 0 ? Math.min(...values) : undefined
  }
  return (
    <div className="flex min-w-0 flex-col gap-3">
      {good.length > 0 ? (
        <Zone tone="good" minPriority={minOf('good')}>
          <ol className="flex flex-col gap-2.5">
            {good.map((item, index) => (
              <InsightItem key={`${item.point}-${index}`} tone="good" marker={String(index + 1)} item={item} priority={rank.get(`good:${index}`)} />
            ))}
          </ol>
        </Zone>
      ) : null}
      {gap.length > 0 ? (
        <Zone tone="gap" minPriority={minOf('gap')}>
          <ul className="flex flex-col gap-2.5">
            {gap.map((item, index) => (
              <InsightItem key={`${item.point}-${index}`} tone="gap" marker="•" item={item} priority={rank.get(`gap:${index}`)} />
            ))}
          </ul>
        </Zone>
      ) : null}
      {next.length > 0 ? (
        <Zone tone="next" minPriority={minOf('next')}>
          <ul className="flex flex-col gap-2.5">
            {next.map((item, index) => (
              <IdeaItem key={`${item.idea}-${index}`} item={item} priority={rank.get(`next:${index}`)} />
            ))}
          </ul>
        </Zone>
      ) : null}
    </div>
  )
}
