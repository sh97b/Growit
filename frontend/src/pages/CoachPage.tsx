import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AiProgressCard, Spinner } from '../components/AiProgress'
import { ChevronRightIcon } from '../components/icons'
import { CoachResultView } from '../components/CoachResultView'
import { FilterBar } from '../components/FilterBar'
import { ListPane } from '../components/ListPane'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
import { Pagination } from '../components/Pagination'
import { ReviewHeaderRow } from '../components/ReviewHeaderRow'
import { useCurrentUser } from '../hooks/useCurrentUser'
import { useFilterBarQuery, resolveDateRange } from '../hooks/useFilterBarQuery'
import type { DateRangeValue } from '../components/FilterBar'
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
  generateAutoCoach,
  markCoachViewed,
  type CoachAutoResponse,
  type CoachPeriod,
  type CoachResult,
} from '../lib/ai'
import { formatMonthDay } from '../lib/date'

const AUTO_CALL_STORAGE_KEY = 'growit_coach_auto_last_call'
const AUTO_CALL_INTERVAL_MS = 10 * 60 * 1000

const PERIODS: { value: CoachPeriod; label: string }[] = [
  { value: 'THIS_WEEK', label: '이번 주' },
  { value: '2W', label: '최근 2주' },
  { value: '1M', label: '한 달' },
]

const LIST_ROW_HEIGHT_REM = 5.5

/**
 * 회고를 만드는 기간은 필터의 [기간] 선택에서 정한다(별도 기간 팝오버 없음). 서버가 받는 기간은
 * 이번 주·최근 2주·한 달 세 가지라서 고른 범위의 길이에 가장 가까운 것으로 옮긴다.
 */
function periodFromRange(range: DateRangeValue): CoachPeriod {
  if (range.range !== 'CUSTOM' || !range.from || !range.to) return '1M'
  const days = Math.round((Date.parse(range.to) - Date.parse(range.from)) / 86_400_000) + 1
  if (days <= 7) return 'THIS_WEEK'
  if (days <= 14) return '2W'
  return '1M'
}

