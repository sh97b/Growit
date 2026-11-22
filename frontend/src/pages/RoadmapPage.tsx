import { useState, type FormEvent, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, Navigate, useParams, useSearchParams } from 'react-router-dom'
import { Spinner } from '../components/AiProgress'
import { Button } from '../components/Button'
import { Full } from '../components/PageLayout'
import { HobbyChipRow } from '../components/HobbyChipRow'
import { Input } from '../components/Input'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
import { CheckIcon, ChevronDownIcon, PencilIcon, PlusIcon, TrashIcon, XIcon } from '../components/icons'
import {
  useAddItem,
  useAddStep,
  useArchiveRoadmap,
  useCreateRoadmap,
  useDeleteItem,
  useDeleteStep,
  useRoadmap,
  useSetItemDone,
  useUpdateItem,
  useUpdateRoadmapTarget,
  useUpdateStep,
} from '../hooks/useRoadmap'
import { useHobbies, useUpdateHobby, type Hobby } from '../hooks/useHobbies'
import { fetchRecords } from '../lib/records'
import { ApiError } from '../lib/api'
import { formatYearMonthDay, todayLocalDate } from '../lib/date'
import type { Roadmap, RoadmapItem, RoadmapStep } from '../lib/roadmap'

const MAX_STEPS = 8
const MAX_ITEMS_PER_STEP = 8
const NOTICE = 'AI가 제안한 일반적인 연습 순서예요. 내 몸과 상황에 맞게 바꿔 쓰고, 통증이나 건강 문제가 있으면 전문가와 상의해요.'

const iconButton =
  'inline-flex h-11 w-11 flex-none items-center justify-center rounded-lg text-ink-2 hover:bg-[color-mix(in_srgb,var(--color-ink)_6%,transparent)] focus-visible:bg-[color-mix(in_srgb,var(--color-ink)_6%,transparent)] disabled:opacity-40'

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

/**
 * 상단 메뉴의 로드맵 화면. 분야는 기록 작성 화면과 같은 칩 줄로 고르고, 마지막으로 고른 분야를 기억한다
 * (처음에는 가장 최근에 기록한 분야). 분야 하나의 화면은 HobbyRoadmap이 상태별로 그린다.
 */
export function RoadmapPage() {
  const [searchParams] = useSearchParams()
  const { data: hobbies, isLoading } = useHobbies(false)
  const recentQuery = useQuery({ queryKey: ['records', 'recent'], queryFn: () => fetchRecords({ size: 1 }) })
  const [pickedId, setPickedId] = useState<number | null>(() => {
    const fromUrl = Number(searchParams.get('hobby'))
    return Number.isFinite(fromUrl) && fromUrl > 0 ? fromUrl : readStoredHobbyId()
  })

  const list = hobbies ?? []
  const recentHobbyId = recentQuery.data?.items[0]?.hobbyId ?? null
  const waitingForRecent = pickedId == null && recentQuery.isLoading
  const selected =
    list.find((hobby) => hobby.id === pickedId) ??
    list.find((hobby) => hobby.id === recentHobbyId) ??
    list[0] ??
    null

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
  } else {
    body = <HobbyRoadmap key={selected.id} hobby={selected} />
  }

  return (
    <Full>
      <h1 className="text-page-title">로드맵</h1>
      {selected ? <HobbyChipRow hobbies={list} selectedId={selected.id} onSelect={pick} /> : null}
      {body}
    </Full>
  )
}

/** 목표 한 줄과 ✎ 수정. 비우고 저장하면 목표를 지운다. */
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
      <form className="flex flex-col gap-2 lg:flex-row lg:items-center" onSubmit={save}>
        <Input
          value={draft}
          maxLength={200}
          onChange={(event) => setDraft(event.target.value)}
          placeholder="예: 코드 전환 익숙해지기"
          aria-label={`${hobby.name} 목표`}
          autoFocus={editing}
          className="lg:max-w-md"
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
    <div className="flex items-start gap-1">
      <p className="min-w-0 flex-1 break-words font-serif text-lg leading-[1.55] lg:text-[1.5rem]">{goal}</p>
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

