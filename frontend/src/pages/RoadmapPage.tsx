import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, Navigate, useParams, useSearchParams } from 'react-router-dom'
import { Spinner } from '../components/AiProgress'
import { Button } from '../components/Button'
import { Dialog } from '../components/Dialog'
import { Dropdown } from '../components/Dropdown'
import { Input } from '../components/Input'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
import {
  CheckIcon,
  ChevronDownIcon,
  MoreIcon,
  PencilIcon,
  PlusIcon,
  TrashIcon,
  XIcon,
} from '../components/icons'
import {
  useAddItem,
  useAddStep,
  useArchiveRoadmap,
  useArchivedRoadmaps,
  useCreateRoadmap,
  useDeleteArchivedRoadmap,
  useDeleteItem,
  useDeleteStep,
  useRestoreRoadmap,
  useRoadmap,
  useRoadmapById,
  useSetItemDone,
  useUpdateItem,
  useUpdateRoadmapTarget,
  useUpdateStep,
} from '../hooks/useRoadmap'
import { useHobbies, useUpdateHobby, type Hobby } from '../hooks/useHobbies'
import { useIsDesktop } from '../hooks/useIsDesktop'
import { fetchRecords } from '../lib/records'
import { ApiError } from '../lib/api'
import { todayLocalDate } from '../lib/date'
import type { Roadmap, RoadmapItem, RoadmapStep, RoadmapSummary } from '../lib/roadmap'

const MAX_STEPS = 8
const MAX_ITEMS_PER_STEP = 8
const NOTICE = 'AI가 제안한 일반적인 연습 순서예요. 내 몸과 상황에 맞게 바꿔 쓰고, 통증이나 건강 문제가 있으면 전문가와 상의해요.'

const iconButton =
  'inline-flex h-11 w-11 flex-none items-center justify-center rounded-lg text-ink-2 hover:bg-[color-mix(in_srgb,var(--color-ink)_6%,transparent)] focus-visible:bg-[color-mix(in_srgb,var(--color-ink)_6%,transparent)] disabled:opacity-40'
/** 웹의 항목 행 아이콘: 평소엔 숨기고 행 hover·포커스에서만 보인다(키보드 포커스로도 나타난다). */
const rowIconButton =
  'inline-flex h-8 w-8 flex-none items-center justify-center rounded-md text-ink-2 hover:bg-[color-mix(in_srgb,var(--color-ink)_6%,transparent)] focus-visible:bg-[color-mix(in_srgb,var(--color-ink)_6%,transparent)] disabled:opacity-40'
const BASE_TINT = 'color-mix(in_srgb,var(--color-base)_12%,white)'

function errorMessageOf(error: unknown): string {
  return error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요'
}

function countItems(steps: RoadmapStep[]): { total: number; done: number } {
  const items = steps.flatMap((step) => step.items)
  return { total: items.length, done: items.filter((item) => item.done).length }
}

/** 첫 미완료 단계(항목이 하나도 없는 단계는 건너뛴다). 모두 마쳤으면 null. */
function currentStepId(steps: RoadmapStep[]): number | null {
  return steps.find((step) => step.items.some((item) => !item.done))?.id ?? null
}

/** "11월 30일" 같은 짧은 날짜(목표일 칩용). */
function shortDate(isoDate: string): string {
  const [, month, day] = isoDate.split('-')
  return `${Number(month)}월 ${Number(day)}일`
}

const STORED_HOBBY_KEY = 'growit.roadmap.hobbyId'

function readStoredHobbyId(): number | null {
  try {
    const value = Number(localStorage.getItem(STORED_HOBBY_KEY))
    return Number.isFinite(value) && value > 0 ? value : null
  } catch {
    return null
  }
}

function writeStoredHobbyId(id: number) {
  try {
    localStorage.setItem(STORED_HOBBY_KEY, String(id))
  } catch {
    // 저장소를 못 쓰는 환경(사생활 보호 창 등)에서는 기억만 포기한다.
  }
}

/** 예전 주소(/hobbies/:id/roadmap)는 새 주소로 보내고 그 분야를 고른 채로 연다. */
export function RoadmapLegacyRedirect() {
  const { id } = useParams()
  return <Navigate to={`/roadmap?hobby=${encodeURIComponent(id ?? '')}`} replace />
}

/** 바깥 클릭·Esc로 닫히는 작은 팝오버의 열림 상태와 기준 요소. */
function usePopover() {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!open) return
    function onPointerDown(event: PointerEvent) {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) setOpen(false)
    }
    function onKey(event: globalThis.KeyboardEvent) {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])
  return { open, setOpen, rootRef }
}

type MenuEntry = { label: string; onSelect: () => void; disabled?: boolean }

/** ⋯ 버튼과 작은 메뉴. 모바일에서 터치 44px, 웹에서는 36px 높이의 항목을 쓴다. */
function ActionMenu({ label, items, small = false }: { label: string; items: MenuEntry[]; small?: boolean }) {
  const { open, setOpen, rootRef } = usePopover()
  return (
    <div ref={rootRef} className="relative flex-none">
      <button
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={label}
        className={small ? rowIconButton : iconButton}
        onClick={() => setOpen((prev) => !prev)}
      >
        <MoreIcon width={18} height={18} />
      </button>
      {open ? (
        <div
          role="menu"
          aria-label={label}
          className="absolute right-0 top-full z-20 mt-1 min-w-[9rem] rounded-lg border border-line-2 bg-white p-1.5 shadow-sm"
        >
          {items.map((entry) => (
            <button
              key={entry.label}
              type="button"
              role="menuitem"
              disabled={entry.disabled}
              onClick={() => {
                setOpen(false)
                entry.onSelect()
              }}
              className="flex min-h-11 w-full items-center whitespace-nowrap rounded-md px-3 text-left text-sm text-ink hover:bg-surface focus-visible:bg-surface disabled:opacity-40 lg:min-h-9"
            >
              {entry.label}
            </button>
          ))}
        </div>
      ) : null}
    </div>
  )
}

