import { useEffect, useState, useSyncExternalStore } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AiLabel } from '../components/AiLabel'
import { Band } from '../components/Card'
import { Button } from '../components/Button'
import { useLatestNextAction } from '../hooks/useLatestNextAction'
import { HomeNextActionBlock } from '../components/NextActionCard'
import { Dropdown } from '../components/Dropdown'
import { HobbyInlineCreate } from '../components/HobbyInlineCreate'
import { HomeActivityCard } from '../components/HomeActivityCard'
import { HomeRecentCompact } from '../components/HomeRecentCompact'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
import { RecentRecordsCard } from '../components/RecentRecordsCard'
import { RecentWeekStrip } from '../components/RecentWeekStrip'
import { TopicChips } from '../components/TopicChips'
import { ChevronRightIcon, PlusIcon } from '../components/icons'
import { useCurrentUser } from '../hooks/useCurrentUser'
import { DotCompletionHost } from '../components/DotCompletionHost'
import { useHobbies } from '../hooks/useHobbies'
import { useIsDesktop } from '../hooks/useIsDesktop'
import { fetchLatestCoach, generateAutoCoach, type CoachAutoResponse } from '../lib/ai'
import { ApiError } from '../lib/api'
import {
  daysBetween,
  formatDurationMinutes,
  formatMonthDay,
  formatMonthDayWeekday,
  startOfWeekLocal,
  todayLocalDate,
} from '../lib/date'
import { takeDroppedOfflineMessages } from '../hooks/useOfflineSync'
import { createRecord, fetchRecords } from '../lib/records'
import { fetchActivityMap, fetchSummary, type StatsSummary } from '../lib/stats'


const primaryLinkClass =
  'inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-lg bg-base px-5 text-body-min font-medium text-white no-underline lg:w-fit'

function displayNameOf(user: { displayName: string | null; email: string | null } | undefined): string {
  if (!user) return ''
  return user.displayName || user.email?.split('@')[0] || 'Growit 회원'
}

const AUTO_COACH_DATE_KEY = 'growit_auto_coach_date'

/**
 * 홈에 들어올 때마다 서버에 자동 회고 조건(기록 5개 이상 쌓임, O1 E)을 확인한다. sessionStorage에
 * 오늘 날짜를 적어 두고 하루 한 번만 호출한다(서버도 같은 입력이면 멱등이라 이중 안전장치).
 * 응답(GENERATED/EXISTS/NOT_ELIGIBLE 등)을 돌려줘 사이드의 회고 카드가 "기록 n/5" 진행 표시에
 * 쓸 수 있게 한다 — 같은 날 두 번째 접속처럼 호출을 건너뛴 경우에는 null을 돌려주고, 그때는
 * latest 쿼리(저장된 결과)만으로 카드를 그린다(P1 5단계).
 */
function useAutoCoachOncePerDay(): CoachAutoResponse | null {
  const queryClient = useQueryClient()
  const [autoStatus, setAutoStatus] = useState<CoachAutoResponse | null>(null)

  useEffect(() => {
    const today = todayLocalDate()
    let lastCalled: string | null = null
    try {
      lastCalled = sessionStorage.getItem(AUTO_COACH_DATE_KEY)
    } catch {
      // 세션 저장소를 못 쓰면(사생활 보호 모드 등) 그냥 매번 호출한다 — 서버가 멱등이라 안전하다.
    }
    if (lastCalled === today) return

    generateAutoCoach()
      .then((data) => {
        try {
          sessionStorage.setItem(AUTO_COACH_DATE_KEY, today)
        } catch {
          // 저장 실패는 무시한다(다음 접속에서 다시 시도될 뿐).
        }
        setAutoStatus(data)
        queryClient.invalidateQueries({ queryKey: ['ai', 'coach', 'latest'] })
      })
      .catch(() => {
        // 실패해도 홈은 평소대로 보여준다. 다음 접속에서 다시 시도된다.
      })
  }, [])

  return autoStatus
}


