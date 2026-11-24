import { useEffect, useLayoutEffect, useRef, useState, type FormEvent } from 'react'
import { Button } from '../../components/Button'
import { Field } from '../../components/Field'
import { Input } from '../../components/Input'
import { ListPane } from '../../components/ListPane'
import { Loading } from '../../components/Loading'
import { Note } from '../../components/Note'
import { SettingsPage } from '../../components/PageLayout'
import { Pagination } from '../../components/Pagination'
import { ApiError } from '../../lib/api'
import type { Hobby } from '../../lib/hobbies'
import { useCreateHobby, useHobbies, useUpdateHobby } from '../../hooks/useHobbies'
import { useFitPageSize, useStablePageOnResize } from '../../hooks/useFitPageSize'
import { useIsDesktop } from '../../hooks/useIsDesktop'
import { PencilIcon } from '../../components/icons'

/** 실제 행 높이를 재기 전(첫 렌더)에만 쓰는 추정 높이(rem). 잰 값이 생기면 그걸 쓴다. */
const ROW_HEIGHT_REM = 4.5
const MOBILE_PAGE_SIZE = 5
const GOAL_MAX = 200

function errorMessageOf(error: unknown): string {
  return error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요'
}

function HobbyRow({
  hobby,
  onError,
  onMeasure,
}: {
  hobby: Hobby
  onError: (message: string) => void
  /** 첫 행이 평소 모양일 때 실제 높이(px)를 알려 준다. 페이지당 행 수 계산에 쓴다. */
  onMeasure?: (heightPx: number) => void
}) {
  const [renaming, setRenaming] = useState(false)
  const [draftName, setDraftName] = useState(hobby.name)
  const [editingGoal, setEditingGoal] = useState(false)
  const [draftGoal, setDraftGoal] = useState(hobby.goal ?? '')
  const updateHobby = useUpdateHobby()
  const rowRef = useRef<HTMLDivElement | null>(null)
  const editing = renaming || editingGoal

  // 편집 중에는 높이가 달라지니 평소 모양일 때만 잰다. 첫 행에는 위 테두리가 없어(first:border-t-0) 1px를 더한다.
  useLayoutEffect(() => {
    if (!onMeasure || editing || !rowRef.current) return
    onMeasure(Math.ceil(rowRef.current.getBoundingClientRect().height) + 1)
  })

  function startRename() {
    setDraftName(hobby.name)
    setRenaming(true)
  }

  function saveRename() {
    const trimmed = draftName.trim()
    if (!trimmed) {
      onError('분야 이름을 입력해 주세요')
      return
    }
    updateHobby.mutate(
      { id: hobby.id, patch: { name: trimmed } },
      {
        onSuccess: () => setRenaming(false),
        onError: (error) => onError(errorMessageOf(error)),
      },
    )
  }

  function saveGoal(event: FormEvent) {
    event.preventDefault()
    const trimmed = draftGoal.trim()
    // 비우면 목표를 지운다(서버는 null로 보내면 비운다).
    updateHobby.mutate(
      { id: hobby.id, patch: { goal: trimmed === '' ? null : trimmed } },
      {
        onSuccess: () => setEditingGoal(false),
        onError: (error) => onError(errorMessageOf(error)),
      },
    )
  }

  function toggleArchived() {
    updateHobby.mutate(
      { id: hobby.id, patch: { archived: !hobby.archived } },
      { onError: (error) => onError(errorMessageOf(error)) },
    )
  }

  const rowClass = 'flex items-center gap-2 border-t border-line py-3 first:border-t-0'

  if (renaming) {
    return (
      <div ref={rowRef} className={rowClass}>
        <Input
          value={draftName}
          onChange={(event) => setDraftName(event.target.value)}
          autoFocus
          className="flex-1"
        />
        <Button type="button" size="sm" disabled={updateHobby.isPending} onClick={saveRename}>
          저장
        </Button>
        <Button type="button" variant="secondary" size="sm" onClick={() => setRenaming(false)}>
          취소
        </Button>
      </div>
    )
  }

  if (editingGoal) {
    return (
      <form ref={rowRef as React.RefObject<HTMLFormElement | null>} className={rowClass} onSubmit={saveGoal}>
        <Input
          value={draftGoal}
          maxLength={GOAL_MAX}
          onChange={(event) => setDraftGoal(event.target.value)}
          placeholder="예: 코드 전환 익숙해지기"
          aria-label={`${hobby.name} 목표`}
          autoFocus
          className="flex-1"
        />
        <Button type="submit" size="sm" disabled={updateHobby.isPending}>
          저장
        </Button>
        <Button type="button" size="sm" variant="secondary" onClick={() => setEditingGoal(false)}>
          취소
        </Button>
      </form>
    )
  }

  const goal = hobby.goal?.trim() ?? ''

  function openGoalEdit() {
    setDraftGoal(hobby.goal ?? '')
    setEditingGoal(true)
  }

  return (
    <div ref={rowRef} className={`${rowClass} gap-4`}>
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="truncate text-card-title">{hobby.name}</span>
        <div className="flex min-w-0 items-center gap-1 text-caption-min text-ink-2">
          <span className="min-w-0 truncate">
            기록 {hobby.recordCount}개{goal ? ` · 목표: ${goal}` : ''}
          </span>
          {!hobby.archived && goal ? (
            <button
              type="button"
              aria-label="목표 고치기"
              onClick={openGoalEdit}
              className="-my-3 inline-flex h-11 w-11 flex-none items-center justify-center rounded-full text-ink-2 hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)] focus-visible:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]"
            >
              <PencilIcon width={14} height={14} />
            </button>
          ) : null}
          {!hobby.archived && !goal ? (
            <>
              <span aria-hidden="true">·</span>
              <button
                type="button"
                onClick={openGoalEdit}
                className="-my-3 inline-flex min-h-11 flex-none items-center rounded-full px-1 text-caption-min font-medium text-base-ink underline-offset-2 hover:underline focus-visible:underline"
              >
                목표 적기
              </button>
            </>
          ) : null}
        </div>
      </div>
      {!hobby.archived ? (
        <Button type="button" variant="secondary" size="sm" onClick={startRename}>
          이름 바꾸기
        </Button>
      ) : null}
      <Button
        type="button"
        size="sm"
        className="text-base-ink hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)] focus-visible:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]"
        variant="ghost"
        disabled={updateHobby.isPending}
        onClick={toggleArchived}
      >
        {hobby.archived ? '다시 꺼내기' : '보관'}
      </Button>
    </div>
  )
}

