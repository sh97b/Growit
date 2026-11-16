import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { ActivityHeatmap } from '../components/ActivityHeatmap'
import { FilterBar } from '../components/FilterBar'
import { ListPane } from '../components/ListPane'
import { Loading } from '../components/Loading'
import { Split } from '../components/PageLayout'
import { Pagination } from '../components/Pagination'
import { ReviewHeaderRow } from '../components/ReviewHeaderRow'
import { PlusIcon } from '../components/icons'
import { useFilterBarQuery, resolveDateRange } from '../hooks/useFilterBarQuery'
import { useFitPageSize, useStablePageOnResize } from '../hooks/useFitPageSize'
import { useHobbies } from '../hooks/useHobbies'
import { useIsDesktop } from '../hooks/useIsDesktop'
import { addDays, formatMonthDay, todayLocalDate } from '../lib/date'
import { fetchRecords } from '../lib/records'
import { fetchActivityMap } from '../lib/stats'

// 기록 카드 한 칸의 최소 높이(패딩+caption 줄+본문 한 줄+테두리, rem). F3: 화면 높이에 맞춰 개수를 정한다.
const CARD_MIN_HEIGHT_REM = 5.5
const CARD_GAP_REM = 0.75

/** 검색어와 일치하는 부분을 <mark>로 강조한다(O1 D, 대소문자 구분 없음). */
function highlightMatch(text: string, query: string): React.ReactNode {
  if (!query) return text
  const lowerText = text.toLowerCase()
  const lowerQuery = query.toLowerCase()
  const index = lowerText.indexOf(lowerQuery)
  if (index === -1) return text
  return (
    <>
      {text.slice(0, index)}
      <mark className="rounded-sm bg-[color-mix(in_srgb,var(--color-base)_28%,white)] text-ink">
        {text.slice(index, index + query.length)}
      </mark>
      {text.slice(index + query.length)}
    </>
  )
}