export function HomePage() {
  const { data: currentUser, isLoading: userLoading } = useCurrentUser()
  const summaryQuery = useQuery({ queryKey: ['stats', 'summary'], queryFn: fetchSummary })
  const [today] = useState(() => new Date())
  const [droppedMessages] = useState(() => takeDroppedOfflineMessages())
  const autoStatus = useAutoCoachOncePerDay()
  const isDesktop = useIsDesktop()
  // 도트 그림 높이 상한만 고르는 값이다(요소를 숨기지 않는다). 열 높이 = 뷰포트 − 116px 기준으로 단계와 맞춘다.
  // 도트 그림 최대 높이는 단계가 아니라 뷰포트 높이에 비례해 연속으로 늘고 줄어든다(헤더·여백 116px 제외 열 높이의 약 28%).
  const viewportHeight = useSyncExternalStore(subscribeResize, () => window.innerHeight, () => 800)
  const pictureMax = Math.round(Math.min(260, Math.max(70, (viewportHeight - 116) * 0.28)))

  // 아래 훅은 summary가 아직 로딩 중이어도(이 아래 early return보다 먼저) 매 렌더마다 똑같이
  // 호출돼야 한다(Hooks 규칙) — summary 로딩 전에는 잠정 기본값으로 계산해 둔다.
  const summaryPreview = summaryQuery.data
  const statePreview: 'first' | 'comeback' | 'done' | 'off' | null = !summaryPreview
    ? null
    : summaryPreview.recordedDays === 0
      ? 'first'
      : (summaryPreview.daysSinceLastRecord ?? 0) >= 7
        ? 'comeback'
        : summaryPreview.todayRecorded
          ? 'done'
          : 'off'

  const restedWeekQuery = useQuery({
    queryKey: ['stats', 'activity-map', 'week-check', startOfWeekLocal(today), todayLocalDate()],
    queryFn: () => fetchActivityMap({ from: startOfWeekLocal(today), to: todayLocalDate() }),
    enabled: statePreview === 'comeback',
  })

  if (userLoading || summaryQuery.isLoading) {
    return <Loading />
  }

  if (summaryQuery.isError) {
    return (
      <div className="flex flex-col gap-3">
        <Note variant="sand">잠시 후 다시 시도해 주세요.</Note>
        <button
          type="button"
          onClick={() => summaryQuery.refetch()}
          className="inline-flex min-h-11 w-fit items-center text-sm font-medium text-base-ink no-underline rounded-full px-2 -mx-2 hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)] focus-visible:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]"
        >
          다시 불러오기
        </button>
      </div>
    )
  }

  if (!summaryQuery.data) {
    return <Loading />
  }

  const summary = summaryQuery.data
  const name = displayNameOf(currentUser)
  const state = statePreview!

  const restedWeek = state === 'comeback' && (restedWeekQuery.data ?? []).length === 0

  const titleText =
    state === 'comeback' ? `다시 오셨네요, ${name}님` : state === 'first' ? `${name}님, 환영합니다` : `안녕하세요, ${name}님`
  const subLine =
    state === 'first' ? (
      <p className="text-body-min text-ink-2">오늘 있었던 일을 한 줄만 적어도 첫 기록이 돼요.</p>
    ) : state === 'comeback' && summary.lastRecordedDate ? (
      // P1 5단계: 쉬었다 돌아온 문구와 "이번 주는 쉬어 간 주예요" 안내를 한 줄로 합친다.
      <p className="text-body-min text-ink-2">
        마지막 기록은 {formatMonthDay(summary.lastRecordedDate)}이에요. 쌓아 둔 기록은 그대로 있어요.
        {restedWeek ? ' 이번 주는 쉬어 간 주예요 — 쉬는 시간도 흐름의 일부예요.' : ''}
      </p>
    ) : null

  const droppedNote =
    droppedMessages.length > 0 ? (
      <Note variant="sand">
        연결이 안 될 때 기기에 저장해 뒀던 기록 {droppedMessages.length}개를 서버가 받아들이지 못해 지웠어요.
      </Note>
    ) : null

  // ---- 웹(폭 1024 이상): 페이지 스크롤 없이 두 열을 뷰포트 − 헤더 높이에 고정하고 CSS 컨테이너 쿼리로 밀도만 바꾼다 ----
  if (isDesktop) {
    return (
      <div className="flex flex-col gap-7 lg:min-h-0 lg:flex-1">
        <div className="grid min-h-0 flex-1 grid-cols-12 grid-rows-[minmax(0,1fr)] gap-8">
          <div className="home-col col-span-7">
            <div className="home-fit">
              <div className="flex flex-col gap-1">
                <div className="flex items-baseline justify-between gap-3">
                  <h1 className="t-page min-w-0 truncate">{titleText}</h1>
                  <span className="t-sub flex-none text-ink-3">{formatMonthDayWeekday(today)}</span>
                </div>
                {subLine ? <div className="line-clamp-1">{subLine}</div> : null}
              </div>
              {droppedNote}
              {state === 'first' ? <FirstUseBoard /> : <QuickRecordInput />}
              {state !== 'first' ? (
                <>
                  <HomeRecentCompact />
                  <HomeActivityCard home maxPictureHeight={pictureMax} />
                </>
              ) : null}
            </div>
          </div>
          <div className="home-col col-span-5">
            <div className="home-fit">
              {state !== 'first' ? (
                <>
                  <RecentWeekStrip home />
                  <HomeCoachCard autoStatus={autoStatus} dense />
                  <StatsTiles summary={summary} slim />
                  <div className="home-hide-1">
                    <TopicChips dense />
                  </div>
                </>
              ) : null}
            </div>
          </div>
        </div>
        <DotCompletionHost />
      </div>
    )
  }

  // ---- 모바일(폭 1024 미만): 기존 구성 그대로 ----
  return (
    <div className="flex flex-col gap-7">
      <div className="flex flex-col gap-5">
        <div className="flex flex-col gap-1.5">
          <h1 className="font-serif text-[22px] font-semibold">{titleText}</h1>
          <span className="text-sm text-ink-2">{formatMonthDayWeekday(today)}</span>
          {subLine}
        </div>

        {droppedNote}

        {/* Q1 1번: 기록이 1개 이상인 모든 상태에서 한 줄 기록 입력칸을 보여주고, 기록이 0개인 첫 사용 상태만 [첫 기록 쓰기]. */}
        {state === 'first' ? <FirstUseBoard /> : <QuickRecordInput />}

        {/* S1 D-2단계: 모바일 순서는 인사말 → 한 줄 기록 → 최근 7일 → 최근 회고 카드 → 최근 기록 2개 → 활동 지도·통계·주제. */}
        {state !== 'first' ? (
          <>
            <RecentWeekStrip />
            <HomeCoachCard autoStatus={autoStatus} />
            <RecentRecordsCard limit={2} />
            <div className="flex flex-col gap-7">
              <HomeActivityCard />
              <StatsTiles summary={summary} />
              <TopicChips />
            </div>
          </>
        ) : null}
      </div>
      <DotCompletionHost />
    </div>
  )
}