/** 목표일 칩. 누르면 팝오버가 열리고 날짜 입력은 그 안에만 있다. 지우기로 목표일을 비운다. */
function DateChip({
  value,
  onChange,
  disabled,
}: {
  value: string | null
  onChange: (value: string | null) => void
  disabled?: boolean
}) {
  const { open, setOpen, rootRef } = usePopover()
  return (
    <div ref={rootRef} className="relative flex-none">
      <button
        type="button"
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-label={value ? `목표일 ${shortDate(value)}, 바꾸기` : '목표일 설정'}
        disabled={disabled}
        onClick={() => setOpen((prev) => !prev)}
        className="inline-flex h-11 items-center gap-1.5 whitespace-nowrap rounded-full border border-line-2 bg-white px-3.5 text-sm font-medium text-ink disabled:opacity-40 lg:h-9"
      >
        {value ? shortDate(value) : '목표일 설정'}
        <ChevronDownIcon width={14} height={14} className="flex-none text-ink-2" />
      </button>
      {open ? (
        <div
          role="dialog"
          aria-label="목표일"
          className="absolute right-0 top-full z-20 mt-1.5 flex w-64 flex-col gap-2 rounded-lg border border-line-2 bg-white p-3 shadow-sm lg:right-0"
        >
          <label className="flex flex-col gap-1.5 whitespace-nowrap text-sm font-medium">
            목표일
            <Input
              type="date"
              value={value ?? ''}
              min={todayLocalDate()}
              onChange={(event) => onChange(event.target.value || null)}
              aria-label="목표일 날짜"
            />
          </label>
          <div className="flex gap-2">
            <Button
              type="button"
              size="sm"
              variant="secondary"
              disabled={!value}
              onClick={() => {
                onChange(null)
                setOpen(false)
              }}
            >
              지우기
            </Button>
            <Button type="button" size="sm" onClick={() => setOpen(false)}>
              닫기
            </Button>
          </div>
        </div>
      ) : null}
    </div>
  )
}

/**
 * 상단 메뉴의 로드맵 화면. 분야는 공용 Dropdown으로 고르고, 마지막으로 고른 분야를 기억한다
 * (처음에는 가장 최근에 기록한 분야). 분야 하나의 화면은 HobbyRoadmap이 상태별로 그린다.
 * 웹은 페이지 스크롤 없이 남는 높이를 채우고, 모바일은 페이지가 세로로 스크롤된다.
 */
export function RoadmapPage() {
  const [searchParams] = useSearchParams()
  const { data: hobbies, isLoading } = useHobbies(false)
  const recentQuery = useQuery({ queryKey: ['records', 'recent'], queryFn: () => fetchRecords({ size: 1 }) })
  const [pickedId, setPickedId] = useState<number | null>(() => {
    const fromUrl = Number(searchParams.get('hobby'))
    return Number.isFinite(fromUrl) && fromUrl > 0 ? fromUrl : readStoredHobbyId()
  })
  const [view, setView] = useState<'active' | 'archive'>('active')

  const list = hobbies ?? []
  const recentHobbyId = recentQuery.data?.items[0]?.hobbyId ?? null
  const waitingForRecent = pickedId == null && recentQuery.isLoading
  const selected =
    list.find((hobby) => hobby.id === pickedId) ??
    list.find((hobby) => hobby.id === recentHobbyId) ??
    list[0] ??
    null

  const archivedQuery = useArchivedRoadmaps(selected?.id ?? 0)
  const archivedCount = archivedQuery.data?.length ?? 0

  function pick(id: number) {
    setPickedId(id)
    writeStoredHobbyId(id)
  }

  let body
  if (isLoading || waitingForRecent) {
    body = <Loading />
  } else if (!selected) {
    body = (
      <div className="flex flex-col items-start gap-3">
        <p className="text-body-min text-ink-2">분야를 먼저 추가해 주세요</p>
        <Link
          to="/settings/hobbies"
          className="inline-flex min-h-11 items-center rounded-lg border border-line-2 bg-white px-4 text-sm font-medium text-ink no-underline"
        >
          활동 분야 관리
        </Link>
      </div>
    )
  } else if (view === 'archive') {
    body = <ArchiveView key={selected.id} hobby={selected} onRestored={() => setView('active')} />
  } else {
    body = <HobbyRoadmap key={selected.id} hobby={selected} />
  }

  return (
    <div className="flex flex-col gap-3 lg:min-h-0 lg:flex-1">
      <div className="flex flex-none flex-wrap items-center justify-between gap-x-3 gap-y-2">
        <h1 className="text-page-title">로드맵</h1>
        {selected ? (
          <div className="flex items-center gap-2">
            <div className="inline-flex rounded-lg border border-line-2 bg-white p-[0.1875rem]" role="group" aria-label="로드맵 보기">
              {(
                [
                  ['active', '진행 중'],
                  ['archive', `보관함 ${archivedCount}`],
                ] as const
              ).map(([value, label]) => (
                <button
                  key={value}
                  type="button"
                  aria-pressed={view === value}
                  onClick={() => setView(value)}
                  className={[
                    'h-10 whitespace-nowrap rounded-md px-3.5 text-sm font-medium lg:h-8',
                    view === value ? 'bg-ink text-white' : 'bg-transparent text-ink-2',
                  ].join(' ')}
                >
                  {label}
                </button>
              ))}
            </div>
            <Dropdown
              aria-label="분야"
              value={String(selected.id)}
              options={list.map((hobby) => ({ value: String(hobby.id), label: hobby.name }))}
              onChange={(value) => {
                setView('active')
                pick(Number(value))
              }}
            />
          </div>
        ) : null}
      </div>
      {body}
    </div>
  )
}

/** 목표 한 줄과 ✎ 수정. 비우고 저장하면 목표를 지운다. 웹에서는 한 줄 말줄임(hover로 전체). */
function GoalLine({ hobby, onError }: { hobby: Hobby; onError: (message: string | null) => void }) {
  const updateHobby = useUpdateHobby()
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(hobby.goal ?? '')
  const goal = hobby.goal?.trim() ?? ''

  function save(event: FormEvent) {
    event.preventDefault()
    const trimmed = draft.trim()
    onError(null)
    updateHobby.mutate(
      { id: hobby.id, patch: { goal: trimmed === '' ? null : trimmed } },
      { onSuccess: () => setEditing(false), onError: (error) => onError(errorMessageOf(error)) },
    )
  }

  if (editing || !goal) {
    return (
      <form className="flex min-w-0 flex-col gap-2 lg:flex-row lg:items-center" onSubmit={save}>
        <Input
          value={draft}
          maxLength={200}
          onChange={(event) => setDraft(event.target.value)}
          placeholder="예: 코드 전환 익숙해지기"
          aria-label={`${hobby.name} 목표`}
          autoFocus={editing}
          className="lg:min-w-[20rem] lg:flex-1"
        />
        <div className="flex gap-2">
          <Button type="submit" disabled={updateHobby.isPending || (!goal && !draft.trim())}>
            저장
          </Button>
          {editing ? (
            <Button type="button" variant="secondary" onClick={() => setEditing(false)}>
              취소
            </Button>
          ) : null}
        </div>
      </form>
    )
  }

  return (
    <div className="flex min-w-0 items-center gap-1">
      <p
        title={goal}
        className="min-w-0 flex-1 break-words font-serif text-lg leading-[1.5] lg:truncate lg:text-[22px] lg:leading-[1.4]"
      >
        {goal}
      </p>
      <button
        type="button"
        aria-label="목표 고치기"
        className={iconButton}
        onClick={() => {
          setDraft(hobby.goal ?? '')
          setEditing(true)
        }}
      >
        <PencilIcon width={18} height={18} />
      </button>
    </div>
  )
}

