import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronRightIcon } from '../components/icons'
import { CoachResultView } from '../components/CoachResultView'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
import { ReviewHeaderRow } from '../components/ReviewHeaderRow'
import { SplitWide } from '../components/PageLayout'
import { useCurrentUser } from '../hooks/useCurrentUser'
import { useFitPageSize, useStablePageOnResize } from '../hooks/useFitPageSize'
import { useHobbies } from '../hooks/useHobbies'
import { useIsDesktop } from '../hooks/useIsDesktop'
import { ApiError } from '../lib/api'
import {
  fetchAiUsage,
  fetchCoach,
  fetchCoachList,
  fetchCoachResult,
  fetchLatestCoach,
  markCoachViewed,
  type CoachPeriod,
  type CoachResult,
} from '../lib/ai'
import { formatMonthDay, formatLocalDate, todayLocalDate } from '../lib/date'
import { pickClass } from '../lib/recordForm'

const PERIODS: { value: CoachPeriod; label: string }[] = [
  { value: 'THIS_WEEK', label: '이번 주' },
  { value: '2W', label: '최근 2주' },
  { value: '1M', label: '한 달' },
]

const LIST_ROW_HEIGHT_REM = 5.5

type DateFilter = 'ALL' | '30D' | 'MONTH' | 'CUSTOM'

function startOfThisMonth(): string {
  const now = new Date()
  return formatLocalDate(new Date(now.getFullYear(), now.getMonth(), 1))
}

function daysAgo(days: number): string {
  const date = new Date()
  date.setDate(date.getDate() - days)
  return formatLocalDate(date)
}