/** 디자인에 없는 추가(U3): 연속 일수 같은 쉬면 줄어드는 지표 대신 쌓이기만 하는 지표 3개. */
function subscribeResize(callback: () => void) {
  window.addEventListener('resize', callback)
  return () => window.removeEventListener('resize', callback)
}

function StatsTiles({ summary, slim = false }: { summary: StatsSummary; slim?: boolean }) {
  const daysSinceStart = summary.firstRecordedDate
    ? daysBetween(summary.firstRecordedDate, summary.today) + 1
    : null

  if (slim) {
    // 웹 홈: 넉넉함·보통은 라벨 위·값 아래 카드, 컴팩트는 한 줄(CSS 변수 --stat-dir).
    const tiles = [
      ['기록한 날', `${summary.recordedDays}일`],
      ['쌓인 시간', summary.totalMinutes === 0 ? '기록 안 함' : formatDurationMinutes(summary.totalMinutes)],
      ['시작한 지', daysSinceStart != null ? `${daysSinceStart}일` : '-'],
    ]
    return (
      <div className="home-stats">
        {tiles.map(([label, value]) => (
          <div key={label} className="home-stat">
            <span className="t-sub flex-none text-ink-2">{label}</span>
            <span className="home-stat-value truncate font-serif text-ink">{value}</span>
          </div>
        ))}
      </div>
    )
  }

  return (
    <div className="grid grid-cols-3 gap-3">
      <div className="flex flex-col gap-1 rounded-xl bg-surface p-4">
        <span className="text-sm text-ink-2">기록한 날</span>
        <span className="font-serif text-lg text-ink">{summary.recordedDays}일</span>
      </div>
      <div className="flex flex-col gap-1 rounded-xl bg-surface p-4">
        <span className="text-sm text-ink-2">쌓인 시간</span>
        <span className="font-serif text-lg text-ink">
          {summary.totalMinutes === 0 ? '기록 안 함' : formatDurationMinutes(summary.totalMinutes)}
        </span>
      </div>
      <div className="flex flex-col gap-1 rounded-xl bg-surface p-4">
        <span className="text-sm text-ink-2">시작한 지</span>
        <span className="font-serif text-lg text-ink">{daysSinceStart != null ? `${daysSinceStart}일` : '-'}</span>
      </div>
    </div>
  )
}