export function RecordsPage() {
  const [page, setPage] = useState(0)
  const isDesktop = useIsDesktop()
  const filter = useFilterBarQuery()
  const { from, to } = resolveDateRange(filter.range)

  // 고정 개수 대신 목록 영역의 실제 높이에 맞춰 한 페이지 개수를 계산한다(F3).
  // 모바일은 페이지 스크롤을 유지하므로 1열 5개를 그대로 쓴다.
  // S1 A단계: ready로 렌더·쿼리를 막지 않는다 — 기본값(또는 직전 값)으로 즉시 그린다.
  const { containerRef, pageSize: fitPageSize } = useFitPageSize({
    itemHeightRem: CARD_MIN_HEIGHT_REM,
    gapRem: CARD_GAP_REM,
    columns: 1,
    min: 2,
    max: 12,
    defaultSize: 6,
  })
  const pageSize = isDesktop ? fitPageSize : 5
  useStablePageOnResize(page, setPage, pageSize)
  const { data: activeHobbies } = useHobbies(false)
  const { data: allHobbies } = useHobbies(true)

  useEffect(() => {
    setPage(0)
  }, [filter.hobbyId, filter.q, filter.range.range, filter.range.from, filter.range.to])

  const hobbyNameById = new Map((allHobbies ?? []).map((hobby) => [hobby.id, hobby.name]))

  const totalQuery = useQuery({
    queryKey: ['records', 'total'],
    queryFn: () => fetchRecords({ page: 0, size: 1 }),
  })

  const listQuery = useQuery({
    queryKey: ['records', 'list', filter.hobbyId, filter.q, from, to, page, pageSize],
    queryFn: () =>
      fetchRecords({
        hobbyId: filter.hobbyId ?? undefined,
        q: filter.q || undefined,
        from,
        to,
        page,
        size: pageSize,
      }),
    placeholderData: keepPreviousData,
  })

  // P1 2단계: 검색·필터로 총 페이지가 줄어들어 현재 페이지가 범위를 벗어나면 마지막 페이지로 보정한다.
  useEffect(() => {
    const total = listQuery.data?.totalCount
    if (total == null) return
    const lastPage = Math.max(0, Math.ceil(total / pageSize) - 1)
    if (page > lastPage) setPage(lastPage)
  }, [listQuery.data?.totalCount, pageSize, page])

  if (totalQuery.isLoading) {
    return <Loading />
  }

  const noRecordsAtAll = (totalQuery.data?.totalCount ?? 0) === 0
  const records = listQuery.data?.items ?? []
  const totalCount = listQuery.data?.totalCount ?? 0
  const totalPages = Math.ceil(totalCount / pageSize)

  if (noRecordsAtAll) {
    return (
      <div className="flex flex-col gap-4 lg:min-h-0 lg:flex-1">
        <ReviewHeaderRow title="지난 기록" />
        <div className="flex max-w-[45rem] flex-col items-start gap-4 rounded-2xl bg-surface p-5">
          <p className="text-[1.0625rem] text-ink">아직 기록이 없어요. 첫 기록이 여기에 쌓여요.</p>
          <Link
            to="/records/new"
            className="inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-lg bg-base px-5 text-body-min font-medium text-white no-underline lg:w-auto"
          >
            <PlusIcon width={18} height={18} />
            기록하기
          </Link>
        </div>
      </div>
    )
  }

  const filterBar = (
    <FilterBar
      search={{ value: filter.q, onChange: filter.setQ, placeholder: '기록 내용 검색' }}
      hobby={{ value: filter.hobbyId, options: activeHobbies ?? [], onChange: filter.setHobbyId }}
      range={{ value: filter.range, onChange: filter.setRange }}
      hasActiveFilter={filter.hasActiveFilter}
      onReset={filter.reset}
    />
  )

  const main = (
    <>
      <MobileHeatmapCard hobbyId={filter.hobbyId} />

      {filterBar}

      <ListPane
        listRef={containerRef}
        className="lg:min-h-0 lg:flex-1"
        footer={<Pagination page={page} totalPages={totalPages} onChange={setPage} />}
      >
        {listQuery.isLoading ? <Loading /> : null}

        {!listQuery.isLoading && totalCount === 0 ? (
          <div className="flex flex-col items-start gap-2 rounded-2xl bg-surface p-5">
            <p className="text-sm text-ink-2">조건에 맞는 기록이 없어요.</p>
            {filter.hasActiveFilter ? (
              <button
                type="button"
                onClick={filter.reset}
                className="inline-flex min-h-9 items-center text-sm font-medium text-base-ink no-underline rounded-full px-2 -mx-2 hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)] focus-visible:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]"
              >
                초기화
              </button>
            ) : null}
          </div>
        ) : null}

        {!listQuery.isLoading && totalCount > 0 ? (
          <div className="grid grid-cols-1 content-start gap-2 lg:gap-3">
            {records.map((record) => {
              const firstTag = record.analysis?.tags[0]
              return (
                <Link
                  key={record.id}
                  to={`/records/${record.id}`}
                  className="flex flex-col gap-1.5 rounded-xl border border-line bg-white p-3.5 text-ink no-underline lg:px-4 lg:py-4"
                >
                  <span className="flex items-center justify-between gap-2">
                    <span className="text-caption-min font-semibold text-ink-2">
                      {formatMonthDay(record.recordDate)} · {hobbyNameById.get(record.hobbyId) ?? ''}
                    </span>
                    {firstTag ? (
                      <span className="inline-flex h-6 flex-none items-center rounded-md bg-tag-topic-bg px-2 text-caption-min font-medium text-tag-topic-fg">
                        {firstTag}
                      </span>
                    ) : null}
                  </span>
                  <p className="line-clamp-2 font-serif text-lg text-ink lg:line-clamp-1">{highlightMatch(record.content, filter.q)}</p>
                </Link>
              )
            })}
          </div>
        ) : null}
      </ListPane>
    </>
  )

  const side = (
    <div className="hidden rounded-2xl bg-surface p-5 lg:block">
      <ActivityHeatmap weeks={52} hobbyId={filter.hobbyId} />
      <YearSummary hobbyId={filter.hobbyId} />
    </div>
  )

  return (
    <div className="flex flex-col gap-4 lg:min-h-0 lg:flex-1">
      <ReviewHeaderRow title="지난 기록" />
      <Split main={main} side={side} className="lg:min-h-0 lg:flex-1" />
    </div>
  )
}

