import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronRightIcon } from '../components/icons'
import { CoachResultView } from '../components/CoachResultView'
import { FilterBar } from '../components/FilterBar'
import { ListPane } from '../components/ListPane'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
import { Pagination } from '../components/Pagination'
import { ReviewHeaderRow } from '../components/ReviewHeaderRow'
import { SplitWide } from '../components/PageLayout'
import { useCurrentUser } from '../hooks/useCurrentUser'
import { useFilterBarQuery, resolveDateRange } from '../hooks/useFilterBarQuery'
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

export function CoachPage() {
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const isDesktop = useIsDesktop()
  const { data: currentUser, isLoading: userLoading } = useCurrentUser()
  const { data: activeHobbies, isLoading: hobbiesLoading } = useHobbies(false)
  const filter = useFilterBarQuery()
  const { from: listFrom, to: listTo } = resolveDateRange(filter.range)
  const [period, setPeriod] = useState<CoachPeriod>('2W')
  const [result, setResult] = useState<CoachResult | null>(null)
  const [autoStatus, setAutoStatus] = useState<CoachAutoResponse | null>(null)
  const viewedIds = useRef(new Set<number>())
  const autoTriggeredRef = useRef(false)
  const [page, setPage] = useState(0)
  const [periodPopoverOpen, setPeriodPopoverOpen] = useState(false)
  const popoverRef = useRef<HTMLDivElement>(null)

  // P1 3단계: 1행에는 [새 회고 받기] 버튼 하나만 두고, 기간·남은 횟수는 눌렀을 때만 열리는 작은
  // 팝오버에 넣는다(본문의 큰 카드를 없애 머리줄 높이를 줄인다).
  useEffect(() => {
    if (!periodPopoverOpen) return
    function handlePointerDown(event: PointerEvent) {
      if (popoverRef.current && !popoverRef.current.contains(event.target as Node)) setPeriodPopoverOpen(false)
    }
    function handleKey(event: KeyboardEvent) {
      if (event.key === 'Escape') setPeriodPopoverOpen(false)
    }
    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleKey)
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('keydown', handleKey)
    }
  }, [periodPopoverOpen])

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
    enabled: !!currentUser?.aiEnabled && selectedId == null,
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
          if (selectedId == null) {
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
            <Link to="/settings/ai" className="font-medium text-base-ink underline underline-offset-4">
              켜기
            </Link>
          </span>
        </Note>
      </div>
    )
  }

  // 머리줄 1행 오른쪽 액션(P1 3단계): [새 회고 받기] 버튼 하나만 두고, 누르면 아래에 기간(라디오)과
  // 남은 횟수·[받기]가 있는 작은 팝오버가 열린다. 2행이나 본문에 큰 카드를 두지 않는다.
  const headerAction = (
    <div className="relative" ref={popoverRef}>
      <button
        type="button"
        aria-haspopup="dialog"
        aria-expanded={periodPopoverOpen}
        onClick={() => setPeriodPopoverOpen((prev) => !prev)}
        className={
          shownResult
            ? 'inline-flex min-h-10 w-fit flex-none items-center justify-center rounded-lg border border-line-2 bg-white px-4 text-sm font-medium text-ink disabled:text-ink-3'
            : 'inline-flex min-h-10 w-fit flex-none items-center justify-center rounded-lg bg-base px-4 text-sm font-medium text-white disabled:bg-line disabled:text-[#5F594F]'
        }
      >
        {shownResult ? '새 회고 받기' : '회고 받기'}
      </button>

      {periodPopoverOpen ? (
        <div className="absolute right-0 top-full z-20 mt-1.5 w-[15rem] rounded-lg border border-line-2 bg-white p-3 shadow-sm">
          <div role="radiogroup" aria-label="회고 기간" className="flex flex-col gap-0.5">
            {PERIODS.map((item) => (
              <label
                key={item.value}
                className="flex min-h-10 items-center gap-2 rounded-md px-2 text-sm text-ink has-[:checked]:bg-surface has-[:checked]:font-semibold"
              >
                <input
                  type="radio"
                  name="coach-period"
                  checked={period === item.value}
                  onChange={() => setPeriod(item.value)}
                  className="h-4 w-4 accent-[var(--color-base)]"
                />
                {item.label}
              </label>
            ))}
          </div>
          {usageQuery.data ? (
            <p className="mt-1 px-2 text-caption-min text-ink-2">오늘 {usageQuery.data.remaining}번 남았어요</p>
          ) : null}
          <button
            type="button"
            disabled={mutation.isPending}
            onClick={() => {
              mutation.mutate()
              setPeriodPopoverOpen(false)
            }}
            className="btn-min mt-2 inline-flex w-full items-center justify-center rounded-lg bg-base px-4 text-sm font-medium text-white disabled:bg-line disabled:text-[#5F594F]"
          >
            받기
          </button>
        </div>
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

      {mutation.isPending ? (
        <div className="flex flex-col gap-3.5 rounded-2xl bg-surface p-7">
          <p className="text-ink">기록을 천천히 읽고 있어요</p>
          <div className="h-3.5 w-[80%] animate-pulse rounded-full bg-rest" />
          <div className="h-3.5 w-full animate-pulse rounded-full bg-rest" />
        </div>
      ) : null}

      {showAutoGeneratingSkeleton ? (
        <div className="flex flex-col gap-3.5 rounded-2xl bg-surface p-7">
          <p className="text-ink">회고를 정리하고 있어요</p>
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
            <Link to="/records/new" className="inline-flex min-h-11 items-center text-sm font-medium text-base-ink underline underline-offset-4">
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
        {listQuery.isLoading ? <Loading /> : null}
        {listQuery.isError ? <Note variant="sand">잠시 후 다시 시도해 주세요</Note> : null}

        {listQuery.data && items.length === 0 ? (
          <div className="flex flex-col items-start gap-2 rounded-2xl bg-surface p-5">
            <p className="text-sm text-ink-2">조건에 맞는 회고가 없어요.</p>
            {filter.hasActiveFilter ? (
              <button
                type="button"
                onClick={filter.reset}
                className="inline-flex min-h-9 items-center text-sm font-medium text-base-ink underline underline-offset-4"
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
          <p className="text-sm text-ink">
            기록 {nextCoachRemaining}/{nextCoachRequired} · {nextCoachRequired}개가 쌓이면 회고가 자동으로 도착해요
          </p>
          <div className="h-2 w-full overflow-hidden rounded-full bg-rest">
            <div
              className="h-full rounded-full bg-base"
              style={{ width: `${Math.min(100, (nextCoachRemaining / Math.max(1, nextCoachRequired)) * 100)}%` }}
            />
          </div>
          <Link to="/records/new" className="inline-flex min-h-9 items-center text-sm font-medium text-base-ink underline underline-offset-4">
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
            <ReviewHeaderRow title="AI 회고" action={headerAction} />
            {filterBar}
            {listPanel}
          </>
        )}
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-4 lg:min-h-0 lg:flex-1">
      <ReviewHeaderRow title="AI 회고" action={headerAction} />
      {filterBar}
      <SplitWide main={listPanel} side={detail} className="lg:min-h-0 lg:flex-1" />
    </div>
  )
}