function HobbyRoadmap({ hobby }: { hobby: Hobby }) {
  const hobbyId = hobby.id
  const { data: roadmap, isLoading, isError } = useRoadmap(hobbyId)
  const create = useCreateRoadmap(hobbyId)
  const archive = useArchiveRoadmap(hobbyId)
  const [targetDraft, setTargetDraft] = useState('')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [confirmingRenew, setConfirmingRenew] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)

  // 로드맵 만들기는 이 버튼을 눌렀을 때만 요청한다(화면을 열 때 자동으로 만들지 않는다).
  function generate(targetDate: string | null, mode: 'AI' | 'MANUAL' = 'AI') {
    setErrorMessage(null)
    create.mutate({ targetDate, mode }, { onError: (error) => setErrorMessage(errorMessageOf(error)) })
  }

  function renew() {
    if (!roadmap) return
    setErrorMessage(null)
    setConfirmingRenew(false)
    archive.mutate(roadmap.id, {
      onSuccess: () => generate(roadmap.targetDate),
      onError: (error) => setErrorMessage(errorMessageOf(error)),
    })
  }

  function archiveOnly() {
    if (!roadmap) return
    setErrorMessage(null)
    setConfirmingRenew(false)
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
      <div role="status" aria-live="polite" className="flex flex-col gap-4 rounded-2xl bg-surface p-6">
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
      <div className="flex flex-col gap-4">
        <GoalLine hobby={hobby} onError={setErrorMessage} />
        {goal ? (
          <>
            <label className="flex max-w-xs flex-col gap-1.5 text-sm font-medium">
              목표일(선택)
              <Input
                type="date"
                value={targetDraft}
                min={todayLocalDate()}
                onChange={(event) => setTargetDraft(event.target.value)}
              />
            </label>
            <Button type="button" className="w-fit" onClick={() => generate(targetDraft || null)}>
              {errorMessage ? '다시 시도' : '로드맵 만들기'}
            </Button>
            <p className="text-body-min text-ink-2">AI가 목표까지의 단계를 제안해요. 단계와 할 일은 마음대로 고칠 수 있어요</p>
            <div className="flex flex-col items-start">
              <button
                type="button"
                onClick={() => generate(targetDraft || null, 'MANUAL')}
                className="inline-flex min-h-11 items-center rounded-full px-1 text-sm font-medium text-base-ink underline underline-offset-2"
              >
                AI 없이 직접 만들기
              </button>
              <span className="text-caption-min text-ink-2">단계와 할 일을 직접 적어요</span>
            </div>
          </>
        ) : (
          <p className="text-body-min text-ink-2">목표를 적으면 로드맵을 만들 수 있어요.</p>
        )}
      </div>
    )
  } else {
    body = (
      <RoadmapBody
        roadmap={roadmap}
        hobbyId={hobbyId}
        goalNode={<GoalLine hobby={hobby} onError={setErrorMessage} />}
        goalChanged={goal !== '' && goal !== roadmap.goalText}
        confirmingRenew={confirmingRenew}
        onAskRenew={() => setConfirmingRenew(true)}
        onCancelRenew={() => setConfirmingRenew(false)}
        onRenew={renew}
        onArchiveOnly={archiveOnly}
        onError={setErrorMessage}
        onNotice={setNotice}
      />
    )
  }

  return (
    <div className="flex flex-col gap-5">
      {errorMessage ? <Note variant="sand">{errorMessage}</Note> : null}
      {notice ? <Note variant="calm">{notice}</Note> : null}
      {body}
      <p className="text-caption-min leading-[1.7] text-ink-2">{NOTICE}</p>
    </div>
  )
}

