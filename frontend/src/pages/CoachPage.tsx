import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronRightIcon } from '../components/icons'
import { CoachResultView } from '../components/CoachResultView'
import { FilterBar } from '../components/FilterBar'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
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

  useEffect(() => {
    setPage(0)
  }, [filter.hobbyId, filter.q, filter.range.range, filter.range.from, filter.range.to])

  const selectedId = Number(searchParams.get('r')) || null

  const { containerRef, pageSize: fitPageSize } = useFitPageSize({
    itemHeightRem: LIST_ROW_HEIGHT_REM,
    min: 2,
    max: 12,
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

  // 머리줄 1행 오른쪽 액션(지시문 3·4): 기간 세그먼트 + [회고 받기] 버튼, 그 아래 남은 횟수를 작은
  // 보조 글자로 — 전에는 본문에 별도의 큰 카드였는데, 머리줄 액션으로 옮겨 높이를 줄인다.
  const headerAction = (
    <div className="flex flex-col items-end gap-1">
      <div className="flex items-center gap-2">
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
          className={
            shownResult
              ? 'inline-flex min-h-10 w-fit flex-none items-center justify-center rounded-lg border border-line-2 bg-white px-4 text-sm font-medium text-ink disabled:text-ink-3'
              : 'inline-flex min-h-10 w-fit flex-none items-center justify-center rounded-lg bg-base px-4 text-sm font-medium text-white disabled:bg-line disabled:text-[#5F594F]'
          }
        >
          {shownResult ? '새 회고 받기' : '회고 받기'}
        </button>
      </div>
      {usageQuery.data ? (
        <p className="text-caption-min text-ink-2">오늘 {usageQuery.data.remaining}/{usageQuery.data.limit}</p>
      ) : null}
    </div>
  )

  // O1(E): 자동 생성이 진행 중이고 아직 보여줄 회고가 없으면(처음 들어왔을 때) 생성 중 자리 표시를
  // 먼저 보여준다. 이미 보여줄 회고가 있으면(최신 회고 등) 조용히 배경에서만 확인하고 방해하지 않는다.
  const showAutoGeneratingSkeleton =
    autoMutation.isPending && selectedId == null && !shownResult && !mutation.isPending

  const detail = (
    <div className="flex flex-col gap-5">
      {shownResult?.isNew ? (
        <div className="flex items-center justify-between gap-3 rounded-xl bg-[color-mix(in_srgb,var(--color-base)_9%,white)] px-4 py-2.5">
          <span className="text-sm font-medium text-base-ink">새 회고가 도착했어요</span>
        </div>
      ) : null}

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

  const listPanel = (
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
        <div ref={containerRef} className="flex flex-col gap-2 lg:min-h-0 lg:flex-1">
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