function tabButtonClass(active: boolean): string {
  return [
    'inline-flex min-h-9 flex-1 items-center justify-center whitespace-nowrap rounded-md px-3 t-sub font-medium lg:flex-none',
    active ? 'bg-ink text-white' : 'bg-transparent text-ink-2',
  ].join(' ')
}

/**
 * 활동 분야 관리(R1 4번): 분야가 늘어도 페이지가 길어지지 않게 [사용 중|보관함] 탭 + ListPane
 * 페이지네이션으로 바꿨다. 데이터는 GET /api/hobbies?includeArchived=true 한 번으로 받아
 * (useHobbies(true)) 클라이언트에서 active/archived로 나누고 탭별로 다시 페이지를 자른다.
 */
export function HobbiesPage() {
  const { data: hobbies, isLoading } = useHobbies(true)
  const createHobby = useCreateHobby()
  const [newName, setNewName] = useState('')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [addedNotice, setAddedNotice] = useState(false)
  const [tab, setTab] = useState<'active' | 'archived'>('active')
  const [page, setPage] = useState(0)
  const isDesktop = useIsDesktop()
  // 첫 행을 실제로 잰 높이. 이 값으로 한 페이지에 완전히 들어가는 행 수만 보여 줘서 잘린 행이 없게 한다.
  const [rowHeightPx, setRowHeightPx] = useState<number | null>(null)

  // S1 A단계: ready로 렌더를 막지 않는다 — 기본값(또는 직전 값)으로 즉시 그리고, 측정되면 보정한다.
  const { containerRef, pageSize: fitPageSize } = useFitPageSize({
    itemHeightRem: ROW_HEIGHT_REM,
    itemHeightPx: rowHeightPx,
    min: 1,
    max: 20,
    defaultSize: 6,
  })
  const pageSize = isDesktop ? fitPageSize : MOBILE_PAGE_SIZE
  useStablePageOnResize(page, setPage, pageSize)

  useEffect(() => {
    if (!addedNotice) return
    const timer = setTimeout(() => setAddedNotice(false), 1500)
    return () => clearTimeout(timer)
  }, [addedNotice])

  const activeHobbies = hobbies?.filter((hobby) => !hobby.archived) ?? []
  const archivedHobbies = hobbies?.filter((hobby) => hobby.archived) ?? []
  const listForTab = tab === 'active' ? activeHobbies : archivedHobbies
  const totalPages = Math.max(1, Math.ceil(listForTab.length / Math.max(1, pageSize)))
  const safePage = Math.min(page, totalPages - 1)
  const pageItems = listForTab.slice(safePage * pageSize, safePage * pageSize + pageSize)

  function switchTab(next: 'active' | 'archived') {
    setTab(next)
    setPage(0)
  }

  function handleCreate(event: FormEvent) {
    event.preventDefault()
    const trimmed = newName.trim()
    if (!trimmed) {
      setErrorMessage('분야 이름을 입력해 주세요')
      return
    }
    setErrorMessage(null)
    createHobby.mutate(
      { name: trimmed },
      {
        onSuccess: () => {
          setNewName('')
          // 사용 중 탭의 마지막 페이지로 옮기지 않고 보던 자리 그대로 둔 채 짧은 안내만 보여준다.
          setAddedNotice(true)
        },
        onError: (error) => setErrorMessage(errorMessageOf(error)),
      },
    )
  }

  return (
    <SettingsPage title="활동 분야 관리">
      {errorMessage ? <Note variant="sand">{errorMessage}</Note> : null}
      {addedNotice ? <Note variant="calm">추가했어요</Note> : null}

      <form className="flex flex-none flex-col gap-2 lg:flex-row lg:items-end lg:gap-3" onSubmit={handleCreate}>
        <div className="flex-1">
          <Field label="분야 이름" htmlFor="new-hobby-name">
            <Input
              id="new-hobby-name"
              value={newName}
              onChange={(event) => setNewName(event.target.value)}
              placeholder="예: 드로잉"
            />
          </Field>
        </div>
        <Button type="submit" disabled={createHobby.isPending}>
          추가하기
        </Button>
      </form>

      <div
        role="tablist"
        aria-label="분야 목록"
        className="flex flex-none gap-1 rounded-lg border border-line-2 bg-white p-1 lg:w-fit lg:self-start"
      >
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'active'}
          className={tabButtonClass(tab === 'active')}
          onClick={() => switchTab('active')}
        >
          사용 중 {activeHobbies.length}
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'archived'}
          className={tabButtonClass(tab === 'archived')}
          onClick={() => switchTab('archived')}
        >
          보관함 {archivedHobbies.length}
        </button>
      </div>

      {isLoading ? (
        <Loading />
      ) : (
        <ListPane
          listRef={containerRef}
          className="lg:min-h-0 lg:flex-1"
          footer={<Pagination page={safePage} totalPages={totalPages} onChange={setPage} />}
        >
          {pageItems.length === 0 ? (
            <p className="py-6 text-center t-sub text-ink-2">
              {tab === 'active' ? '아직 활동 분야가 없어요.' : '보관한 분야가 없어요.'}
            </p>
          ) : (
            <div className="flex flex-col">
              {pageItems.map((hobby, index) => (
                <HobbyRow
                  key={hobby.id}
                  hobby={hobby}
                  onError={setErrorMessage}
                  onMeasure={index === 0 ? setRowHeightPx : undefined}
                />
              ))}
            </div>
          )}
        </ListPane>
      )}
    </SettingsPage>
  )
}