function FirstUseBoard() {
  return (
    <Band className="flex flex-col items-start gap-3.5">
      <h2 className="text-lg font-semibold">첫 기록을 남겨 볼까요?</h2>
      <Link to="/records/new" className={primaryLinkClass}>
        <PlusIcon width={18} height={18} />첫 기록 쓰기
      </Link>
    </Band>
  )
}

/**
 * 홈의 "최근 회고 카드". 상태와 무관하게 항상 보이고, 로딩 중에는 같은 높이의 자리표시, 회고가 없으면 한 줄 안내를
 * 같은 카드 자리에 둔다. cardState가 EXPIRED여도 카드를 숨기지 않는다(날짜·내용은 그대로, 반응 버튼만 숨김).
 * dense는 웹 홈용: "회고 보기"는 제목 줄 오른쪽 끝, 요약 2줄 말줄임, 다음에 해 볼 일은 컴팩트 모양, 안쪽 여백 축소.
 */
function HomeCoachCard({ autoStatus, dense = false }: { autoStatus: CoachAutoResponse | null; dense?: boolean }) {
  const { data, isLoading } = useQuery({ queryKey: ['ai', 'coach', 'latest'], queryFn: fetchLatestCoach })
  const nextActionQuery = useLatestNextAction()

  if (isLoading || nextActionQuery.isLoading) {
    return dense ? (
      <div className="home-pad home-grow-3 flex flex-col gap-2 rounded-2xl bg-surface" role="status" aria-label="회고를 불러오는 중이에요">
        <div className="h-5 w-1/3 rounded-full bg-rest motion-safe:animate-pulse" />
        <div className="h-4 w-full rounded-full bg-rest motion-safe:animate-pulse" />
        <div className="min-h-[8.5rem] flex-1 rounded-xl bg-rest motion-safe:animate-pulse" />
      </div>
    ) : (
      <Loading />
    )
  }

  // 회고가 있든 없든, 최신 기록에 반응 전 제안이 있으면 항상 보인다.
  const nextAction = nextActionQuery.data && nextActionQuery.data.state == null ? nextActionQuery.data : null
  const nextActionBlock = nextAction ? <HomeNextActionBlock action={nextAction} dense={dense} /> : null
  const cardClass = dense ? 'home-pad home-grow-3 flex min-h-0 flex-col gap-2 rounded-2xl bg-surface' : 'flex flex-col gap-2.5 rounded-2xl bg-surface p-5'

  // 회고가 아예 없으면: 오늘 접속 때 자동 생성 조건을 이미 확인했다면(autoStatus) 정확한 진행 수치를, 아니면 일반 안내만.
  if (!data) {
    if (autoStatus && autoStatus.status === 'NOT_ELIGIBLE' && !autoStatus.hasAnyCoach) {
      const ratio = Math.min(100, (autoStatus.newRecordsSinceLast / Math.max(1, autoStatus.requiredCount)) * 100)
      return (
        <div className={dense ? cardClass : 'flex flex-col items-start gap-3 rounded-2xl bg-surface p-5'}>
          <h2 className="text-card-title">AI 회고</h2>
          {nextActionBlock ? <div className="w-full">{nextActionBlock}</div> : null}
          <p className="text-caption-min text-ink-2">
            기록 {autoStatus.newRecordsSinceLast}/{autoStatus.requiredCount} · {autoStatus.requiredCount}개가 쌓이면
            회고가 자동으로 도착해요
          </p>
          <div className="h-2 w-full overflow-hidden rounded-full bg-rest">
            <div className="h-full rounded-full bg-base" style={{ width: `${ratio}%` }} />
          </div>
          {dense ? null : (
            <Link to="/records/new" className="inline-flex min-h-9 items-center text-sm font-medium text-base-ink no-underline rounded-full px-2 -mx-2 hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)] focus-visible:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]">
              기록하기
            </Link>
          )}
        </div>
      )
    }
    return (
      <div className={dense ? cardClass : 'flex flex-col items-start gap-2 rounded-2xl bg-surface p-5'}>
        <h2 className="text-card-title">AI 회고</h2>
        {nextActionBlock ? <div className="w-full">{nextActionBlock}</div> : null}
        <p className="text-caption-min text-ink-2">
          {dense ? '기록이 쌓이면 회고를 보여 드릴게요' : '아직 받은 회고가 없어요.'}
        </p>
      </div>
    )
  }

  const newBanner = data.isNew ? (
    <div className="flex items-center gap-1.5">
      <AiLabel>새로 도착</AiLabel>
    </div>
  ) : null
  const reviewLink = (
    <Link
      to={data.id != null ? `/coach?r=${data.id}` : '/coach'}
      className={
        dense
          ? 'inline-flex h-8 flex-none items-center gap-0.5 rounded-full px-2 text-sm font-medium text-base-ink no-underline hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]'
          : 'inline-flex min-h-9 w-fit items-center gap-0.5 text-sm font-medium text-base-ink no-underline'
      }
    >
      회고 보기
      <ChevronRightIcon width={14} height={14} />
    </Link>
  )

  return (
    <div className={cardClass}>
      <div className="flex items-center justify-between gap-2">
        <h2 className="min-w-0 truncate text-card-title">
          {data.startDate ? `${formatMonthDay(data.startDate)} 회고` : 'AI 회고'}
        </h2>
        <div className="flex flex-none items-center gap-1">
          {newBanner}
          {dense ? reviewLink : null}
        </div>
      </div>
      {data.periodSummary ? (
        <p className={dense ? 'home-sum t-body text-ink-2' : 'line-clamp-2 text-body-min text-ink-2'}>{data.periodSummary}</p>
      ) : null}
      {nextActionBlock}
      {dense ? null : reviewLink}
    </div>
  )
}


