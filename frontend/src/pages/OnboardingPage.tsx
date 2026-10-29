import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { Button } from '../components/Button'
import { Field } from '../components/Field'
import { Input } from '../components/Input'
import { LinkButton } from '../components/LinkButton'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
import { StepDots } from '../components/StepDots'
import { ApiError } from '../lib/api'
import { createHobby, fetchHobbies, updateHobby } from '../lib/hobbies'
import { updateDisplayName } from '../lib/users'
import { useCurrentUser } from '../hooks/useCurrentUser'

const TOTAL_STEPS = 3
const SUGGESTED_HOBBIES = ['기타', '러닝', '코딩', '피아노', '그림', '요가', '영어 회화']
const GOAL_MAX_LENGTH = 200

type CreatedHobby = { id: number; name: string }

export function OnboardingPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: currentUser, isLoading: isUserLoading } = useCurrentUser()

  const [step, setStep] = useState(1)
  const [submitting, setSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const [name, setName] = useState('')
  const [nameInitialized, setNameInitialized] = useState(false)
  if (!nameInitialized && currentUser) {
    setName(currentUser.displayName ?? '')
    setNameInitialized(true)
  }

  const [selectedHobbies, setSelectedHobbies] = useState<string[]>([])
  const [customHobby, setCustomHobby] = useState('')

  const [createdHobbies, setCreatedHobbies] = useState<CreatedHobby[]>([])
  const [goalDrafts, setGoalDrafts] = useState<Record<number, string>>({})
  const [openGoalId, setOpenGoalId] = useState<number | null>(null)

  function finish() {
    queryClient.invalidateQueries({ queryKey: ['hobbies'] })
    navigate('/', { replace: true })
  }

  function toggleHobby(hobbyName: string) {
    setSelectedHobbies((prev) =>
      prev.includes(hobbyName) ? prev.filter((item) => item !== hobbyName) : [...prev, hobbyName],
    )
  }

  function addCustomHobby() {
    const trimmed = customHobby.trim()
    if (!trimmed) return
    if (!selectedHobbies.includes(trimmed)) {
      setSelectedHobbies((prev) => [...prev, trimmed])
    }
    setCustomHobby('')
  }

  async function handleNameSubmit(event: FormEvent) {
    event.preventDefault()
    const trimmed = name.trim()
    if (!trimmed) {
      setErrorMessage('이름을 입력해 주세요')
      return
    }
    setErrorMessage(null)
    setSubmitting(true)
    try {
      await updateDisplayName(trimmed)
      setStep(2)
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요')
    } finally {
      setSubmitting(false)
    }
  }

  async function handleHobbiesSubmit() {
    setErrorMessage(null)
    if (selectedHobbies.length === 0) {
      finish()
      return
    }
    setSubmitting(true)
    try {
      for (const hobbyName of selectedHobbies) {
        try {
          await createHobby(hobbyName)
        } catch (error) {
          if (!(error instanceof ApiError && error.code === 'HOBBY_NAME_TAKEN')) {
            throw error
          }
        }
      }
      const hobbies = await fetchHobbies()
      const created = hobbies
        .filter((hobby) => selectedHobbies.includes(hobby.name))
        .map((hobby) => ({ id: hobby.id, name: hobby.name }))

      if (created.length === 0) {
        finish()
        return
      }
      setCreatedHobbies(created)
      setStep(3)
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요')
    } finally {
      setSubmitting(false)
    }
  }

  async function handleGoalsSubmit() {
    setErrorMessage(null)
    setSubmitting(true)
    try {
      for (const hobby of createdHobbies) {
        const goal = goalDrafts[hobby.id]?.trim()
        if (goal) {
          await updateHobby(hobby.id, { goal })
        }
      }
      finish()
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="flex min-h-dvh flex-col bg-paper px-5 pb-8 pt-4">
      <div className="mx-auto flex w-full max-w-[420px] flex-1 flex-col gap-5">
        <div className="flex items-center justify-between">
          <StepDots step={step} total={TOTAL_STEPS} />
          <LinkButton to="/" onClick={(event) => { event.preventDefault(); finish() }}>
            건너뛰기
          </LinkButton>
        </div>

        {errorMessage ? <Note variant="sand">{errorMessage}</Note> : null}

        {step === 1 ? (
          isUserLoading ? (
            <Loading />
          ) : (
            <form className="flex flex-col gap-5" onSubmit={handleNameSubmit}>
              <div className="flex flex-col gap-1.5">
                <h1 className="font-serif text-xl font-semibold leading-[1.45]">뭐라고 부르면 좋을까요?</h1>
                <p className="text-sm text-ink-2">홈 인사말에 쓰여요</p>
              </div>
              <Field label="이름" htmlFor="display-name">
                <Input
                  id="display-name"
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  maxLength={30}
                  autoFocus
                />
              </Field>
              <Button type="submit" block disabled={submitting}>
                다음
              </Button>
            </form>
          )
        ) : null}

        {step === 2 ? (
          <div className="flex flex-col gap-5">
            <h1 className="font-serif text-xl font-semibold leading-[1.45]">키우고 싶은 활동을 골라 주세요</h1>
            <div className="flex flex-wrap gap-2">
              {Array.from(new Set([...SUGGESTED_HOBBIES, ...selectedHobbies])).map((hobbyName) => (
                <button
                  key={hobbyName}
                  type="button"
                  aria-pressed={selectedHobbies.includes(hobbyName)}
                  onClick={() => toggleHobby(hobbyName)}
                  className={[
                    'inline-flex min-h-11 items-center justify-center gap-1.5 rounded-lg border px-4 text-sm font-medium',
                    selectedHobbies.includes(hobbyName)
                      ? 'border-base bg-[color-mix(in_srgb,var(--color-base)_12%,white)] text-ink'
                      : 'border-line-2 bg-white text-ink',
                  ].join(' ')}
                >
                  {hobbyName}
                </button>
              ))}
            </div>
            <div className="flex gap-2">
              <Input
                value={customHobby}
                onChange={(event) => setCustomHobby(event.target.value)}
                placeholder="직접 적기 · 예: 수영"
                aria-label="직접 적기"
                onKeyDown={(event) => {
                  if (event.key === 'Enter') {
                    event.preventDefault()
                    addCustomHobby()
                  }
                }}
              />
              <Button type="button" variant="secondary" onClick={addCustomHobby}>
                추가
              </Button>
            </div>
            <Button type="button" block disabled={submitting} onClick={handleHobbiesSubmit}>
              다음
            </Button>
          </div>
        ) : null}

        {step === 3 ? (
          <div className="flex flex-col gap-5">
            <h1 className="font-serif text-xl font-semibold leading-[1.45]">목표와 수준은 나중에 적어도 돼요</h1>
            <div className="flex flex-col">
              {createdHobbies.map((hobby) => (
                <div key={hobby.id} className="flex flex-col gap-2 border-t border-line py-3 first:border-t-0">
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-ink">{hobby.name}</span>
                    {openGoalId !== hobby.id ? (
                      <button
                        type="button"
                        onClick={() => setOpenGoalId(hobby.id)}
                        className="inline-flex min-h-11 items-center gap-1 border-0 bg-transparent p-0 text-sm text-base-ink underline underline-offset-4"
                      >
                        지금 적어 보기
                      </button>
                    ) : null}
                  </div>
                  {openGoalId === hobby.id ? (
                    <Input
                      value={goalDrafts[hobby.id] ?? ''}
                      onChange={(event) =>
                        setGoalDrafts((prev) => ({ ...prev, [hobby.id]: event.target.value }))
                      }
                      maxLength={GOAL_MAX_LENGTH}
                      placeholder="예: 코드 전환 익숙해지기"
                      autoFocus
                    />
                  ) : null}
                </div>
              ))}
            </div>
            <Button type="button" block disabled={submitting} onClick={handleGoalsSubmit}>
              다음
            </Button>
          </div>
        ) : null}
      </div>
    </main>
  )
}