type Confirming = null | 'renew' | 'archive'

function HobbyRoadmap({ hobby }: { hobby: Hobby }) {
  const hobbyId = hobby.id
  const { data: roadmap, isLoading, isError } = useRoadmap(hobbyId)
  const create = useCreateRoadmap(hobbyId)
  const archive = useArchiveRoadmap(hobbyId)
  const [targetDraft, setTargetDraft] = useState<string | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [confirming, setConfirming] = useState<Confirming>(null)
  const [notice, setNotice] = useState<string | null>(null)

  // 로드맵 만들기는 이 버튼을 눌렀을 때만 요청한다(화면을 열 때 자동으로 만들지 않는다).
  function generate(targetDate: string | null, mode: 'AI' | 'MANUAL' = 'AI') {
    setErrorMessage(null)
    create.mutate({ targetDate, mode }, { onError: (error) => setErrorMessage(errorMessageOf(error)) })
  }

  function renew() {
    if (!roadmap) return
    setErrorMessage(null)
    setConfirming(null)
    archive.mutate(roadmap.id, {
      onSuccess: () => generate(roadmap.targetDate),
      onError: (error) => setErrorMessage(errorMessageOf(error)),
    })
  }

  function archiveOnly() {
    if (!roadmap) return
    setErrorMessage(null)
    setConfirming(null)
    archive.mutate(roadmap.id, { onError: (error) => setErrorMessage(errorMessageOf(error)) })
  }

  const goal = hobby.goal?.trim() ?? ''

  let body
  if (isError) {
    body = <Note variant="sand">로드맵을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</Note>
  } else if (isLoading) {
    body = <Loading />
  } else if (create.isPending || archive.isPending) {
    body = (
      <div role="status" aria-live="polite" className="flex flex-col gap-3 rounded-2xl bg-surface p-5">
        <p className="flex items-center gap-3 text-[1.125rem] font-medium leading-[1.5] text-ink">
          <Spinner className="text-base-ink" />
          AI가 로드맵을 만들고 있어요
        </p>
        {[0, 1, 2].map((index) => (
          <div
            key={index}
            className={`h-4 animate-pulse rounded-full bg-rest motion-reduce:animate-none ${index === 0 ? 'w-[80%]' : 'w-full'}`}
          />
        ))}
      </div>
    )
  } else if (!roadmap) {
    body = (
      <div className="flex flex-col gap-4 lg:max-w-3xl">
        <GoalLine hobby={hobby} onError={setErrorMessage} />
        {goal ? (
          <div className="flex flex-col gap-3">
            <div className="flex flex-wrap items-center gap-3">
              <DateChip value={targetDraft} onChange={setTargetDraft} />
              <Button type="button" onClick={() => generate(targetDraft)}>
                {errorMessage ? '다시 시도' : '로드맵 만들기'}
              </Button>
              <button
                type="button"
                onClick={() => generate(targetDraft, 'MANUAL')}
                className="inline-flex min-h-11 items-center whitespace-nowrap rounded-full px-1 text-sm font-medium text-base-ink underline underline-offset-2"
              >
                AI 없이 직접 만들기
              </button>
            </div>
            <p className="text-body-min text-ink-2">
              AI가 목표까지의 단계를 제안해요. 단계와 할 일은 마음대로 고칠 수 있어요. 직접 만들면 단계와 할 일을 직접 적어요
            </p>
          </div>
        ) : (
          <p className="text-body-min text-ink-2">목표를 적으면 로드맵을 만들 수 있어요.</p>
        )}
      </div>
    )
  } else {
    body = (
      <RoadmapBody
        roadmap={roadmap}
        hobby={hobby}
        goalChanged={goal !== '' && goal !== roadmap.goalText}
        confirming={confirming}
        onAskConfirm={setConfirming}
        onRenew={renew}
        onArchiveOnly={archiveOnly}
        onError={setErrorMessage}
        onNotice={setNotice}
      />
    )
  }

  return (
    <div className="flex flex-col gap-3 lg:min-h-0 lg:flex-1">
      {errorMessage ? <Note variant="sand">{errorMessage}</Note> : null}
      {notice ? <Note variant="calm">{notice}</Note> : null}
      {body}
      <p className="flex-none text-xs leading-[1.5] text-ink-2 lg:text-xs">{NOTICE}</p>
    </div>
  )
}

