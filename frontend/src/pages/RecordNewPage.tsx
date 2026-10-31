import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Button } from '../components/Button'
import { EmptyState } from '../components/EmptyState'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
import { RecordFormFields } from '../components/RecordFormFields'
import { RecordSavedPanel } from '../components/RecordSavedPanel'
import { PencilIcon, RefreshIcon } from '../components/icons'
import { useCurrentUser } from '../hooks/useCurrentUser'
import { useHobbies } from '../hooks/useHobbies'
import { ApiError } from '../lib/api'
import { todayLocalDate, yesterdayLocalDate } from '../lib/date'
import type { DateMode } from '../lib/recordForm'
import {
  clearRecordDraft,
  isRecordDraftEmpty,
  loadRecordDraft,
  saveRecordDraft,
  type RecordDraft,
} from '../lib/recordDraft'
import { createRecord, fetchRecords, type Feeling, type RecordItem } from '../lib/records'

const DEFAULT_ERROR_MESSAGE = '저장하지 못했어요. 쓴 글은 이 기기에 남아 있어요.'
const DRAFT_SAVE_DELAY_MS = 500

export function RecordNewPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: currentUser, isLoading: userLoading } = useCurrentUser()
  const { data: hobbies, isLoading: hobbiesLoading } = useHobbies(false)
  const recentQuery = useQuery({ queryKey: ['records', 'recent'], queryFn: () => fetchRecords({ size: 1 }) })

  const activeHobbies = hobbies ?? []

  const [hobbyId, setHobbyId] = useState<number | null>(null)
  const [hobbyPickerOpen, setHobbyPickerOpen] = useState(false)
  const [content, setContent] = useState('')
  const [moreOpen, setMoreOpen] = useState(false)
  const [dateMode, setDateMode] = useState<DateMode>('today')
  const [customDate, setCustomDate] = useState(todayLocalDate())
  const [minutes, setMinutes] = useState<number | null>(null)
  const [difficulty, setDifficulty] = useState('')
  const [feeling, setFeeling] = useState<Feeling | null>(null)
  const [draftRestored, setDraftRestored] = useState(false)

  const [submitting, setSubmitting] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [savedRecord, setSavedRecord] = useState<RecordItem | null>(null)
  const [savedWasFirst, setSavedWasFirst] = useState(false)

  const initializedRef = useRef(false)

  useEffect(() => {
    if (initializedRef.current) return
    if (hobbiesLoading || recentQuery.isLoading || userLoading) return
    initializedRef.current = true

    const draft = currentUser ? loadRecordDraft(currentUser.id) : null
    const activeIds = new Set(activeHobbies.map((hobby) => hobby.id))
    const recentHobbyId = recentQuery.data?.items[0]?.hobbyId ?? null

    const defaultHobbyId =
      draft?.hobbyId != null && activeIds.has(draft.hobbyId)
        ? draft.hobbyId
        : recentHobbyId != null && activeIds.has(recentHobbyId)
          ? recentHobbyId
          : (activeHobbies[0]?.id ?? null)

    setHobbyId(defaultHobbyId)
    if (draft) {
      setContent(draft.content)
      setMinutes(draft.minutes)
      setDifficulty(draft.difficulty)
      setFeeling(draft.feeling)
      if (!isRecordDraftEmpty(draft)) {
        setDraftRestored(true)
        setMoreOpen(draft.minutes != null || draft.difficulty !== '' || draft.feeling != null)
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hobbiesLoading, recentQuery.isLoading, userLoading])

  useEffect(() => {
    if (!initializedRef.current || !currentUser) return
    const draft: RecordDraft = { hobbyId, content, minutes, difficulty, feeling }
    const timer = setTimeout(() => {
      if (isRecordDraftEmpty(draft)) {
        clearRecordDraft(currentUser.id)
      } else {
        saveRecordDraft(currentUser.id, draft)
      }
    }, DRAFT_SAVE_DELAY_MS)
    return () => clearTimeout(timer)
  }, [hobbyId, content, minutes, difficulty, feeling, currentUser])

  if (userLoading || hobbiesLoading || recentQuery.isLoading) {
    return <Loading />
  }

  if (activeHobbies.length === 0) {
    return (
      <EmptyState
        title="아직 활동 분야가 없어요"
        description="기록을 쓰려면 먼저 활동 분야를 하나 만들어 주세요."
        action={
          <Link
            to="/settings/hobbies"
            className="inline-flex min-h-11 items-center justify-center rounded-lg bg-base px-5 text-[15px] font-medium text-white no-underline"
          >
            활동 분야 관리로 가기
          </Link>
        }
      />
    )
  }

  const selectedHobby = activeHobbies.find((hobby) => hobby.id === hobbyId) ?? activeHobbies[0]
  const today = todayLocalDate()
  const recordDate = dateMode === 'today' ? today : dateMode === 'yesterday' ? yesterdayLocalDate() : customDate

  if (savedRecord) {
    return (
      <RecordSavedPanel
        record={savedRecord}
        hobbyName={activeHobbies.find((hobby) => hobby.id === savedRecord.hobbyId)?.name ?? selectedHobby.name}
        isFirstRecord={savedWasFirst}
        onHome={() => navigate('/')}
        onViewDetail={() => navigate(`/records/${savedRecord.id}`)}
        onEdit={() => navigate(`/records/${savedRecord.id}/edit`)}
      />
    )
  }

  async function handleSave() {
    if (submitting || !selectedHobby) return
    const trimmedContent = content.trim()
    if (!trimmedContent) {
      setSaveError('한 일을 입력해 주세요')
      return
    }
    setSaveError(null)
    setSubmitting(true)
    const wasFirst = (recentQuery.data?.totalCount ?? 0) === 0
    try {
      const created = await createRecord({
        hobbyId: selectedHobby.id,
        recordDate,
        content: trimmedContent,
        minutes: minutes ?? undefined,
        difficulty: difficulty.trim() || undefined,
        feeling: feeling ?? undefined,
      })
      if (currentUser) clearRecordDraft(currentUser.id)
      queryClient.invalidateQueries({ queryKey: ['records'] })
      queryClient.invalidateQueries({ queryKey: ['hobbies'] })
      setSavedRecord(created)
      setSavedWasFirst(wasFirst)
    } catch (error) {
      setSaveError(error instanceof ApiError ? error.message : DEFAULT_ERROR_MESSAGE)
    } finally {
      setSubmitting(false)
    }
  }

  function toggleMinutes(preset: number) {
    setMinutes((prev) => (prev === preset ? null : preset))
  }

  function handleMinutesInput(value: string) {
    if (value === '') {
      setMinutes(null)
      return
    }
    const parsed = Number(value)
    if (Number.isNaN(parsed)) return
    setMinutes(Math.max(0, Math.min(1440, Math.round(parsed))))
  }

  function toggleFeeling(value: Feeling) {
    setFeeling((prev) => (prev === value ? null : value))
  }

  return (
    <div className="flex flex-col gap-4 lg:max-w-[760px] lg:gap-4">
      <h1 className="font-serif text-xl font-semibold lg:text-[30px]">오늘의 기록</h1>

      <RecordFormFields
        activeHobbies={activeHobbies}
        hobbyId={selectedHobby.id}
        hobbyPickerOpen={hobbyPickerOpen}
        onToggleHobbyPicker={() => setHobbyPickerOpen((open) => !open)}
        onHobbyChange={(id) => {
          setHobbyId(id)
          setHobbyPickerOpen(false)
        }}
        content={content}
        onContentChange={setContent}
        moreOpen={moreOpen}
        onToggleMore={() => setMoreOpen((open) => !open)}
        dateMode={dateMode}
        onDateModeChange={setDateMode}
        customDate={customDate}
        onCustomDateChange={setCustomDate}
        today={today}
        minutes={minutes}
        onToggleMinutes={toggleMinutes}
        onMinutesInput={handleMinutesInput}
        difficulty={difficulty}
        onDifficultyChange={setDifficulty}
        feeling={feeling}
        onToggleFeeling={toggleFeeling}
        banner={
          draftRestored ? (
            <div className="flex w-fit items-center gap-2 rounded-[10px] bg-[color-mix(in_srgb,var(--color-base)_9%,white)] px-3 py-2 text-sm text-ink">
              <PencilIcon width={16} height={16} />
              쓰던 글이 남아 있어요
            </div>
          ) : null
        }
      />

      {saveError ? (
        <div className="flex flex-col gap-3">
          <Note variant="sand">{saveError}</Note>
          <div className="flex flex-col items-start gap-1">
            <Button type="button" block disabled={submitting} onClick={handleSave} className="lg:w-auto lg:self-start">
              <RefreshIcon width={16} height={16} />
              다시 저장
            </Button>
            <button
              type="button"
              onClick={() => setSaveError(null)}
              className="mx-auto inline-flex min-h-11 items-center text-sm font-medium text-base-ink underline underline-offset-4"
            >
              나중에 저장
            </button>
          </div>
        </div>
      ) : (
        <Button type="button" block disabled={submitting} onClick={handleSave} className="lg:w-auto lg:self-start">
          {moreOpen ? '저장하기' : '한 줄만 저장하기'}
        </Button>
      )}
    </div>
  )
}
