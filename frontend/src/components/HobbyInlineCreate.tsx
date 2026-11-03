import { useState } from 'react'
import { Button } from './Button'
import { Input } from './Input'
import { Note } from './Note'
import { useCreateHobby } from '../hooks/useHobbies'
import { ApiError } from '../lib/api'
import type { Hobby } from '../lib/hobbies'

const HOBBY_NAME_MAX_LENGTH = 50

/** 기록 작성·수정의 "분야 변경" 펼침과 분야가 하나도 없을 때 공통으로 쓰는 새 분야 만들기 입력. */
export function HobbyInlineCreate({
  onCreated,
  autoFocus = false,
}: {
  onCreated: (hobby: Hobby) => void
  autoFocus?: boolean
}) {
  const createHobby = useCreateHobby()
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)

  function handleCreate() {
    const trimmed = name.trim()
    if (!trimmed) return
    setError(null)
    createHobby.mutate(
      { name: trimmed },
      {
        onSuccess: (hobby) => {
          setName('')
          onCreated(hobby)
        },
        onError: (mutationError) =>
          setError(mutationError instanceof ApiError ? mutationError.message : '만들지 못했어요'),
      },
    )
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="flex gap-2">
        <Input
          value={name}
          onChange={(event) => setName(event.target.value)}
          placeholder="새 분야 추가"
          aria-label="새 분야 추가"
          maxLength={HOBBY_NAME_MAX_LENGTH}
          autoFocus={autoFocus}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              event.preventDefault()
              handleCreate()
            }
          }}
        />
        <Button type="button" variant="secondary" disabled={createHobby.isPending} onClick={handleCreate}>
          추가
        </Button>
      </div>
      {error ? <Note variant="sand">{error}</Note> : null}
    </div>
  )
}