/**
 * Q1 1번: 모든 상태(기본·오늘 기록 완료·쉬었다 돌아온·AI 정리 꺼짐)에서 인사말 아래에 항상 보이는
 * 한 줄 기록 입력칸(이전엔 "쉬었다 돌아온" 상태에만 있던 ComebackBoard를 그대로 일반화한 것).
 * 분야 기본값은 가장 최근 기록의 분야(보관됐으면 첫 활성 분야), 분야가 둘 이상이면 입력칸 왼쪽에
 * 작은 분야 드롭다운을 둔다. 기록이 0개인 첫 사용 상태는 이 컴포넌트 대신 FirstUseBoard를 쓴다.
 */
function QuickRecordInput() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const isDesktop = useIsDesktop()
  const { data: activeHobbies, isLoading: hobbiesLoading } = useHobbies(false)
  const recentQuery = useQuery({ queryKey: ['records', 'recent'], queryFn: () => fetchRecords({ size: 1 }) })

  const [selectedHobbyId, setSelectedHobbyId] = useState<number | null>(null)
  const [content, setContent] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [addingHobby, setAddingHobby] = useState(false)

  if (hobbiesLoading || recentQuery.isLoading) {
    return isDesktop ? (
      <div className="h-[6.5rem] rounded-xl bg-surface motion-safe:animate-pulse" role="status" aria-label="입력칸을 불러오는 중이에요" />
    ) : (
      <Loading />
    )
  }

  const activeIds = new Set((activeHobbies ?? []).map((hobby) => hobby.id))
  const recentHobbyId = recentQuery.data?.items[0]?.hobbyId ?? null
  const defaultHobbyId =
    recentHobbyId != null && activeIds.has(recentHobbyId) ? recentHobbyId : (activeHobbies?.[0]?.id ?? null)
  const hobbyId = selectedHobbyId != null && activeIds.has(selectedHobbyId) ? selectedHobbyId : defaultHobbyId

  async function handleSave() {
    if (submitting || hobbyId == null) return
    const trimmed = content.trim()
    if (!trimmed) {
      setError('한 일을 입력해 주세요')
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      const created = await createRecord({ hobbyId, recordDate: todayLocalDate(), content: trimmed })
      setContent('')
      queryClient.invalidateQueries({ queryKey: ['stats'] })
      queryClient.invalidateQueries({ queryKey: ['records'] })
      queryClient.invalidateQueries({ queryKey: ['hobbies'] })
      navigate(`/records/${created.id}/saved`, { replace: true })
    } catch (saveError) {
      setError(saveError instanceof ApiError ? saveError.message : '저장하지 못했어요. 잠시 후 다시 시도해 주세요.')
    } finally {
      setSubmitting(false)
    }
  }

  // T3 2번: 분야가 1개뿐이어도 드롭다운을 항상 보여준다("분야는 ○○예요" 고정 문구는 없앤다) —
  // 목록 끝에 "+ 새 분야 추가"를 두고, 기록 작성의 HobbyInlineCreate와 같은 API(POST /api/hobbies,
  // useCreateHobby)를 그대로 재사용한다.
  const hobbyDropdown =
    hobbyId != null ? (
      <Dropdown
        pill
        aria-label="분야 선택"
        value={String(hobbyId)}
        options={(activeHobbies ?? []).map((hobby) => ({ value: String(hobby.id), label: hobby.name }))}
        onChange={(value) => setSelectedHobbyId(Number(value))}
        extra={{
          show: true,
          content: (close) =>
            addingHobby ? (
              // 팝오버 기본 min-w(9.5rem)로는 입력칸이 거의 안 보일 만큼 눌려서 폭을 넓힌다.
              <div className="min-w-[14rem]">
                <HobbyInlineCreate
                  autoFocus
                  onCreated={(hobby) => {
                    setSelectedHobbyId(hobby.id)
                    setAddingHobby(false)
                    close()
                  }}
                />
              </div>
            ) : (
              <button
                type="button"
                onClick={() => setAddingHobby(true)}
                className="flex min-h-10 w-full items-center gap-1.5 rounded-md px-3 text-left text-sm font-medium text-base-ink"
              >
                <PlusIcon width={14} height={14} className="flex-none" />
                새 분야 추가
              </button>
            ),
        }}
      />
    ) : null

  const saveButton = (
    <Button type="button" size="md" disabled={submitting} onClick={handleSave}>
      저장
    </Button>
  )

  // FIX 홈 입력칸: 모바일에서 h-14(56px) 유틸리티 클래스만으로는 화면에서 약 34px로 작게
  // 보이는 사례가 있었다(배포 지연·서비스워커 캐시로 구버전 CSS가 남았을 가능성이 유력하지만,
  // 어느 경우든 실제로 적용되게 하려고 모바일에서만 인라인 style로 높이를 직접 못 박는다).
  const inputField = (
    <input
      value={content}
      onChange={(event) => setContent(event.target.value)}
      placeholder="오늘은 무엇을 했나요?"
      aria-label="오늘 한 줄 기록"
      className="w-full min-w-0 flex-1 rounded-lg border border-input bg-white font-serif text-body-min text-ink outline-none focus:border-base lg:h-12 lg:px-3.5"
      style={
        isDesktop
          ? undefined
          : {
              height: '56px',
              minHeight: '56px',
              padding: '0 14px',
              fontSize: '16px',
              boxSizing: 'border-box',
              width: '100%',
            }
      }
    />
  )

  return (
    <>
      {hobbyId == null ? (
        <Link to="/records/new" className={primaryLinkClass}>
          <PlusIcon width={18} height={18} />
          기록하기
        </Link>
      ) : (
        <div className="flex flex-col gap-2 rounded-xl bg-surface px-3.5 py-3.5 lg:px-[1.125rem]">
          <span className="text-sm text-ink-2">오늘 한 줄만 남겨도 충분해요</span>

          {/* R1 3번: 모바일(폭 1024 미만)은 위 줄 = 입력칸 전체 폭, 아래 줄 = 분야 알약(왼쪽)과
              저장(오른쪽)으로 세로 쌓기 — 한 줄에 셋을 다 넣으면 폭 360px에서 저장 버튼 글자가
              세로로 줄바꿈되던 문제라 분리했다. 데스크톱은 기존처럼 한 줄. 입력칸을 검색창보다
              눈에 띄게 하려고 모바일에서만 높이를 h-14(56px)로 키웠다(가장자리까지 늘리면 카드
              모서리와 부딪혀 어색해져서 폭은 카드 안쪽 여백을 그대로 둔다). */}
          <div className="flex flex-col gap-2 lg:hidden">
            {inputField}
            <div className="flex items-center justify-between gap-2">
              {hobbyDropdown ?? <span />}
              {saveButton}
            </div>
          </div>
          <div className="hidden items-center gap-2 lg:flex">
            {hobbyDropdown}
            {inputField}
            {saveButton}
          </div>

          {error ? <span className="text-caption-min text-[#6B3F0F]">{error}</span> : null}
        </div>
      )}
    </>
  )
}