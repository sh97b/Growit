import { useEffect, useState, type FormEvent } from 'react'
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

/** 분야 행 하나의 대략적 높이(이름+기록 수 두 줄, py-4, 테두리, rem). ListPane 개수 계산에 쓴다. */
const ROW_HEIGHT_REM = 5
const MOBILE_PAGE_SIZE = 5

function errorMessageOf(error: unknown): string {
  return error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요'
}

function HobbyRow({ hobby, onError }: { hobby: Hobby; onError: (message: string) => void }) {
  const [renaming, setRenaming] = useState(false)
  const [draftName, setDraftName] = useState(hobby.name)
  const updateHobby = useUpdateHobby()

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

  function toggleArchived() {
    updateHobby.mutate(
      { id: hobby.id, patch: { archived: !hobby.archived } },
      { onError: (error) => onError(errorMessageOf(error)) },
    )
  }

  if (renaming) {
    return (
      <div className="flex items-center gap-2 border-t border-line py-4 first:border-t-0">
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

  return (
    <div className="flex items-center gap-4 border-t border-line py-4 first:border-t-0">
      <div className="flex flex-1 flex-col gap-0.5">
        <span className="text-card-title">{hobby.name}</span>
        <span className="text-caption-min text-ink-2">기록 {hobby.recordCount}개</span>
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
    'inline-flex min-h-9 flex-1 items-center justify-center whitespace-nowrap rounded-md px-3 text-sm font-medium lg:flex-none',
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

  // S1 A단계: ready로 렌더를 막지 않는다 — 기본값(또는 직전 값)으로 즉시 그리고, 측정되면 보정한다.
  const { containerRef, pageSize: fitPageSize } = useFitPageSize({
    itemHeightRem: ROW_HEIGHT_REM,
    min: 3,
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

      <div role="tablist" aria-label="분야 목록" className="flex flex-none gap-1 rounded-lg border border-line-2 bg-white p-1">
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
            <p className="py-6 text-center text-sm text-ink-2">
              {tab === 'active' ? '아직 활동 분야가 없어요.' : '보관한 분야가 없어요.'}
            </p>
          ) : (
            <div className="flex flex-col">
              {pageItems.map((hobby) => (
                <HobbyRow key={hobby.id} hobby={hobby} onError={setErrorMessage} />
              ))}
            </div>
          )}
        </ListPane>
      )}
    </SettingsPage>
  )
}