function RoadmapBody({
  roadmap,
  hobby,
  goalChanged,
  confirming,
  onAskConfirm,
  onRenew,
  onArchiveOnly,
  onError,
  onNotice,
  readOnly = false,
  banner,
}: {
  roadmap: Roadmap
  hobby: Hobby
  /** 분야의 목표가 이 로드맵을 만들 때의 목표와 달라졌을 때 true. */
  goalChanged: boolean
  confirming: Confirming
  onAskConfirm: (value: Confirming) => void
  onRenew: () => void
  onArchiveOnly: () => void
  onError: (message: string | null) => void
  onNotice: (message: string | null) => void
  /** 보관한 로드맵 보기: 체크·수정·추가·삭제를 모두 막고 머리줄 자리에 banner를 둔다. */
  readOnly?: boolean
  banner?: ReactNode
}) {
  const hobbyId = hobby.id
  const desktop = useIsDesktop()
  const updateTarget = useUpdateRoadmapTarget(hobbyId)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [open, setOpen] = useState<Record<number, boolean>>({})
  const [adding, setAdding] = useState(false)
  const counts = countItems(roadmap.steps)
  const currentId = currentStepId(roadmap.steps)
  // 처음 열 때(고른 단계가 없을 때)와 고른 단계가 사라졌을 때는 현재 단계를 고른다.
  const selected =
    roadmap.steps.find((step) => step.id === selectedId) ??
    roadmap.steps.find((step) => step.id === currentId) ??
    roadmap.steps[0] ??
    null

  function changeTarget(value: string | null) {
    onError(null)
    updateTarget.mutate({ id: roadmap.id, targetDate: value }, { onError: (error) => onError(errorMessageOf(error)) })
  }

  const header = readOnly ? (
    banner
  ) : (
    <div className="flex flex-none flex-wrap items-center gap-x-2 gap-y-1 lg:flex-nowrap lg:gap-3">
      <div className="min-w-0 basis-full lg:flex-1 lg:basis-auto">
        <GoalLine hobby={hobby} onError={onError} />
      </div>
      <DateChip value={roadmap.targetDate} onChange={changeTarget} disabled={updateTarget.isPending} />
      <p className="min-w-0 flex-1 whitespace-nowrap text-sm text-ink-2 lg:flex-none" aria-live="polite">
        {counts.total}개 중 {counts.done}개 체크했어요
      </p>
      <ActionMenu
        label="로드맵 메뉴"
        items={[
          { label: '새로 만들기', onSelect: () => onAskConfirm('renew') },
          { label: '보관', onSelect: () => onAskConfirm('archive') },
        ]}
      />
    </div>
  )

  const confirmRow = !readOnly && confirming ? (
    <div className="flex flex-none flex-wrap items-center gap-x-3 gap-y-2 rounded-xl bg-surface px-4 py-2">
      <p className="text-sm">
        {confirming === 'renew'
          ? '지금 로드맵은 보관하고 새로 만들까요? 기존 로드맵은 보관함에 남아요.'
          : '지금 로드맵을 보관할까요? 보관함에서 다시 쓸 수 있어요.'}
      </p>
      <div className="flex flex-wrap gap-2">
        {confirming === 'renew' ? (
          <>
            <Button type="button" size="sm" onClick={onRenew}>
              새로 만들기
            </Button>
            <Button type="button" size="sm" variant="secondary" onClick={onArchiveOnly}>
              보관만 하기
            </Button>
          </>
        ) : (
          <Button type="button" size="sm" onClick={onArchiveOnly}>
            보관하기
          </Button>
        )}
        <Button type="button" size="sm" variant="secondary" onClick={() => onAskConfirm(null)}>
          그대로 둘게요
        </Button>
      </div>
    </div>
  ) : null

  const goalNote = !readOnly && goalChanged ? (
    <p className="flex-none text-xs text-ink-2">
      이 로드맵은 &lsquo;{roadmap.goalText}&rsquo; 목표로 만들었어요. 새로 만들면 지금 목표로 다시 제안해요.
    </p>
  ) : null

  const adder = readOnly ? null : (
    <StepAdder
      roadmapId={roadmap.id}
      hobbyId={hobbyId}
      stepCount={roadmap.steps.length}
      open={adding}
      setOpen={setAdding}
      onAdded={(updated) => setSelectedId(updated.steps[updated.steps.length - 1]?.id ?? null)}
      onError={onError}
    />
  )

  if (desktop) {
    return (
      <div className="flex flex-col gap-3 lg:min-h-0 lg:flex-1">
        {header}
        {confirmRow}
        {goalNote}
        <div className="grid min-h-0 flex-1 grid-cols-[280px_minmax(0,1fr)] gap-4">
          <StepRail
            steps={roadmap.steps}
            selectedId={selected?.id ?? null}
            currentId={currentId}
            onSelect={setSelectedId}
            adder={adder}
          />
          {selected ? (
            <StepCard
              key={selected.id}
              variant="detail"
              step={selected}
              number={roadmap.steps.indexOf(selected) + 1}
              isFirst={roadmap.steps[0].id === selected.id}
              isLast={roadmap.steps[roadmap.steps.length - 1].id === selected.id}
              hobbyId={hobbyId}
              current={selected.id === currentId}
              readOnly={readOnly}
              onError={onError}
              onNotice={onNotice}
            />
          ) : (
            <div className="flex min-h-0 flex-col items-start justify-center gap-3 rounded-2xl border border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_9%,white)] p-5">
              <p className="text-body-min">{readOnly ? '이 로드맵에는 단계가 없어요.' : '아직 단계가 없어요. 목표까지 가는 첫 단계를 적어 보세요.'}</p>
              {readOnly ? null : (
                <Button type="button" onClick={() => setAdding(true)}>
                  첫 단계 추가하기
                </Button>
              )}
            </div>
          )}
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-3">
      {header}
      {confirmRow}
      {goalNote}
      {roadmap.steps.length === 0 ? (
        <div className="flex flex-col items-start gap-3 rounded-2xl border border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_9%,white)] p-5">
          <p className="text-body-min">{readOnly ? '이 로드맵에는 단계가 없어요.' : '아직 단계가 없어요. 목표까지 가는 첫 단계를 적어 보세요.'}</p>
          {readOnly ? null : (
            <Button type="button" onClick={() => setAdding(true)}>
              첫 단계 추가하기
            </Button>
          )}
        </div>
      ) : null}
      <ol className="flex flex-col gap-3">
        {roadmap.steps.map((step, index) => (
          <StepCard
            key={step.id}
            variant="accordion"
            step={step}
            number={index + 1}
            isFirst={index === 0}
            isLast={index === roadmap.steps.length - 1}
            hobbyId={hobbyId}
            current={step.id === currentId}
            expanded={open[step.id] ?? step.id === currentId}
            onToggle={() => setOpen((prev) => ({ ...prev, [step.id]: !(prev[step.id] ?? step.id === currentId) }))}
            readOnly={readOnly}
            onError={onError}
            onNotice={onNotice}
          />
        ))}
      </ol>
      {adder}
    </div>
  )
}

/** "+ 단계 추가" 버튼 또는 그 자리에서 열리는 입력 한 줄. */
function StepAdder({
  roadmapId,
  hobbyId,
  stepCount,
  open,
  setOpen,
  onAdded,
  onError,
}: {
  roadmapId: number
  hobbyId: number
  stepCount: number
  open: boolean
  setOpen: (open: boolean) => void
  onAdded: (roadmap: Roadmap) => void
  onError: (message: string | null) => void
}) {
  const addStep = useAddStep(hobbyId)
  const [title, setTitle] = useState('')

  if (stepCount >= MAX_STEPS) {
    return <p className="px-2 py-2 text-xs text-ink-2">단계는 {MAX_STEPS}개까지 둘 수 있어요</p>
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    const trimmed = title.trim()
    if (!trimmed) return
    onError(null)
    addStep.mutate(
      { roadmapId, title: trimmed },
      {
        onSuccess: (updated) => {
          setTitle('')
          setOpen(false)
          onAdded(updated)
        },
        onError: (error) => onError(errorMessageOf(error)),
      },
    )
  }

  if (!open) {
    return (
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="inline-flex h-11 w-full items-center justify-center gap-1.5 whitespace-nowrap rounded-lg border border-dashed border-line-2 text-sm font-medium text-ink-2 hover:bg-surface lg:h-10"
      >
        <PlusIcon width={16} height={16} />
        단계 추가
      </button>
    )
  }

  return (
    <form className="flex flex-col gap-2" onSubmit={submit}>
      <Input
        value={title}
        maxLength={40}
        onChange={(event) => setTitle(event.target.value)}
        placeholder="단계 제목"
        aria-label="새 단계 제목"
        autoFocus
        onKeyDown={(event) => {
          if (event.key === 'Escape') setOpen(false)
        }}
      />
      <div className="flex gap-2">
        <Button type="submit" size="sm" disabled={addStep.isPending || !title.trim()}>
          추가
        </Button>
        <Button type="button" size="sm" variant="secondary" onClick={() => setOpen(false)}>
          취소
        </Button>
      </div>
    </form>
  )
}

/** 웹 왼쪽 단계 목록(폭 280px). ↑↓로 이동하고 고른 단계는 aria-selected, 단계가 많으면 이 목록만 한 겹 스크롤한다. */
function StepRail({
  steps,
  selectedId,
  currentId,
  onSelect,
  adder,
}: {
  steps: RoadmapStep[]
  selectedId: number | null
  currentId: number | null
  onSelect: (id: number) => void
  adder: ReactNode | null
}) {
  function onKeyDown(event: KeyboardEvent<HTMLUListElement>) {
    const index = steps.findIndex((step) => step.id === selectedId)
    let next = -1
    if (event.key === 'ArrowDown') next = Math.min(steps.length - 1, index + 1)
    else if (event.key === 'ArrowUp') next = Math.max(0, index - 1)
    else if (event.key === 'Home') next = 0
    else if (event.key === 'End') next = steps.length - 1
    else return
    event.preventDefault()
    if (next < 0 || next >= steps.length) return
    onSelect(steps[next].id)
    document.getElementById(`roadmap-rail-${steps[next].id}`)?.focus()
  }

  return (
    <div className="flex min-h-0 flex-col rounded-2xl border border-line-2 bg-white">
      <ul
        role="listbox"
        aria-label="단계"
        onKeyDown={onKeyDown}
        className="scrollbar-thin-fade flex min-h-0 flex-1 flex-col gap-1 overflow-y-auto overscroll-contain p-1.5"
      >
        {steps.map((step, index) => {
          const counts = countItems([step])
          const isSelected = step.id === selectedId
          return (
            <li
              key={step.id}
              id={`roadmap-rail-${step.id}`}
              role="option"
              aria-selected={isSelected}
              tabIndex={isSelected ? 0 : -1}
              onClick={() => onSelect(step.id)}
              className={[
                'flex min-h-11 cursor-pointer items-center gap-2.5 rounded-xl border px-2.5 py-1.5 outline-offset-0',
                isSelected ? 'border-[color-mix(in_srgb,var(--color-base)_40%,white)]' : 'border-transparent hover:bg-surface',
              ].join(' ')}
              style={isSelected ? { background: BASE_TINT.replace(/_/g, ' ') } : undefined}
            >
              <span
                aria-hidden="true"
                className="flex h-7 w-7 flex-none items-center justify-center rounded-full bg-white text-sm font-semibold ring-1 ring-line-2"
              >
                {index + 1}
              </span>
              <span className="line-clamp-2 min-w-0 flex-1 break-words text-sm leading-[1.35]">{step.title}</span>
              {step.id === currentId ? (
                <span className="flex-none" title="현재 단계">
                  <span aria-hidden="true" className="block h-2 w-2 rounded-full bg-base" />
                  <span className="sr-only">현재 단계</span>
                </span>
              ) : null}
              <span className="flex-none text-xs tabular-nums text-ink-2">
                {counts.done}/{counts.total}
              </span>
            </li>
          )
        })}
      </ul>
      {adder ? <div className="flex-none border-t border-line p-1.5">{adder}</div> : null}
    </div>
  )
}

/**
 * 단계 하나. 웹(detail)은 오른쪽 상세 카드로 체크리스트만 안에서 스크롤하고 "항목 추가하기"는 하단에 고정한다.
 * 모바일(accordion)은 펼치고 접는 카드이며 카드 안 스크롤은 없다.
 */
function StepCard({
  variant,
  step,
  number,
  isFirst,
  isLast,
  hobbyId,
  current,
  expanded = true,
  onToggle,
  readOnly = false,
  onError,
  onNotice,
}: {
  variant: 'detail' | 'accordion'
  step: RoadmapStep
  number: number
  isFirst: boolean
  isLast: boolean
  hobbyId: number
  current: boolean
  expanded?: boolean
  onToggle?: () => void
  readOnly?: boolean
  onError: (message: string | null) => void
  onNotice: (message: string | null) => void
}) {
  const updateStep = useUpdateStep(hobbyId)
  const deleteStep = useDeleteStep(hobbyId)
  const addItem = useAddItem(hobbyId)
  const [mode, setMode] = useState<'view' | 'edit' | 'confirm'>('view')
  const [titleDraft, setTitleDraft] = useState(step.title)
  const [hintDraft, setHintDraft] = useState(step.periodHint ?? '')
  const [newItem, setNewItem] = useState('')
  const counts = countItems([step])
  const bodyId = `roadmap-step-${step.id}`
  const detail = variant === 'detail'

  function saveStep(event: FormEvent) {
    event.preventDefault()
    const title = titleDraft.trim()
    if (!title) {
      onError('단계 제목을 입력해 주세요')
      return
    }
    onError(null)
    updateStep.mutate(
      { id: step.id, patch: { title, periodHint: hintDraft.trim() || null } },
      { onSuccess: () => setMode('view'), onError: (error) => onError(errorMessageOf(error)) },
    )
  }

  function move(direction: 'UP' | 'DOWN') {
    onError(null)
    updateStep.mutate({ id: step.id, patch: { move: direction } }, { onError: (error) => onError(errorMessageOf(error)) })
  }

  function submitItem(event: FormEvent) {
    event.preventDefault()
    const text = newItem.trim()
    if (!text) return
    onError(null)
    addItem.mutate(
      { stepId: step.id, text },
      { onSuccess: () => setNewItem(''), onError: (error) => onError(errorMessageOf(error)) },
    )
  }

  const menu = readOnly ? null : (
    <ActionMenu
      label={`${step.title} 단계 메뉴`}
      items={[
        { label: '위로 이동', onSelect: () => move('UP'), disabled: isFirst || updateStep.isPending },
        { label: '아래로 이동', onSelect: () => move('DOWN'), disabled: isLast || updateStep.isPending },
        {
          label: '이름 바꾸기',
          onSelect: () => {
            setTitleDraft(step.title)
            setHintDraft(step.periodHint ?? '')
            setMode('edit')
          },
        },
        { label: '삭제', onSelect: () => setMode('confirm') },
      ]}
    />
  )

  const editForm =
    !readOnly && mode === 'edit' ? (
      <form className="flex flex-none flex-col gap-2 lg:flex-row lg:items-center" onSubmit={saveStep}>
        <Input
          value={titleDraft}
          maxLength={40}
          onChange={(event) => setTitleDraft(event.target.value)}
          aria-label="단계 제목"
          autoFocus
        />
        <Input
          value={hintDraft}
          maxLength={20}
          onChange={(event) => setHintDraft(event.target.value)}
          placeholder="기간 힌트(예: 약 2주)"
          aria-label="기간 힌트"
          className="lg:max-w-[10rem]"
        />
        <div className="flex gap-2">
          <Button type="submit" size="sm" disabled={updateStep.isPending}>
            저장
          </Button>
          <Button type="button" size="sm" variant="secondary" onClick={() => setMode('view')}>
            취소
          </Button>
        </div>
      </form>
    ) : null

  const confirmRow =
    !readOnly && mode === 'confirm' ? (
      <div className="flex flex-none flex-wrap items-center gap-x-3 gap-y-2 rounded-xl bg-surface px-3 py-2">
        <p className="text-sm">이 단계와 안의 항목을 지울까요?</p>
        <div className="flex gap-2">
          <Button
            type="button"
            size="sm"
            disabled={deleteStep.isPending}
            onClick={() => deleteStep.mutate(step.id, { onError: (error) => onError(errorMessageOf(error)) })}
          >
            지우기
          </Button>
          <Button type="button" size="sm" variant="secondary" onClick={() => setMode('view')}>
            그대로 둘게요
          </Button>
        </div>
      </div>
    ) : null

  const list = (
    <ul
      className={[
        'flex flex-col',
        detail ? 'scrollbar-thin-fade min-h-0 flex-1 overflow-y-auto overscroll-contain' : '',
      ].join(' ')}
    >
      {step.items.length === 0 ? (
        <li className="py-2 text-sm text-ink-2">{readOnly ? '항목이 없어요.' : '아직 항목이 없어요. 아래에서 추가해 보세요.'}</li>
      ) : null}
      {step.items.map((item, index) => (
        <ItemRow
          key={item.id}
          item={item}
          hobbyId={hobbyId}
          compact={detail}
          readOnly={readOnly}
          isFirst={index === 0}
          isLast={index === step.items.length - 1}
          onError={onError}
          onNotice={onNotice}
        />
      ))}
    </ul>
  )

  const addForm = readOnly ? null : step.items.length < MAX_ITEMS_PER_STEP ? (
      <form className="flex flex-none items-center gap-2" onSubmit={submitItem}>
        <Input
          value={newItem}
          maxLength={60}
          onChange={(event) => setNewItem(event.target.value)}
          placeholder="항목 추가하기"
          aria-label={`${step.title} 항목 추가`}
        />
        <Button type="submit" size="sm" variant="secondary" disabled={addItem.isPending || !newItem.trim()}>
          추가
        </Button>
      </form>
    ) : (
      <p className="flex-none text-xs text-ink-2">항목은 {MAX_ITEMS_PER_STEP}개까지 둘 수 있어요</p>
    )

  if (detail) {
    return (
      <section
        aria-label={`${number}단계 ${step.title}`}
        className="flex min-h-0 flex-col gap-2 rounded-2xl border border-line-2 bg-white p-4"
      >
        <div className="flex flex-none items-start gap-2">
          <div className="flex min-w-0 flex-1 flex-col">
            <h2 className="line-clamp-2 break-words text-card-title">{step.title}</h2>
            <p className="text-xs text-ink-2">
              {step.periodHint ? `${step.periodHint} · ` : ''}
              {counts.total}개 중 {counts.done}개 체크했어요
            </p>
          </div>
          {menu}
        </div>
        {editForm}
        {confirmRow}
        {list}
        {addForm ? <div className="flex-none border-t border-line pt-3">{addForm}</div> : null}
      </section>
    )
  }

  return (
    <li
      className={[
        'flex flex-col rounded-2xl border bg-white',
        current ? 'border-[color-mix(in_srgb,var(--color-base)_45%,white)] bg-[color-mix(in_srgb,var(--color-base)_6%,white)]' : 'border-line-2',
      ].join(' ')}
    >
      <div className="flex items-center gap-1 pr-1">
        <button
          type="button"
          aria-expanded={expanded}
          aria-controls={bodyId}
          onClick={onToggle}
          className="flex min-h-14 min-w-0 flex-1 items-center gap-3 rounded-2xl px-4 py-2 text-left"
        >
          <span
            aria-hidden="true"
            className="flex h-8 w-8 flex-none items-center justify-center rounded-full bg-[color-mix(in_srgb,var(--color-base)_14%,white)] text-sm font-semibold"
          >
            {number}
          </span>
          <span className="flex min-w-0 flex-1 flex-col">
            <span className="break-words text-card-title">{step.title}</span>
            <span className="text-caption-min text-ink-2">
              {step.periodHint ? `${step.periodHint} · ` : ''}
              {counts.total}개 중 {counts.done}개 체크했어요
            </span>
          </span>
          <ChevronDownIcon width={18} height={18} className={expanded ? 'rotate-180' : ''} />
        </button>
        {menu}
      </div>

      {expanded ? (
        <div id={bodyId} className="flex flex-col gap-3 px-4 pb-4">
          {editForm}
          {confirmRow}
          {list}
          {addForm}
        </div>
      ) : null}
    </li>
  )
}

function ItemRow({
  item,
  hobbyId,
  compact,
  readOnly = false,
  isFirst,
  isLast,
  onError,
  onNotice,
}: {
  item: RoadmapItem
  hobbyId: number
  /** 보관한 로드맵: 체크·수정을 막는다. */
  readOnly?: boolean
  /** 웹: 아이콘을 hover·포커스에서만 보여 준다. 모바일(false)은 ⋯ 메뉴. */
  compact: boolean
  isFirst: boolean
  isLast: boolean
  onError: (message: string | null) => void
  onNotice: (message: string | null) => void
}) {
  const setDone = useSetItemDone(hobbyId)
  const updateItem = useUpdateItem(hobbyId)
  const deleteItem = useDeleteItem(hobbyId)
  const [mode, setMode] = useState<'view' | 'edit' | 'confirm'>('view')
  const [draft, setDraft] = useState(item.text)

  function save(event: FormEvent) {
    event.preventDefault()
    const text = draft.trim()
    if (!text) {
      onError('항목을 입력해 주세요')
      return
    }
    onError(null)
    updateItem.mutate(
      { id: item.id, patch: { text } },
      { onSuccess: () => setMode('view'), onError: (error) => onError(errorMessageOf(error)) },
    )
  }

  function move(direction: 'UP' | 'DOWN') {
    onError(null)
    updateItem.mutate({ id: item.id, patch: { move: direction } }, { onError: (error) => onError(errorMessageOf(error)) })
  }

  function startEdit() {
    setDraft(item.text)
    setMode('edit')
  }

  if (mode === 'edit') {
    return (
      <li className="py-1.5">
        <form className="flex items-center gap-2" onSubmit={save}>
          <Input value={draft} maxLength={60} onChange={(event) => setDraft(event.target.value)} aria-label="항목" autoFocus />
          <button type="submit" className={iconButton} aria-label="저장" disabled={updateItem.isPending}>
            <CheckIcon width={18} height={18} />
          </button>
          <button type="button" className={iconButton} aria-label="취소" onClick={() => setMode('view')}>
            <XIcon width={18} height={18} />
          </button>
        </form>
      </li>
    )
  }

  if (mode === 'confirm') {
    return (
      <li className="flex flex-col gap-2 py-2 lg:flex-row lg:items-center">
        <p className="min-w-0 break-words text-sm">이 항목을 지울까요?</p>
        <div className="flex gap-2">
          <Button
            type="button"
            size="sm"
            disabled={deleteItem.isPending}
            onClick={() => deleteItem.mutate(item.id, { onError: (error) => onError(errorMessageOf(error)) })}
          >
            지우기
          </Button>
          <Button type="button" size="sm" variant="secondary" onClick={() => setMode('view')}>
            그대로 둘게요
          </Button>
        </div>
      </li>
    )
  }

  const checkbox = (
    <label className="flex min-h-11 min-w-0 flex-1 cursor-pointer items-center gap-3 py-1 lg:min-h-10">
      <input
        type="checkbox"
        checked={item.done}
        disabled={setDone.isPending || readOnly}
        onChange={(event) => {
          onError(null)
          onNotice(null)
          setDone.mutate(
            { id: item.id, done: event.target.checked },
            {
              onSuccess: (roadmap) => onNotice(roadmap.completedStepMessage ?? null),
              onError: (error) => onError(errorMessageOf(error)),
            },
          )
        }}
        className="h-5 w-5 flex-none accent-[var(--color-base)]"
      />
      <span
        className={['line-clamp-2 min-w-0 break-words text-body-min leading-[1.45]', item.done ? 'text-ink-2 line-through' : ''].join(' ')}
      >
        {item.text}
      </span>
    </label>
  )

  if (readOnly) {
    return <li className="flex items-center border-t border-line first:border-t-0">{checkbox}</li>
  }

  if (!compact) {
    return (
      <li className="flex items-center gap-1 border-t border-line first:border-t-0">
        {checkbox}
        <ActionMenu
          label={`${item.text} 항목 메뉴`}
          items={[
            { label: '고치기', onSelect: startEdit },
            { label: '위로 이동', onSelect: () => move('UP'), disabled: isFirst || updateItem.isPending },
            { label: '아래로 이동', onSelect: () => move('DOWN'), disabled: isLast || updateItem.isPending },
            { label: '지우기', onSelect: () => setMode('confirm') },
          ]}
        />
      </li>
    )
  }

  const reveal = 'opacity-0 group-hover:opacity-100 group-focus-within:opacity-100 focus-visible:opacity-100'
  return (
    <li className="group flex items-center gap-1 border-t border-line first:border-t-0">
      {checkbox}
      <div className="flex flex-none items-center">
        <button type="button" className={`${rowIconButton} ${reveal}`} aria-label={`${item.text} 항목 고치기`} onClick={startEdit}>
          <PencilIcon width={15} height={15} />
        </button>
        <button
          type="button"
          className={`${rowIconButton} ${reveal}`}
          aria-label={`${item.text} 항목을 위로`}
          disabled={isFirst || updateItem.isPending}
          onClick={() => move('UP')}
        >
          <ChevronDownIcon width={15} height={15} className="rotate-180" />
        </button>
        <button
          type="button"
          className={`${rowIconButton} ${reveal}`}
          aria-label={`${item.text} 항목을 아래로`}
          disabled={isLast || updateItem.isPending}
          onClick={() => move('DOWN')}
        >
          <ChevronDownIcon width={15} height={15} />
        </button>
        <button
          type="button"
          className={`${rowIconButton} ${reveal}`}
          aria-label={`${item.text} 항목 지우기`}
          onClick={() => setMode('confirm')}
        >
          <TrashIcon width={15} height={15} />
        </button>
      </div>
    </li>
  )
}

/** "11월 20일"(올해가 아니면 연도 포함). 서버의 날짜-시각 문자열 앞 10자리만 쓴다. */
function archiveDate(value: string): string {
  const [year, month, day] = value.slice(0, 10).split('-')
  const text = `${Number(month)}월 ${Number(day)}일`
  return Number(year) === new Date().getFullYear() ? text : `${year}년 ${text}`
}

const cardButton =
  'inline-flex h-11 items-center justify-center whitespace-nowrap rounded-lg border border-line-2 bg-white px-4 text-sm font-medium text-ink hover:bg-surface disabled:opacity-40 lg:h-9'

type ArchiveConfirm = null | { kind: 'restore' | 'delete'; id: number }

/**
 * 보관함: 보관한 로드맵 카드 목록과 읽기 전용 보기. 다시 쓰기는 진행 중 로드맵이 있으면 확인 후 맞바꾸고(swap),
 * 없으면 바로 복원한다. 삭제는 되돌릴 수 없어서 한 번 더 묻는다.
 */
function ArchiveView({ hobby, onRestored }: { hobby: Hobby; onRestored: () => void }) {
  const hobbyId = hobby.id
  const listQuery = useArchivedRoadmaps(hobbyId)
  const activeQuery = useRoadmap(hobbyId)
  const restore = useRestoreRoadmap(hobbyId)
  const remove = useDeleteArchivedRoadmap(hobbyId)
  const [viewingId, setViewingId] = useState<number | null>(null)
  const [confirm, setConfirm] = useState<ArchiveConfirm>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const busy = restore.isPending || remove.isPending

  function doRestore(id: number, swap: boolean) {
    setErrorMessage(null)
    restore.mutate(
      { id, swap },
      {
        onSuccess: () => {
          setConfirm(null)
          onRestored()
        },
        onError: (error) => {
          if (error instanceof ApiError && error.code === 'ROADMAP_ACTIVE_EXISTS') {
            // 그 사이 진행 중 로드맵이 생겼다면 확인을 거쳐 맞바꾼다.
            setConfirm({ kind: 'restore', id })
          } else {
            setConfirm(null)
            setErrorMessage(errorMessageOf(error))
          }
        },
      },
    )
  }

  function requestRestore(id: number) {
    if (activeQuery.data) {
      setConfirm({ kind: 'restore', id })
    } else {
      doRestore(id, false)
    }
  }

  function doDelete(id: number) {
    setErrorMessage(null)
    remove.mutate(id, {
      onSuccess: () => {
        setConfirm(null)
        if (viewingId === id) setViewingId(null)
      },
      onError: (error) => {
        setConfirm(null)
        setErrorMessage(errorMessageOf(error))
      },
    })
  }

  const dialog = confirm ? (
    <Dialog onClose={() => setConfirm(null)} labelledBy="roadmap-archive-confirm">
      <h2 id="roadmap-archive-confirm" className="font-serif text-xl font-semibold">
        {confirm.kind === 'restore' ? '이 로드맵을 다시 쓸까요?' : '이 로드맵을 삭제할까요?'}
      </h2>
      <p className="mt-3 text-body-min leading-[1.7] text-ink">
        {confirm.kind === 'restore'
          ? '지금 로드맵을 보관하고 이 로드맵을 다시 쓸까요?'
          : '삭제하면 되돌릴 수 없어요. 단계와 할 일도 함께 지워져요.'}
      </p>
      <div className="mt-5 flex gap-2 lg:justify-end">
        <Button type="button" variant="secondary" onClick={() => setConfirm(null)} className="flex-1! lg:flex-none!">
          그대로 둘게요
        </Button>
        <Button
          type="button"
          disabled={busy}
          onClick={() => (confirm.kind === 'restore' ? doRestore(confirm.id, true) : doDelete(confirm.id))}
          className="flex-1! lg:flex-none!"
        >
          {confirm.kind === 'restore' ? '보관하고 다시 쓰기' : '삭제'}
        </Button>
      </div>
    </Dialog>
  ) : null

  if (viewingId != null) {
    return (
      <div className="flex flex-col gap-3 lg:min-h-0 lg:flex-1">
        {errorMessage ? <Note variant="sand">{errorMessage}</Note> : null}
        <ArchivedDetail
          id={viewingId}
          hobby={hobby}
          restoreDisabled={busy || activeQuery.isLoading}
          onBack={() => setViewingId(null)}
          onRestore={() => requestRestore(viewingId)}
        />
        {dialog}
      </div>
    )
  }

  const list: RoadmapSummary[] = listQuery.data ?? []
  let body: ReactNode
  if (listQuery.isError) {
    body = <Note variant="sand">보관함을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</Note>
  } else if (listQuery.isLoading) {
    body = <Loading />
  } else if (list.length === 0) {
    body = (
      <div className="flex flex-col gap-1 rounded-2xl bg-surface p-5">
        <p className="text-body-min">보관한 로드맵이 없어요.</p>
        <p className="text-sm text-ink-2">새로 만들거나 보관하면 기존 로드맵이 여기에 남아요.</p>
      </div>
    )
  } else {
    body = (
      <ul className="scrollbar-thin-fade grid content-start gap-3 lg:min-h-0 lg:flex-1 lg:grid-cols-2 lg:overflow-y-auto lg:overscroll-contain">
        {list.map((item) => (
          <li key={item.id} className="flex min-w-0 flex-col gap-1.5 rounded-2xl border border-line-2 bg-white p-4">
            <p className="line-clamp-2 break-words font-serif text-lg leading-[1.45]" title={item.goalText}>
              {item.goalText}
            </p>
            <p className="text-sm text-ink-2">
              {archiveDate(item.createdAt)} → {archiveDate(item.archivedAt)} 보관
            </p>
            <p className="text-sm text-ink-2">
              {item.itemCount}개 중 {item.doneCount}개 체크했어요
            </p>
            <div className="mt-1 flex flex-wrap gap-2">
              <button type="button" className={cardButton} onClick={() => setViewingId(item.id)}>
                보기
              </button>
              <button
                type="button"
                className={cardButton}
                disabled={busy || activeQuery.isLoading}
                onClick={() => requestRestore(item.id)}
              >
                다시 쓰기
              </button>
              <button type="button" className={cardButton} disabled={busy} onClick={() => setConfirm({ kind: 'delete', id: item.id })}>
                삭제
              </button>
            </div>
          </li>
        ))}
      </ul>
    )
  }

  return (
    <div className="flex flex-col gap-3 lg:min-h-0 lg:flex-1">
      {errorMessage ? <Note variant="sand">{errorMessage}</Note> : null}
      {body}
      {dialog}
    </div>
  )
}

/** 보관한 로드맵 한 개를 현재 로드맵과 같은 2열(모바일은 아코디언)로 읽기 전용으로 보여 준다. */
function ArchivedDetail({
  id,
  hobby,
  restoreDisabled,
  onBack,
  onRestore,
}: {
  id: number
  hobby: Hobby
  restoreDisabled: boolean
  onBack: () => void
  onRestore: () => void
}) {
  const { data: roadmap, isLoading, isError } = useRoadmapById(id)

  if (isError) {
    return (
      <div className="flex flex-col items-start gap-3">
        <Note variant="sand">로드맵을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</Note>
        <button type="button" className={cardButton} onClick={onBack}>
          보관함으로
        </button>
      </div>
    )
  }
  if (isLoading || !roadmap) return <Loading />

  const counts = countItems(roadmap.steps)
  const banner = (
    <div className="flex flex-none flex-wrap items-center gap-x-3 gap-y-2">
      <button type="button" className={cardButton} onClick={onBack}>
        ← 보관함
      </button>
      <div className="min-w-0 basis-full lg:flex-1 lg:basis-auto">
        <p title={roadmap.goalText} className="break-words font-serif text-lg leading-[1.5] lg:truncate lg:text-[22px] lg:leading-[1.4]">
          {roadmap.goalText}
        </p>
      </div>
      <p className="min-w-0 flex-1 text-sm text-ink-2 lg:flex-none">
        <span className="font-medium text-ink">보관한 로드맵이에요</span> · {counts.total}개 중 {counts.done}개 체크했어요
      </p>
      <Button type="button" disabled={restoreDisabled} onClick={onRestore}>
        다시 쓰기
      </Button>
    </div>
  )

  return (
    <RoadmapBody
      roadmap={roadmap}
      hobby={hobby}
      goalChanged={false}
      confirming={null}
      onAskConfirm={() => {}}
      onRenew={() => {}}
      onArchiveOnly={() => {}}
      onError={() => {}}
      onNotice={() => {}}
      readOnly
      banner={banner}
    />
  )
}