function RoadmapBody({
  roadmap,
  hobbyId,
  goalNode,
  goalChanged,
  confirmingRenew,
  onAskRenew,
  onCancelRenew,
  onRenew,
  onArchiveOnly,
  onError,
  onNotice,
}: {
  roadmap: Roadmap
  hobbyId: number
  goalNode: ReactNode
  /** 분야의 목표가 이 로드맵을 만들 때의 목표와 달라졌을 때 true. */
  goalChanged: boolean
  confirmingRenew: boolean
  onAskRenew: () => void
  onCancelRenew: () => void
  onRenew: () => void
  onArchiveOnly: () => void
  onError: (message: string | null) => void
  onNotice: (message: string | null) => void
}) {
  const updateTarget = useUpdateRoadmapTarget(hobbyId)
  const addStep = useAddStep(hobbyId)
  const [open, setOpen] = useState<Record<number, boolean>>({})
  const [newStepTitle, setNewStepTitle] = useState('')
  const counts = countItems(roadmap.steps)
  const currentId = currentStepId(roadmap.steps)

  function changeTarget(value: string) {
    onError(null)
    updateTarget.mutate(
      { id: roadmap.id, targetDate: value || null },
      { onError: (error) => onError(errorMessageOf(error)) },
    )
  }

  function submitStep(event: FormEvent) {
    event.preventDefault()
    const trimmed = newStepTitle.trim()
    if (!trimmed) return
    onError(null)
    addStep.mutate(
      { roadmapId: roadmap.id, title: trimmed },
      { onSuccess: () => setNewStepTitle(''), onError: (error) => onError(errorMessageOf(error)) },
    )
  }

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-3">
        {goalNode}
        {goalChanged ? (
          <p className="text-caption-min text-ink-2">
            이 로드맵은 &lsquo;{roadmap.goalText}&rsquo; 목표로 만들었어요. 새로 만들면 지금 목표로 다시 제안해요.
          </p>
        ) : null}
        <div className="flex flex-col gap-2 lg:flex-row lg:items-center lg:gap-4">
          <label className="flex items-center gap-2 text-sm text-ink-2">
            목표일
            <Input
              type="date"
              className="h-11 w-auto"
              value={roadmap.targetDate ?? ''}
              min={todayLocalDate()}
              onChange={(event) => changeTarget(event.target.value)}
              aria-label="목표일"
            />
          </label>
          {roadmap.targetDate ? (
            <span className="text-caption-min text-ink-2">{formatYearMonthDay(roadmap.targetDate)}</span>
          ) : null}
        </div>
        <p className="text-body-min text-ink-2" aria-live="polite">
          {counts.total}개 중 {counts.done}개 체크했어요
        </p>
      </div>

      {roadmap.steps.length === 0 ? (
        <div className="flex flex-col items-start gap-3 rounded-2xl border border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_9%,white)] p-5">
          <p className="text-body-min">아직 단계가 없어요. 목표까지 가는 첫 단계를 적어 보세요.</p>
          <Button type="button" onClick={() => document.getElementById('new-step-title')?.focus()}>
            첫 단계 추가하기
          </Button>
        </div>
      ) : null}

      <ol className="flex flex-col gap-3">
        {roadmap.steps.map((step, index) => (
          <StepCard
            key={step.id}
            step={step}
            number={index + 1}
            isFirst={index === 0}
            isLast={index === roadmap.steps.length - 1}
            hobbyId={hobbyId}
            current={step.id === currentId}
            expanded={open[step.id] ?? step.id === currentId}
            onToggle={() => setOpen((prev) => ({ ...prev, [step.id]: !(prev[step.id] ?? step.id === currentId) }))}
            onError={onError}
            onNotice={onNotice}
          />
        ))}
      </ol>

      {roadmap.steps.length < MAX_STEPS ? (
        <form className="flex flex-col gap-2 lg:flex-row lg:items-center" onSubmit={submitStep}>
          <Input
            value={newStepTitle}
            maxLength={40}
            onChange={(event) => setNewStepTitle(event.target.value)}
            placeholder="단계 추가하기"
            id="new-step-title"
            aria-label="새 단계 제목"
            className="lg:max-w-sm"
          />
          <Button type="submit" variant="secondary" disabled={addStep.isPending || !newStepTitle.trim()}>
            <PlusIcon width={16} height={16} />
            단계 추가
          </Button>
        </form>
      ) : null}

      <div className="flex flex-col gap-2 border-t border-line pt-4">
        {confirmingRenew ? (
          <div className="flex flex-col gap-2 lg:flex-row lg:items-center">
            <p className="text-body-min">지금 로드맵은 보관하고 새로 만들까요?</p>
            <div className="flex flex-wrap gap-2">
              <Button type="button" onClick={onRenew}>
                새로 만들기
              </Button>
              <Button type="button" variant="secondary" onClick={onArchiveOnly}>
                보관만 하기
              </Button>
              <Button type="button" variant="secondary" onClick={onCancelRenew}>
                그대로 둘게요
              </Button>
            </div>
          </div>
        ) : (
          <Button type="button" variant="secondary" className="w-fit" onClick={onAskRenew}>
            새로 만들기
          </Button>
        )}
      </div>
    </div>
  )
}

