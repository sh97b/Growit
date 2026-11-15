import { useSearchParams } from 'react-router-dom'
import type { DateRangeValue, RangeValue } from '../components/FilterBar'
import { addDays, todayLocalDate } from '../lib/date'

function startOfThisMonth(): string {
  const now = new Date()
  const first = new Date(now.getFullYear(), now.getMonth(), 1)
  return formatLocalDateOf(first)
}

function formatLocalDateOf(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

/** 기간 값(ALL/30D/MONTH/CUSTOM)을 실제 API에 보낼 from/to(YYYY-MM-DD)로 바꾼다. */
export function resolveDateRange(value: DateRangeValue): { from?: string; to?: string } {
  if (value.range === '30D') return { from: addDays(todayLocalDate(), -30), to: todayLocalDate() }
  if (value.range === 'MONTH') return { from: startOfThisMonth(), to: todayLocalDate() }
  if (value.range === 'CUSTOM') return { from: value.from || undefined, to: value.to || undefined }
  return {}
}

/**
 * 돌아보기 기록·회고 탭이 공유하는 FilterBar 값을 URL 쿼리(q, hobby, range, from, to)와
 * 동기화한다(O1 C.2) — 새로고침·공유해도 필터가 유지된다. 회고 탭의 선택 결과 id(r)처럼 다른
 * 쿼리 파라미터는 건드리지 않는다.
 */
export function useFilterBarQuery() {
  const [searchParams, setSearchParams] = useSearchParams()

  const q = searchParams.get('q') ?? ''
  const hobbyId = searchParams.has('hobby') ? Number(searchParams.get('hobby')) : null
  const range: DateRangeValue = {
    range: (searchParams.get('range') as RangeValue | null) ?? 'ALL',
    from: searchParams.get('from') ?? '',
    to: searchParams.get('to') ?? '',
  }

  function patch(entries: Record<string, string | null>) {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        for (const [key, value] of Object.entries(entries)) {
          if (value == null || value === '') next.delete(key)
          else next.set(key, value)
        }
        return next
      },
      { replace: true },
    )
  }

  function setQ(value: string) {
    patch({ q: value || null })
  }

  function setHobbyId(value: number | null) {
    patch({ hobby: value == null ? null : String(value) })
  }

  function setRange(value: DateRangeValue) {
    patch({
      range: value.range === 'ALL' ? null : value.range,
      from: value.range === 'CUSTOM' ? value.from : null,
      to: value.range === 'CUSTOM' ? value.to : null,
    })
  }

  const hasActiveFilter = q !== '' || hobbyId != null || range.range !== 'ALL'

  function reset() {
    patch({ q: null, hobby: null, range: null, from: null, to: null })
  }

  return { q, hobbyId, range, setQ, setHobbyId, setRange, hasActiveFilter, reset }
}
