import { useEffect, useState } from 'react'
import { Dropdown } from './Dropdown'
import { SearchIcon, XIcon } from './icons'

export type RangeValue = 'ALL' | '30D' | 'MONTH' | 'CUSTOM'

export type DateRangeValue = { range: RangeValue; from: string; to: string }

const RANGE_OPTIONS: { value: RangeValue; label: string }[] = [
  { value: 'ALL', label: '전체 기간' },
  { value: '30D', label: '최근 30일' },
  { value: 'MONTH', label: '이번 달' },
  { value: 'CUSTOM', label: '직접 선택' },
]

type HobbyOption = { id: number; name: string }

type FilterBarProps = {
  search: { value: string; onChange: (value: string) => void; placeholder?: string }
  hobby: { value: number | null; options: HobbyOption[]; onChange: (value: number | null) => void }
  range: { value: DateRangeValue; onChange: (value: DateRangeValue) => void }
  hasActiveFilter: boolean
  onReset: () => void
}

/**
 * 돌아보기 기록·회고 탭이 공유하는 필터 줄(O1 C.2): [검색창] [분야 드롭다운] [기간 드롭다운].
 * 직접 선택을 고르면 드롭다운 패널 안에 날짜 입력 팝오버가 열려 줄 높이를 늘리지 않는다.
 */
export function FilterBar({ search, hobby, range, hasActiveFilter, onReset }: FilterBarProps) {
  const hobbyOptions = [{ value: 'ALL', label: '전체' }, ...hobby.options.map((h) => ({ value: String(h.id), label: h.name }))]
  const hobbyValue = hobby.value == null ? 'ALL' : String(hobby.value)

  // 입력은 바로 보여주고, 상위(URL·쿼리)로의 반영만 300ms 디바운스한다 — 매 글자마다 검색 요청이
  // 나가지 않게 한다. 바깥에서 값이 바뀌면(초기화, 뒤로가기 등) 입력칸도 같이 맞춘다.
  const [draft, setDraft] = useState(search.value)
  useEffect(() => {
    setDraft(search.value)
  }, [search.value])
  useEffect(() => {
    if (draft === search.value) return
    const timer = setTimeout(() => search.onChange(draft), 300)
    return () => clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [draft])

  return (
    <div className="flex h-10 flex-wrap items-center gap-2 lg:h-10 lg:flex-nowrap">
      <div className="relative h-11 w-[26rem] max-w-full flex-none lg:h-10">
        <SearchIcon width={16} height={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-ink-3" />
        <input
          type="search"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          placeholder={search.placeholder ?? '검색'}
          aria-label={search.placeholder ?? '검색'}
          className="h-11 w-full rounded-lg border border-line-2 bg-white pl-9 pr-9 text-sm text-ink placeholder:text-ink-3 lg:h-10"
        />
        {draft ? (
          <button
            type="button"
            onClick={() => setDraft('')}
            aria-label="검색어 지우기"
            className="absolute right-1.5 top-1/2 inline-flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-full text-ink-2"
          >
            <XIcon width={14} height={14} />
          </button>
        ) : null}
      </div>

      <Dropdown
        aria-label="분야"
        value={hobbyValue}
        options={hobbyOptions}
        onChange={(value) => hobby.onChange(value === 'ALL' ? null : Number(value))}
      />

      <Dropdown
        aria-label="기간"
        value={range.value.range}
        options={RANGE_OPTIONS}
        onChange={(value) => range.onChange({ ...range.value, range: value })}
        extra={{
          show: range.value.range === 'CUSTOM',
          content: () => (
            <div className="flex items-center gap-1.5 p-1">
              <input
                type="date"
                value={range.value.from}
                onChange={(event) => range.onChange({ ...range.value, from: event.target.value })}
                aria-label="시작 날짜"
                className="h-10 w-[8.5rem] rounded-md border border-line-2 bg-white px-2 text-sm text-ink"
              />
              <span className="text-ink-2">–</span>
              <input
                type="date"
                value={range.value.to}
                onChange={(event) => range.onChange({ ...range.value, to: event.target.value })}
                aria-label="종료 날짜"
                className="h-10 w-[8.5rem] rounded-md border border-line-2 bg-white px-2 text-sm text-ink"
              />
            </div>
          ),
        }}
      />

      {hasActiveFilter ? (
        <button
          type="button"
          onClick={onReset}
          className="inline-flex h-11 flex-none items-center text-sm font-medium text-base-ink underline underline-offset-4 lg:h-10"
        >
          초기화
        </button>
      ) : null}
    </div>
  )
}
