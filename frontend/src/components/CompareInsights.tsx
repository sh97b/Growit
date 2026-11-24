import { useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { formatMonthDay } from '../lib/date'
import type { CompareEvidence, CompareResult } from '../lib/ai'

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

function EvidenceFold({ evidence }: { evidence: CompareEvidence[] }) {
  const [open, setOpen] = useState(false)
  if (evidence.length === 0) return null
  return (
    <div className="ch-fold flex min-w-0 flex-col gap-1">
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
        className="t-sub inline-flex h-7 w-fit items-center gap-1 rounded-full px-1 font-medium text-ink-2 hover:text-ink"
      >
        근거 {evidence.length}개 {open ? '▴' : '▾'}
      </button>
      {open ? (
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
      ) : null}
    </div>
  )
}

function Zone({ tone, title, dense = false, children }: { tone: Tone; title: string; dense?: boolean; children: ReactNode }) {
  const style = TONE[tone]
  return (
    <section aria-label={title} className={['flex min-w-0 flex-col rounded-xl border', dense ? 'gap-1.5 p-2.5' : 'gap-2 p-3', style.box].join(' ')}>
      <h4 className={['t-body font-semibold', style.title].join(' ')}>{title}</h4>
      {children}
    </section>
  )
}

function Marker({ tone, children }: { tone: Tone; children: ReactNode }) {
  return (
    <span
      aria-hidden="true"
      className={['mt-0.5 flex h-5 w-5 flex-none items-center justify-center rounded-full text-xs font-semibold', TONE[tone].marker].join(' ')}
    >
      {children}
    </span>
  )
}

/**
 * 구조화된 변화 해석(schemaVersion 2): 큰 세리프 headline 아래에 "좋아진 점"·"더 키워 볼 점"·"이렇게 해 보면 좋아요" 구역.
 * 각 항목은 point를 굵게, because를 보조 글자로 쓰고, 근거 기록(서버가 채운 날짜·내용)은 "근거 N개"로 접어 둔다.
 */
export function CompareInsights({
  result,
  headlineClass = '',
  dense = false,
  columns = false,
  hideHeadline = false,
}: {
  result: CompareResult
  headlineClass?: string
  /** 웹 변화 탭 카드: 이유는 2줄까지만, 근거 접기는 숨긴다(전체는 [전체 보기]에서). */
  dense?: boolean
  /** 웹 변화 탭: 세 구역을 나란히 세 열로 놓는다. */
  columns?: boolean
  /** 카드가 headline을 따로 그릴 때. */
  hideHeadline?: boolean
}) {
  const good = result.good ?? []
  const gap = result.gap ?? []
  const next = result.next ?? []
  return (
    <div className={['flex min-w-0 flex-col', dense ? 'gap-2' : 'gap-3'].join(' ')}>
      {hideHeadline ? null : <h3 className={['t-key break-words text-ink', headlineClass].join(' ')}>{result.headline}</h3>}
      <div className={columns ? 'grid min-w-0 grid-cols-3 items-start gap-4' : 'contents'}>
      {good.length > 0 ? (
        <Zone tone="good" title="좋아진 점" dense={dense}>
          <ol className="flex flex-col gap-2.5">
            {good.map((item, index) => (
              <li key={`${item.point}-${index}`} className="flex min-w-0 gap-2">
                <Marker tone="good">{index + 1}</Marker>
                <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                  <p className="t-body-lg break-words font-semibold text-ink">{item.point}</p>
                  <p className={['t-body break-words text-ink-2', dense ? 'line-clamp-2' : columns ? 'ch-because line-clamp-3' : ''].join(' ')}>{item.because}</p>
                  {dense ? null : <EvidenceFold evidence={item.evidence} />}
                </div>
              </li>
            ))}
          </ol>
        </Zone>
      ) : null}
      {gap.length > 0 ? (
        <Zone tone="gap" title="더 키워 볼 점" dense={dense}>
          <ul className="flex flex-col gap-2.5">
            {gap.map((item, index) => (
              <li key={`${item.point}-${index}`} className="flex min-w-0 gap-2">
                <Marker tone="gap">•</Marker>
                <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                  <p className="t-body-lg break-words font-semibold text-ink">{item.point}</p>
                  <p className={['t-body break-words text-ink-2', dense ? 'line-clamp-2' : ''].join(' ')}>{item.because}</p>
                  {dense ? null : <EvidenceFold evidence={item.evidence} />}
                </div>
              </li>
            ))}
          </ul>
        </Zone>
      ) : null}
      {next.length > 0 ? (
        <Zone tone="next" title="이렇게 해 보면 좋아요" dense={dense}>
          <ul className="flex flex-col gap-2.5">
            {next.map((item, index) => (
              <li key={`${item.idea}-${index}`} className="flex min-w-0 gap-2">
                <Marker tone="next">→</Marker>
                <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                  <p className="t-body-lg break-words font-semibold text-ink">{item.idea}</p>
                  {dense ? null : <EvidenceFold evidence={item.evidence} />}
                </div>
              </li>
            ))}
          </ul>
        </Zone>
      ) : null}
      </div>
    </div>
  )
}