function StepCard({
  step,
  number,
  isFirst,
  isLast,
  hobbyId,
  current,
  expanded,
  onToggle,
  onError,
  onNotice,
}: {
  step: RoadmapStep
  number: number
  isFirst: boolean
  isLast: boolean
  hobbyId: number
  current: boolean
  expanded: boolean
  onToggle: () => void
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
      </div>

      {expanded ? (
        <div id={bodyId} className="flex flex-col gap-3 px-4 pb-4">
          {mode === 'edit' ? (
            <form className="flex flex-col gap-2 lg:flex-row lg:items-center" onSubmit={saveStep}>
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
          ) : null}

          <ul className="flex flex-col">
            {step.items.map((item, index) => (
              <ItemRow
                key={item.id}
                item={item}
                hobbyId={hobbyId}
                isFirst={index === 0}
                isLast={index === step.items.length - 1}
                onError={onError}
                onNotice={onNotice}
              />
            ))}
          </ul>

          {step.items.length < MAX_ITEMS_PER_STEP ? (
            <form className="flex flex-col gap-2 lg:flex-row lg:items-center" onSubmit={submitItem}>
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
          ) : null}

          {mode === 'confirm' ? (
            <div className="flex flex-col gap-2 lg:flex-row lg:items-center">
              <p className="text-body-min">이 단계와 안의 항목을 지울까요?</p>
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
          ) : (
            <div className="flex items-center gap-1 border-t border-line pt-2">
              <button
                type="button"
                className={iconButton}
                aria-label={`${step.title} 단계 이름·기간 고치기`}
                onClick={() => {
                  setTitleDraft(step.title)
                  setHintDraft(step.periodHint ?? '')
                  setMode('edit')
                }}
              >
                <PencilIcon width={18} height={18} />
              </button>
              <button
                type="button"
                className={iconButton}
                aria-label={`${step.title} 단계를 위로`}
                disabled={isFirst || updateStep.isPending}
                onClick={() => move('UP')}
              >
                <ChevronDownIcon width={18} height={18} className="rotate-180" />
              </button>
              <button
                type="button"
                className={iconButton}
                aria-label={`${step.title} 단계를 아래로`}
                disabled={isLast || updateStep.isPending}
                onClick={() => move('DOWN')}
              >
                <ChevronDownIcon width={18} height={18} />
              </button>
              <button
                type="button"
                className={iconButton}
                aria-label={`${step.title} 단계 지우기`}
                onClick={() => setMode('confirm')}
              >
                <TrashIcon width={18} height={18} />
              </button>
            </div>
          )}
        </div>
      ) : null}
    </li>
  )
}

function ItemRow({
  item,
  hobbyId,
  isFirst,
  isLast,
  onError,
  onNotice,
}: {
  item: RoadmapItem
  hobbyId: number
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
        <p className="min-w-0 break-words text-body-min">이 항목을 지울까요?</p>
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

  return (
    <li className="flex items-center gap-1 border-t border-line first:border-t-0">
      <label className="flex min-h-11 min-w-0 flex-1 cursor-pointer items-center gap-3 py-1">
        <input
          type="checkbox"
          checked={item.done}
          disabled={setDone.isPending}
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
        <span className={['min-w-0 break-words text-body-min', item.done ? 'text-ink-2 line-through' : ''].join(' ')}>
          {item.text}
        </span>
      </label>
      <button
        type="button"
        className={iconButton}
        aria-label={`${item.text} 항목 고치기`}
        onClick={() => {
          setDraft(item.text)
          setMode('edit')
        }}
      >
        <PencilIcon width={16} height={16} />
      </button>
      <button
        type="button"
        className={iconButton}
        aria-label={`${item.text} 항목을 위로`}
        disabled={isFirst || updateItem.isPending}
        onClick={() => move('UP')}
      >
        <ChevronDownIcon width={16} height={16} className="rotate-180" />
      </button>
      <button
        type="button"
        className={iconButton}
        aria-label={`${item.text} 항목을 아래로`}
        disabled={isLast || updateItem.isPending}
        onClick={() => move('DOWN')}
      >
        <ChevronDownIcon width={16} height={16} />
      </button>
      <button type="button" className={iconButton} aria-label={`${item.text} 항목 지우기`} onClick={() => setMode('confirm')}>
        <TrashIcon width={16} height={16} />
      </button>
    </li>
  )
}
