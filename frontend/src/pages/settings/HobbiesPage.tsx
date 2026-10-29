import { useState, type FormEvent } from 'react'
import { Button } from '../../components/Button'
import { Field } from '../../components/Field'
import { Input } from '../../components/Input'
import { Loading } from '../../components/Loading'
import { Note } from '../../components/Note'
import { ApiError } from '../../lib/api'
import type { Hobby } from '../../lib/hobbies'
import { useCreateHobby, useHobbies, useUpdateHobby } from '../../hooks/useHobbies'

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
        <span className="font-serif text-lg font-semibold">{hobby.name}</span>
        <span className="text-[13px] text-ink-2">기록 {hobby.recordCount}개</span>
      </div>
      {!hobby.archived ? (
        <Button type="button" variant="secondary" size="sm" onClick={startRename}>
          이름 바꾸기
        </Button>
      ) : null}
      <Button
        type="button"
        size="sm"
        className="underline"
        variant="ghost"
        disabled={updateHobby.isPending}
        onClick={toggleArchived}
      >
        {hobby.archived ? '다시 꺼내기' : '보관'}
      </Button>
    </div>
  )
}

export function HobbiesPage() {
  const { data: hobbies, isLoading } = useHobbies(true)
  const createHobby = useCreateHobby()
  const [newName, setNewName] = useState('')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const activeHobbies = hobbies?.filter((hobby) => !hobby.archived) ?? []
  const archivedHobbies = hobbies?.filter((hobby) => hobby.archived) ?? []

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
        onSuccess: () => setNewName(''),
        onError: (error) => setErrorMessage(errorMessageOf(error)),
      },
    )
  }

  const listSection = isLoading ? (
    <Loading />
  ) : (
    <>
      <div className="flex flex-col">
        {activeHobbies.map((hobby) => (
          <HobbyRow key={hobby.id} hobby={hobby} onError={setErrorMessage} />
        ))}
      </div>

      {archivedHobbies.length > 0 ? (
        <div className="flex flex-col gap-2">
          <h2 className="font-serif text-lg font-semibold">보관한 분야</h2>
          <div className="flex flex-col">
            {archivedHobbies.map((hobby) => (
              <HobbyRow key={hobby.id} hobby={hobby} onError={setErrorMessage} />
            ))}
          </div>
        </div>
      ) : null}
    </>
  )

  const addForm = (
    <form className="flex flex-col gap-3" onSubmit={handleCreate}>
      <h2 className="font-serif text-lg font-semibold">분야 추가</h2>
      <Field label="분야 이름" htmlFor="new-hobby-name">
        <Input
          id="new-hobby-name"
          value={newName}
          onChange={(event) => setNewName(event.target.value)}
          placeholder="예: 드로잉"
        />
      </Field>
      <Button type="submit" disabled={createHobby.isPending} className="self-start">
        추가하기
      </Button>
      <p className="text-[13px] text-ink-2">
        분야를 보관해도 쓴 기록은 남아 있어요. 언제든 다시 꺼낼 수 있어요.
      </p>
    </form>
  )

  return (
    <div className="flex flex-col gap-4 lg:gap-5">
      <nav aria-label="현재 위치" className="hidden items-center gap-1.5 text-[13px] text-ink-3 lg:flex">
        <span>설정</span>
        <span aria-hidden="true">›</span>
        <span>활동 분야 관리</span>
      </nav>
      <h1 className="font-serif text-xl font-semibold lg:text-[30px]">활동 분야 관리</h1>

      {errorMessage ? <Note variant="sand">{errorMessage}</Note> : null}

      <div className="flex flex-col gap-6 lg:grid lg:grid-cols-[7fr_4fr] lg:items-start lg:gap-20">
        <div className="flex flex-col gap-6">{listSection}</div>
        {addForm}
      </div>
    </div>
  )
}