export function CoachPage() {
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const isDesktop = useIsDesktop()
  const { data: currentUser, isLoading: userLoading } = useCurrentUser()
  const { data: activeHobbies, isLoading: hobbiesLoading } = useHobbies(false)
  const filter = useFilterBarQuery()
  const { from: listFrom, to: listTo } = resolveDateRange(filter.range)
  const period = periodFromRange(filter.range)
  const [result, setResult] = useState<CoachResult | null>(null)
  const [autoStatus, setAutoStatus] = useState<CoachAutoResponse | null>(null)
  const viewedIds = useRef(new Set<number>())
  const autoTriggeredRef = useRef(false)
  const [page, setPage] = useState(0)
  const justGeneratedIdRef = useRef<number | null>(null)
  const resultRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    setPage(0)
  }, [filter.hobbyId, filter.q, filter.range.range, filter.range.from, filter.range.to])

  const selectedId = Number(searchParams.get('r')) || null

  // S1 A단계: ready로 렌더·쿼리를 막지 않는다 — 기본값(또는 직전 값)으로 즉시 그린다.
  const { containerRef, pageSize: fitPageSize } = useFitPageSize({
    itemHeightRem: LIST_ROW_HEIGHT_REM,
    min: 2,
    max: 12,
    defaultSize: 6,
  })
  const pageSize = isDesktop ? fitPageSize : 6
  useStablePageOnResize(page, setPage, pageSize)

  const listQuery = useQuery({
    queryKey: ['ai', 'coach', 'list', filter.hobbyId, filter.q, listFrom, listTo, page, pageSize],
    queryFn: () =>
      fetchCoachList({
        hobbyId: filter.hobbyId ?? undefined,
        q: filter.q || undefined,
        from: listFrom,
        to: listTo,
        page,
        size: pageSize,
      }),
    enabled: !!currentUser?.aiEnabled,
    placeholderData: keepPreviousData,
  })

  // P1 2단계: 검색·필터로 총 페이지가 줄어들어 현재 페이지가 범위를 벗어나면 마지막 페이지로 보정한다.
  useEffect(() => {
    const total = listQuery.data?.totalCount
    if (total == null) return
    const lastPage = Math.max(0, Math.ceil(total / pageSize) - 1)
    if (page > lastPage) setPage(lastPage)
  }, [listQuery.data?.totalCount, pageSize, page])

  const latestQuery = useQuery({
    queryKey: ['ai', 'coach', 'latest'],
    queryFn: fetchLatestCoach,
    // 웹은 회고를 고르기 전에는 오른쪽 상세를 닫아 두므로 최신 회고를 미리 불러오지 않는다.
    enabled: !!currentUser?.aiEnabled && selectedId == null && !isDesktop,
  })

  const selectedQuery = useQuery({
    queryKey: ['ai', 'results', selectedId],
    queryFn: () => fetchCoachResult(selectedId!),
    enabled: selectedId != null,
  })

  const mutation = useMutation({
    mutationFn: () => fetchCoach({ hobbyId: filter.hobbyId ?? undefined, period }),
    onSuccess: (data) => {
      setResult(data)
      justGeneratedIdRef.current = data.id
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

  const autoMutation = useMutation({ mutationFn: generateAutoCoach })

  // O1(E): 회고 탭에 들어오면(홈과 별개로, 같은 세션 10분 안에는 다시 안 부름) 자동 생성 조건을
  // 확인한다. 생성·재사용됐고 지금 특정 회고를 보고 있지 않으면 그 회고를 자동 선택해 보여준다.
  useEffect(() => {
    if (!currentUser?.aiEnabled || autoTriggeredRef.current) return
    autoTriggeredRef.current = true

    let lastCalledAt: number | null = null
    try {
      const raw = sessionStorage.getItem(AUTO_CALL_STORAGE_KEY)
      lastCalledAt = raw ? Number(raw) : null
    } catch {
      // 세션 저장소를 못 쓰면 그냥 매번 호출한다 — 서버가 멱등이라 안전하다.
    }
    if (lastCalledAt != null && Date.now() - lastCalledAt < AUTO_CALL_INTERVAL_MS) return

    autoMutation.mutate(undefined, {
      onSuccess: (data) => {
        try {
          sessionStorage.setItem(AUTO_CALL_STORAGE_KEY, String(Date.now()))
        } catch {
          // 저장 실패는 무시한다(다음 접속에서 다시 시도될 뿐).
        }
        setAutoStatus(data)
        if ((data.status === 'GENERATED' || data.status === 'EXISTS') && data.result) {
          queryClient.invalidateQueries({ queryKey: ['ai', 'coach', 'list'] })
          // 웹은 자동 생성돼도 상세를 저절로 열지 않는다(목록의 새 회고 점으로 알린다).
          if (selectedId == null && !isDesktop) {
            setResult(data.result)
            if (data.result.id != null) {
              setSearchParams((prev) => {
                const next = new URLSearchParams(prev)
                next.set('r', String(data.result!.id))
                return next
              })
            }
          } else {
            queryClient.invalidateQueries({ queryKey: ['ai', 'coach', 'latest'] })
          }
        }
      },
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentUser?.aiEnabled])

  // 전에는 "7일 안에 받은 것만" 보여줬는데, 목록에는 더 오래된 회고가 있는데도 오른쪽은 "아직 받은
  // 회고가 없어요"로 나오는 모순이 생겼다(지시문 5) — latestQuery는 정말 하나도 없을 때만 null/204를
  // 주므로(CoachService.latest), 나이 제한 없이 그대로 쓰면 된다.
  const shownResult = result ?? (selectedId != null ? selectedQuery.data ?? null : latestQuery.data ?? null)

  // 방금 만든 회고가 화면에 나오면 그쪽으로 스크롤하고 포커스를 옮긴다(움직임 줄이기 설정이면 부드럽게 하지 않는다).
  useEffect(() => {
    if (shownResult?.id == null || justGeneratedIdRef.current !== shownResult.id) return
    justGeneratedIdRef.current = null
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    resultRef.current?.scrollIntoView({ behavior: reduce ? 'auto' : 'smooth', block: 'start' })
    resultRef.current?.focus({ preventScroll: true })
  }, [shownResult?.id])

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
            <Link to="/settings/ai" className="font-medium text-base-ink no-underline rounded-full px-2 -mx-2 hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)] focus-visible:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]">
              켜기
            </Link>
          </span>
        </Note>
      </div>
    )
  }

  // [새 회고 받기]: 필터의 분야·기간 바로 옆(모바일은 바로 아래 전체 폭)에 두고, 누르면 그 분야·기간으로 바로 만든다.
  const generateButton = (
    <div className="flex flex-col gap-1.5">
      <button
        type="button"
        disabled={mutation.isPending}
        onClick={() => mutation.mutate()}
        className="inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-lg bg-base px-5 text-body-min font-medium text-white disabled:bg-line disabled:text-[#5F594F] lg:h-10 lg:min-h-10 lg:w-auto"
      >
        {mutation.isPending ? (
          <>
            <Spinner />
            회고를 만들고 있어요
          </>
        ) : shownResult ? (
          '새 회고 받기'
        ) : (
          '회고 받기'
        )}
      </button>
      {usageQuery.data ? (
        <p className="text-center text-caption-min text-ink-2 lg:hidden">
          {PERIODS.find((item) => item.value === period)?.label} 기록으로 만들어요 · 오늘 {usageQuery.data.remaining}번 남았어요
        </p>
      ) : null}
    </div>
  )

  // O1(E): 자동 생성이 진행 중이고 아직 보여줄 회고가 없으면(처음 들어왔을 때) 생성 중 자리 표시를
  // 먼저 보여준다. 이미 보여줄 회고가 있으면(최신 회고 등) 조용히 배경에서만 확인하고 방해하지 않는다.
  const showAutoGeneratingSkeleton =
    autoMutation.isPending && selectedId == null && !shownResult && !mutation.isPending

  // P1 4단계: "새로 도착" 표시는 이제 CoachResultView의 메타 줄 배지 하나로 충분해(isNew),
  // 여기서 별도의 큰 색 배너를 그리지 않는다.
  const detail = (
    <div className="flex flex-col gap-5">

      {showAutoGeneratingSkeleton ? (
        <div className="flex flex-col gap-3.5 rounded-2xl bg-surface p-7">
          <p className="text-ink">회고를 정리하고 있어요</p>
          <div className="h-3.5 w-[80%] animate-pulse rounded-full bg-rest" />
          <div className="h-3.5 w-full animate-pulse rounded-full bg-rest" />
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
          <p className="t-sub font-medium text-ink-2">{shownResult.currentCount ?? 0}/{shownResult.requiredCount ?? 5}</p>
          <Link to="/records/new" className="inline-flex min-h-11 items-center t-sub font-medium text-base-ink no-underline rounded-full px-2 -mx-2 hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)] focus-visible:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]">
            기록하기
          </Link>
        </div>
      ) : null}

      {shownResult && !shownResult.locked ? (
        <div ref={resultRef} tabIndex={-1} className="outline-none">
          <CoachResultView result={shownResult} onResultChange={(updated) => setResult(updated)} />
        </div>
      ) : null}

      {!shownResult &&
      !mutation.isPending &&
      !showAutoGeneratingSkeleton &&
      selectedId == null &&
      !selectedQuery.isLoading ? (
        autoStatus?.status === 'NOT_ELIGIBLE' && !autoStatus.hasAnyCoach ? (
          <div className="flex flex-col items-start gap-3 rounded-2xl bg-surface p-7">
            <p className="text-ink">
              기록 {autoStatus.newRecordsSinceLast}/{autoStatus.requiredCount} · {autoStatus.requiredCount}개가 쌓이면 회고가
              자동으로 도착해요
            </p>
            <div className="h-2 w-full max-w-[16rem] overflow-hidden rounded-full bg-rest">
              <div
                className="h-full rounded-full bg-base"
                style={{
                  width: `${Math.min(100, (autoStatus.newRecordsSinceLast / Math.max(1, autoStatus.requiredCount)) * 100)}%`,
                }}
              />
            </div>
            <Link to="/records/new" className="inline-flex min-h-11 items-center t-sub font-medium text-base-ink no-underline rounded-full px-2 -mx-2 hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)] focus-visible:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]">
              기록하기
            </Link>
          </div>
        ) : (
          <Note variant="calm">아직 받은 회고가 없어요. [회고 받기]를 눌러 보세요.</Note>
        )
      ) : null}
    </div>
  )

  const items = listQuery.data?.items ?? []
  const totalPages = Math.ceil((listQuery.data?.totalCount ?? 0) / pageSize)

  const filterBar = (
    <FilterBar
      search={{ value: filter.q, onChange: filter.setQ, placeholder: '회고 내용 검색' }}
      hobby={{ value: filter.hobbyId, options: activeHobbies ?? [], onChange: filter.setHobbyId }}
      range={{ value: filter.range, onChange: filter.setRange }}
      hasActiveFilter={filter.hasActiveFilter}
      onReset={filter.reset}
      trailing={generateButton}
    />
  )

  // P2 4번: 목록을 "받은 회고 N개" 카드로 감싸고, 그 아래 항상 "다음 회고" 카드를 둬서(진행 n/5)
  // 회고가 1개뿐이어도 왼쪽 열이 비어 보이지 않게 한다.
  const nextCoachRemaining = autoStatus?.newRecordsSinceLast
  const nextCoachRequired = autoStatus?.requiredCount ?? 5

  const listPanel = (
    <div className="flex flex-col gap-3 lg:h-full lg:min-h-0">
      <div className="flex flex-col gap-3 rounded-2xl border border-line bg-white p-5 lg:min-h-0 lg:flex-1">
        <h2 className="flex-none text-card-title text-ink">받은 회고 {listQuery.data?.totalCount ?? 0}개</h2>
        <ListPane
          listRef={containerRef}
          className="lg:min-h-0 lg:flex-1"
          footer={<Pagination page={page} totalPages={totalPages} onChange={setPage} />}
        >
      <div className="flex flex-col gap-4">
        {mutation.isPending ? (
          <AiProgressCard message="AI가 회고를 만들고 있어요. 잠시만 기다려 주세요" lines={3} />
        ) : null}
        {mutation.isError ? (
          <div className="flex flex-col gap-3">
            <Note variant="sand">
              {mutation.error instanceof ApiError ? mutation.error.message : '지금은 회고를 만들지 못했어요. 기록은 그대로 있어요.'}
            </Note>
            <button
              type="button"
              onClick={() => mutation.mutate()}
              className="inline-flex min-h-11 w-fit items-center t-sub font-medium text-base-ink no-underline rounded-full px-2 -mx-2 hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)] focus-visible:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]"
            >
              다시 시도
            </button>
          </div>
        ) : null}
        {listQuery.isLoading ? <Loading /> : null}
        {listQuery.isError ? <Note variant="sand">잠시 후 다시 시도해 주세요</Note> : null}

        {listQuery.data && items.length === 0 ? (
          <div className="flex flex-col items-start gap-2 rounded-2xl bg-surface p-5">
            <p className="t-sub text-ink-2">조건에 맞는 회고가 없어요.</p>
            {filter.hasActiveFilter ? (
              <button
                type="button"
                onClick={filter.reset}
                className="inline-flex min-h-9 items-center t-sub font-medium text-base-ink no-underline rounded-full px-2 -mx-2 hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)] focus-visible:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]"
              >
                필터 지우기
              </button>
            ) : null}
          </div>
        ) : null}

        {items.length > 0 ? (
          <div className="flex flex-col gap-2">
            {items.map((item) => (
              <button
                key={item.id}
                type="button"
                onClick={() => selectResult(item.id)}
                aria-current={selectedId === item.id || (selectedId == null && shownResult?.id === item.id)}
                className={[
                  'flex items-center gap-3 rounded-xl border px-4 py-3 text-left',
                  selectedId === item.id || (selectedId == null && shownResult?.id === item.id)
                    ? 'border-line-2 bg-surface'
                    : 'border-line bg-white',
                ].join(' ')}
              >
                <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                  <span className="flex items-center gap-1.5 text-caption-min text-ink-2">
                    {formatMonthDay(item.createdAt.slice(0, 10))} · {item.hobbyName ?? '전체'}
                    {item.isNew ? (
                      <span aria-label="새 회고" className="h-1.5 w-1.5 flex-none rounded-full bg-base" />
                    ) : null}
                  </span>
                  <span className="truncate text-body-min text-ink">{item.nextActionTitle ?? item.summary ?? '회고 결과'}</span>
                </span>
                <ChevronRightIcon width={16} height={16} className="flex-none text-ink-2" />
              </button>
            ))}
          </div>
        ) : null}
      </div>
        </ListPane>
      </div>

      {nextCoachRemaining != null ? (
        <div className="flex flex-none flex-col items-start gap-2 rounded-2xl border border-line bg-white p-5">
          <p className="t-sub text-ink">
            기록 {nextCoachRemaining}/{nextCoachRequired} · {nextCoachRequired}개가 쌓이면 회고가 자동으로 도착해요
          </p>
          <div className="h-2 w-full overflow-hidden rounded-full bg-rest">
            <div
              className="h-full rounded-full bg-base"
              style={{ width: `${Math.min(100, (nextCoachRemaining / Math.max(1, nextCoachRequired)) * 100)}%` }}
            />
          </div>
          <Link to="/records/new" className="inline-flex min-h-9 items-center t-sub font-medium text-base-ink no-underline rounded-full px-2 -mx-2 hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)] focus-visible:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]">
            기록하기
          </Link>
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
            <nav className="t-sub text-ink-2">
              <button
                type="button"
                onClick={() =>
                  setSearchParams((prev) => {
                    const next = new URLSearchParams(prev)
                    next.delete('r')
                    return next
                  })
                }
                className="inline-flex min-h-9 items-center gap-1 bg-transparent p-0 text-caption-min text-ink-2"
              >
                ‹ 회고
              </button>
            </nav>
            {detail}
          </>
        ) : (
          <>
            <ReviewHeaderRow title="AI 회고" />
            {filterBar}
            {listPanel}
          </>
        )}
      </div>
    )
  }

  // 웹: 회고를 고르기 전에는 목록이 전체 폭을 쓰고, 고르면 목록이 줄면서 오른쪽에서 상세가 밀려 나온다(움직임 줄이기 설정이면 바로 바뀐다).
  const detailOpen = selectedId != null || result != null
  function closeDetail() {
    setResult(null)
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev)
      next.delete('r')
      return next
    })
  }
  const columnClass = 'flex min-h-0 min-w-0 flex-col gap-5 overflow-y-auto overflow-x-hidden overscroll-contain scrollbar-thin-fade'

  return (
    <div className="flex flex-col gap-4 lg:min-h-0 lg:flex-1">
      <ReviewHeaderRow title="AI 회고" />
      {filterBar}
      <div
        className="grid min-h-0 flex-1 transition-[grid-template-columns,column-gap] duration-300 ease-out motion-reduce:transition-none"
        style={{ gridTemplateColumns: detailOpen ? '5fr 7fr' : '1fr 0fr', columnGap: detailOpen ? '2rem' : '0rem' }}
      >
        <div className={columnClass}>{listPanel}</div>
        <div className="min-h-0 min-w-0 overflow-hidden">
          <div
            inert={!detailOpen}
            aria-hidden={!detailOpen}
            className={[
              columnClass,
              'h-full min-w-[30rem] transition-[opacity,transform] duration-300 ease-out motion-reduce:transition-none',
              detailOpen ? 'translate-x-0 opacity-100' : 'translate-x-8 opacity-0',
            ].join(' ')}
          >
            <button
              type="button"
              onClick={closeDetail}
              className="t-sub inline-flex min-h-9 w-fit flex-none items-center gap-1 rounded-full bg-transparent px-2 -mx-2 font-medium text-ink-2 hover:text-ink"
            >
              <ChevronRightIcon width={14} height={14} className="rotate-180" aria-hidden="true" />
              목록만 보기
            </button>
            {detail}
          </div>
        </div>
      </div>
    </div>
  )
}