/**
 * S1 B-4단계: 모바일 기록 탭의 활동 지도는 항상 펼쳐 두지 않는다. 기본은 접힘(높이 44px 한 줄,
 * "올해 기록한 날 N일 · 지도 보기 ▾")이고 누르면 아래로 펼쳐진다(6개월/1년 토글은 펼친 안에서).
 */
function MobileHeatmapCard({ hobbyId }: { hobbyId: number | null }) {
  const [expanded, setExpanded] = useState(false)
  const today = todayLocalDate()
  const from = `${today.slice(0, 4)}-01-01`
  const query = useQuery({
    queryKey: ['stats', 'activity-map', 'this-year-count', from, today, hobbyId ?? null],
    queryFn: () => fetchActivityMap({ from, to: today, hobbyId: hobbyId ?? undefined }),
  })
  const recordedDays = query.data?.length ?? 0

  return (
    <div className="rounded-2xl bg-surface lg:hidden">
      <button
        type="button"
        onClick={() => setExpanded((prev) => !prev)}
        aria-expanded={expanded}
        className="flex min-h-11 w-full items-center justify-between gap-2 px-4 text-body-min font-medium text-ink"
      >
        <span>올해 기록한 날 {recordedDays}일</span>
        <span aria-hidden="true" className="text-caption-min text-ink-2">
          {expanded ? '지도 접기 ▴' : '지도 보기 ▾'}
        </span>
      </button>
      {expanded ? (
        <div className="px-4 pb-4">
          <ActivityHeatmap weeks={52} toggle hobbyId={hobbyId} />
        </div>
      ) : null}
    </div>
  )
}

/** 디자인에 없는 추가(LAYOUT-V2 3단계): 활동 지도 데이터로 클라이언트에서 계산하는 "올해 요약" 한 줄. */
function YearSummary({ hobbyId }: { hobbyId: number | null }) {
  const today = todayLocalDate()
  const from = addDays(today, -364)
  const query = useQuery({
    queryKey: ['stats', 'activity-map', 'year-summary', from, today, hobbyId ?? null],
    queryFn: () => fetchActivityMap({ from, to: today, hobbyId: hobbyId ?? undefined }),
  })

  const summary = useMemo(() => {
    const entries = query.data ?? []
    if (entries.length === 0) return null
    const recordedDays = entries.length
    const recordCount = entries.reduce((sum, entry) => sum + entry.count, 0)
    const countByMonth = new Map<string, number>()
    for (const entry of entries) {
      const month = entry.date.slice(0, 7)
      countByMonth.set(month, (countByMonth.get(month) ?? 0) + entry.count)
    }
    const topMonth = [...countByMonth.entries()].sort((a, b) => b[1] - a[1])[0]
    const topMonthLabel = topMonth ? `${Number(topMonth[0].slice(5, 7))}월` : null
    return { recordedDays, recordCount, topMonthLabel }
  }, [query.data])

  if (!summary) return null

  return (
    <p className="mt-3 border-t border-line pt-3 text-caption-min text-ink-2">
      최근 1년 · 기록한 날 {summary.recordedDays}일 · 기록 {summary.recordCount}개
      {summary.topMonthLabel ? ` · 가장 많이 쓴 달 ${summary.topMonthLabel}` : ''}
    </p>
  )
}