export function CoachPage() {
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const isDesktop = useIsDesktop()
  const { data: currentUser, isLoading: userLoading } = useCurrentUser()
  const { data: activeHobbies, isLoading: hobbiesLoading } = useHobbies(false)
  const [hobbyId, setHobbyId] = useState<number | null>(null)
  const [period, setPeriod] = useState<CoachPeriod>('2W')
  const [result, setResult] = useState<CoachResult | null>(null)
  const viewedIds = useRef(new Set<number>())

  const [queryInput, setQueryInput] = useState('')
  const [debouncedQuery, setDebouncedQuery] = useState('')
  const [dateFilter, setDateFilter] = useState<DateFilter>('ALL')
  const [customFrom, setCustomFrom] = useState('')
  const [customTo, setCustomTo] = useState('')
  const [page, setPage] = useState(0)

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQuery(queryInput.trim()), 300)
    return () => clearTimeout(timer)
  }, [queryInput])

  const selectedId = Number(searchParams.get('r')) || null

  // 전체(null)를 기본으로 하고, 특정 분야를 고르면 그 값을 쓴다. 분야 목록이 바뀌어 더 이상
  // 활성 목록에 없는 분야가 선택돼 있으면(보관 등) 전체로 되돌린다.
  const effectiveHobbyId =
    hobbyId != null && activeHobbies?.some((hobby) => hobby.id === hobbyId) ? hobbyId : null

  const listFrom = dateFilter === '30D' ? daysAgo(30) : dateFilter === 'MONTH' ? startOfThisMonth() : dateFilter === 'CUSTOM' ? customFrom || undefined : undefined
  const listTo = dateFilter === '30D' || dateFilter === 'MONTH' ? todayLocalDate() : dateFilter === 'CUSTOM' ? customTo || undefined : undefined

  const { containerRef, pageSize: fitPageSize } = useFitPageSize({
    itemHeightRem: LIST_ROW_HEIGHT_REM,
    min: 2,
    max: 12,
  })
  const pageSize = isDesktop ? fitPageSize : 6
  useStablePageOnResize(page, setPage, pageSize)

  const listQuery = useQuery({
    queryKey: ['ai', 'coach', 'list', effectiveHobbyId, debouncedQuery, listFrom, listTo, page, pageSize],
    queryFn: () =>
      fetchCoachList({
        hobbyId: effectiveHobbyId ?? undefined,
        q: debouncedQuery || undefined,
        from: listFrom,
        to: listTo,
        page,
        size: pageSize,
      }),
    enabled: !!currentUser?.aiEnabled,
  })

  const latestQuery = useQuery({
    queryKey: ['ai', 'coach', 'latest'],
    queryFn: fetchLatestCoach,
    enabled: !!currentUser?.aiEnabled && selectedId == null,
  })

  const selectedQuery = useQuery({
    queryKey: ['ai', 'results', selectedId],
    queryFn: () => fetchCoachResult(selectedId!),
    enabled: selectedId != null,
  })

  const mutation = useMutation({
    mutationFn: () => fetchCoach({ hobbyId: effectiveHobbyId ?? undefined, period }),
    onSuccess: (data) => {
      setResult(data)
      if (data.id != null) {
        setSearchParams((prev) => {
          const next = new URLSearchParams(prev)
          next.set('r', String(data.id))
          return next
        })
      }
      queryClient.invalidateQueries({ queryKey: ['ai', 'coach', 'list'] })
    },
  })

  const usageQuery = useQuery({ queryKey: ['ai', 'usage'], queryFn: fetchAiUsage })

  // 전에는 "7일 안에 받은 것만" 보여줬는데, 목록에는 더 오래된 회고가 있는데도 오른쪽은 "아직 받은
  // 회고가 없어요"로 나오는 모순이 생겼다(지시문 5) — latestQuery는 정말 하나도 없을 때만 null/204를
  // 주므로(CoachService.latest), 나이 제한 없이 그대로 쓰면 된다.
  const shownResult = result ?? (selectedId != null ? selectedQuery.data ?? null : latestQuery.data ?? null)

  // 자동 회고를 열면 한 번만 viewed 처리한다(이 화면에 머무는 동안 중복 호출 방지).
  useEffect(() => {
    if (!shownResult?.isNew || shownResult.id == null || viewedIds.current.has(shownResult.id)) return
    viewedIds.current.add(shownResult.id)
    markCoachViewed(shownResult.id)
      .then((updated) => {
        queryClient.setQueryData(['ai', 'coach', 'latest'], updated)
        setResult((prev) => (prev && prev.id === updated.id ? updated : prev))
      })
      .catch(() => {
        viewedIds.current.delete(shownResult.id!)
      })
  }, [shownResult?.id, shownResult?.isNew, queryClient])

  function selectResult(id: number) {
    setResult(null)
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev)
      next.set('r', String(id))
      return next
    })
  }

  function clearFilters() {
    setQueryInput('')
    setDebouncedQuery('')
    setDateFilter('ALL')
    setCustomFrom('')
    setCustomTo('')
    setPage(0)
  }

  if (userLoading || hobbiesLoading || (selectedId == null && latestQuery.isLoading)) {
    return <Loading />
  }

  if (!currentUser?.aiEnabled) {
    return (
      <div className="flex flex-col gap-4">
        <ReviewHeaderRow title="AI 회고" />
        <Note variant="calm">
          <span>
            AI 정리가 꺼져 있어요. 켜면 기간을 돌아보는 회고를 받을 수 있어요.{' '}
            <Link to="/settings/ai" className="font-medium text-base-ink underline underline-offset-4">
              켜기
            </Link>
          </span>
        </Note>
      </div>
    )
  }

  // 탭 박스 아래 한 줄: 분야 필터만(지시문 5). 기간·[회고 받기]는 오른쪽 "새 회고 받기" 카드로 옮겼다.
  const hobbyFilter =
    activeHobbies && activeHobbies.length > 0 ? (
      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          aria-pressed={effectiveHobbyId === null}
          onClick={() => {
            setHobbyId(null)
            setPage(0)
          }}
          className={pickClass(effectiveHobbyId === null)}
        >
          전체
        </button>
        {activeHobbies.map((hobby) => (
          <button
            key={hobby.id}
            type="button"
            aria-pressed={effectiveHobbyId === hobby.id}
            onClick={() => {
              setHobbyId(hobby.id)
              setPage(0)
            }}
            className={pickClass(effectiveHobbyId === hobby.id)}
          >
            {hobby.name}
          </button>
        ))}
      </div>
    ) : null

  const newCoachCard = (
    <div className="flex flex-col gap-3 rounded-2xl border border-line-2 bg-white p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="inline-flex rounded-lg border border-line-2 bg-white p-[0.1875rem]" role="group" aria-label="회고 기간">
          {PERIODS.map((item) => (
            <button
              key={item.value}
              type="button"
              aria-pressed={period === item.value}
              onClick={() => setPeriod(item.value)}
              className={[
                'min-h-9 whitespace-nowrap rounded-md px-3 text-sm font-medium',
                period === item.value ? 'bg-ink text-white' : 'bg-transparent text-ink-2',
              ].join(' ')}
            >
              {item.label}
            </button>
          ))}
        </div>
        <button
          type="button"
          disabled={mutation.isPending}
          onClick={() => mutation.mutate()}
          className="inline-flex min-h-10 w-fit items-center justify-center rounded-lg bg-base px-5 text-body-min font-medium text-white disabled:bg-line disabled:text-[#5F594F]"
        >
          {shownResult ? '새 회고 받기' : '회고 받기'}
        </button>
      </div>
      {usageQuery.data ? (
        <p className="text-caption-min text-ink-2">오늘 남은 횟수 {usageQuery.data.remaining}/{usageQuery.data.limit}</p>
      ) : null}
    </div>
  )

  const detail = (
    <div className="flex flex-col gap-5">
      {mutation.isPending ? (
        <div className="flex flex-col gap-3.5 rounded-2xl bg-surface p-7">
          <p className="text-ink">기록을 천천히 읽고 있어요</p>
          <div className="h-3.5 w-[80%] animate-pulse rounded-full bg-rest" />
          <div className="h-3.5 w-full animate-pulse rounded-full bg-rest" />
        </div>
      ) : null}

      {mutation.isError ? (
        <div className="flex flex-col gap-3">
          <Note variant="sand">
            {mutation.error instanceof ApiError ? mutation.error.message : '잠시 후 다시 시도해 주세요'}
          </Note>
          <button
            type="button"
            onClick={() => mutation.mutate()}
            className="inline-flex min-h-11 w-fit items-center text-sm font-medium text-base-ink underline underline-offset-4"
          >
            다시 받기
          </button>
        </div>
      ) : null}

      {/* 불러오는 중에는 빈 안내 대신 자리 표시(스켈레톤)를 먼저 보여준다(지시문 5). */}
      {selectedId != null && selectedQuery.isLoading && !mutation.isPending ? (
        <div className="flex flex-col gap-3.5 rounded-2xl bg-surface p-7">
          <div className="h-3.5 w-1/3 animate-pulse rounded-full bg-rest" />
          <div className="h-3.5 w-full animate-pulse rounded-full bg-rest" />
          <div className="h-3.5 w-[85%] animate-pulse rounded-full bg-rest" />
        </div>
      ) : null}
      {selectedId != null && selectedQuery.isError ? (
        <Note variant="sand">
          {selectedQuery.error instanceof ApiError && selectedQuery.error.status === 404
            ? '회고를 찾을 수 없어요.'
            : '잠시 후 다시 시도해 주세요'}
        </Note>
      ) : null}

      {shownResult?.locked ? (
        <div className="flex flex-col items-start gap-3 rounded-2xl bg-surface p-7">
          <p className="text-ink">기록이 5개 쌓이면 첫 회고를 받아요</p>
          <p className="text-sm font-medium text-ink-2">{shownResult.currentCount ?? 0}/{shownResult.requiredCount ?? 5}</p>
          <Link to="/records/new" className="inline-flex min-h-11 items-center text-sm font-medium text-base-ink underline underline-offset-4">
            기록하기
          </Link>
        </div>
      ) : null}

      {shownResult && !shownResult.locked ? (
        <CoachResultView result={shownResult} onResultChange={(updated) => setResult(updated)} />
      ) : null}

      {!shownResult && !mutation.isPending && selectedId == null && !selectedQuery.isLoading ? (
        <Note variant="calm">아직 받은 회고가 없어요. [회고 받기]를 눌러 보세요.</Note>
      ) : null}
    </div>
  )

  const items = listQuery.data?.items ?? []
  const totalPages = Math.ceil((listQuery.data?.totalCount ?? 0) / pageSize)

  const listPanel = (
    <div className="flex flex-col gap-4">
      <div className="relative">
        <input
          type="search"
          value={queryInput}
          onChange={(event) => {
            setQueryInput(event.target.value)
            setPage(0)
          }}
          placeholder="회고 내용 검색"
          className="h-11 w-full rounded-lg border border-line-2 bg-white px-4 pr-10 text-body-min text-ink placeholder:text-ink-3"
        />
        {queryInput ? (
          <button
            type="button"
            onClick={() => {
              setQueryInput('')
              setPage(0)
            }}
            aria-label="검색어 지우기"
            className="absolute right-2 top-1/2 inline-flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-full text-ink-2"
          >
            ×
          </button>
        ) : null}
      </div>

      <div className="flex flex-wrap gap-2">
        {([
          ['ALL', '전체'],
          ['30D', '최근 30일'],
          ['MONTH', '이번 달'],
          ['CUSTOM', '직접 선택'],
        ] as [DateFilter, string][]).map(([value, label]) => (
          <button
            key={value}
            type="button"
            aria-pressed={dateFilter === value}
            onClick={() => {
              setDateFilter(value)
              setPage(0)
            }}
            className={pickClass(dateFilter === value)}
          >
            {label}
          </button>
        ))}
      </div>

      {dateFilter === 'CUSTOM' ? (
        <div className="flex items-center gap-2">
          <input
            type="date"
            value={customFrom}
            onChange={(event) => {
              setCustomFrom(event.target.value)
              setPage(0)
            }}
            className="h-11 flex-1 rounded-lg border border-line-2 bg-white px-3 text-sm text-ink"
          />
          <span className="text-ink-2">–</span>
          <input
            type="date"
            value={customTo}
            onChange={(event) => {
              setCustomTo(event.target.value)
              setPage(0)
            }}
            className="h-11 flex-1 rounded-lg border border-line-2 bg-white px-3 text-sm text-ink"
          />
        </div>
      ) : null}

      {listQuery.isLoading ? <Loading /> : null}
      {listQuery.isError ? <Note variant="sand">잠시 후 다시 시도해 주세요</Note> : null}

      {listQuery.data && items.length === 0 ? (
        <div className="flex flex-col items-start gap-2 rounded-2xl bg-surface p-5">
          <p className="text-sm text-ink-2">조건에 맞는 회고가 없어요.</p>
          <button
            type="button"
            onClick={clearFilters}
            className="inline-flex min-h-9 items-center text-sm font-medium text-base-ink underline underline-offset-4"
          >
            필터 지우기
          </button>
        </div>
      ) : null}

      {items.length > 0 ? (
        <div ref={containerRef} className="scrollbar-thin-fade flex flex-col gap-2 lg:min-h-0 lg:flex-1 lg:overflow-y-auto">
          {items.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => selectResult(item.id)}
              aria-current={selectedId === item.id || (selectedId == null && shownResult?.id === item.id)}
              className={[
                'flex items-center gap-3 rounded-xl border px-4 py-3 text-left',
                selectedId === item.id || (selectedId == null && shownResult?.id === item.id)
                  ? 'border-base bg-[color-mix(in_srgb,var(--color-base)_9%,white)]'
                  : 'border-line bg-white',
              ].join(' ')}
            >
              <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                <span className="flex items-center gap-1.5 text-caption-min font-semibold tabular-nums text-ink-2">
                  {formatMonthDay(item.createdAt.slice(0, 10))}
                  {item.hobbyName ? ` · ${item.hobbyName}` : ''}
                  {item.isNew ? (
                    <span className="inline-flex h-5 items-center rounded-md bg-[color-mix(in_srgb,var(--color-base)_16%,white)] px-1.5 text-[0.6875rem] font-medium text-base-ink">
                      새 회고
                    </span>
                  ) : null}
                </span>
                <span className="truncate text-body-min text-ink">{item.nextActionTitle ?? item.summary ?? '회고 결과'}</span>
              </span>
              <ChevronRightIcon width={16} height={16} className="flex-none text-ink-2" />
            </button>
          ))}
        </div>
      ) : null}

      {totalPages > 1 ? (
        <div className="flex items-center justify-between gap-2">
          <button
            type="button"
            disabled={page <= 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            className="inline-flex min-h-9 items-center text-sm font-medium text-base-ink underline underline-offset-4 disabled:text-ink-3 disabled:no-underline"
          >
            이전
          </button>
          <span className="text-caption-min text-ink-2">{page + 1} / {totalPages}</span>
          <button
            type="button"
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
            className="inline-flex min-h-9 items-center text-sm font-medium text-base-ink underline underline-offset-4 disabled:text-ink-3 disabled:no-underline"
          >
            다음
          </button>
        </div>
      ) : null}
    </div>
  )

  // 모바일: r이 있으면 상세만, 없으면 목록만 보여준다(위아래로 쌓지 않는다).
  if (!isDesktop) {
    return (
      <div className="flex flex-col gap-5">
        {selectedId != null ? (
          <>
            <nav className="text-sm text-ink-2">
              <button
                type="button"
                onClick={() =>
                  setSearchParams((prev) => {
                    const next = new URLSearchParams(prev)
                    next.delete('r')
                    return next
                  })
                }
                className="inline-flex items-center gap-1 bg-transparent p-0 text-ink-2"
              >
                ← 목록으로
              </button>
            </nav>
            {detail}
          </>
        ) : (
          <>
            <ReviewHeaderRow title="AI 회고" />
            {hobbyFilter}
            {newCoachCard}
            {listPanel}
          </>
        )}
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-4 lg:min-h-0 lg:flex-1">
      <ReviewHeaderRow title="AI 회고" />
      {hobbyFilter}
      <SplitWide main={listPanel} side={<>{newCoachCard}{detail}</>} className="lg:min-h-0 lg:flex-1" />
    </div>
  )
}
