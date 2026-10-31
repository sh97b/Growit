import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Button } from '../components/Button'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
import { RecordFormFields } from '../components/RecordFormFields'
import { useHobbies, type Hobby } from '../hooks/useHobbies'
import { useRecord, useUpdateRecord } from '../hooks/useRecords'
import { ApiError } from '../lib/api'
import { todayLocalDate, yesterdayLocalDate } from '../lib/date'
import type { DateMode } from '../lib/recordForm'
import type { Feeling, RecordDetail, RecordUpdatePayload } from '../lib/records'

const DEFAULT_ERROR_MESSAGE = '고치지 못했어요. 잠시 후 다시 시도해 주세요.'

export function RecordEditPage() {
  const { id } = useParams()
  const recordId = Number(id)
  const { data: record, isLoading: recordLoading, isError } = useRecord(recordId)
  const { data: activeHobbies, isLoading: hobbiesLoading } = useHobbies(false)

  if (recordLoading || hobbiesLoading) {
    return <Loading />
  }

  if (isError || !record) {
    return <p className="py-10 text-center text-sm text-ink-2">기록을 찾을 수 없어요.</p>
  }

  return <RecordEditForm key={record.id} record={record} activeHobbies={activeHobbies ?? []} />
}

function RecordEditForm({ record, activeHobbies }: { record: RecordDetail; activeHobbies: Hobby[] }) {
  const navigate = useNavigate()
  const updateRecord = useUpdateRecord()

  const [hobbyId, setHobbyId] = useState(record.hobbyId)
  const [hobbyPickerOpen, setHobbyPickerOpen] = useState(false)
  const [content, setContent] = useState(record.content)
  const [moreOpen, setMoreOpen] = useState(false)
  const [dateMode, setDateMode] = useState<DateMode>('custom')
  const [customDate, setCustomDate] = useState(record.recordDate)
  const [minutes, setMinutes] = useState<number | null>(record.minutes)
  const [difficulty, setDifficulty] = useState(record.difficulty ?? '')
  const [feeling, setFeeling] = useState<Feeling | null>(record.feeling)
  const [contentError, setContentError] = useState<string | null>(null)
  const [saveError, setSaveError] = useState<string | null>(null)

  const includesCurrentHobby = activeHobbies.some((hobby) => hobby.id === hobbyId)
  const pickerHobbies = includesCurrentHobby
    ? activeHobbies
    : [...activeHobbies, { id: record.hobbyId, name: '(보관됨)', goal: null, recordCount: 0, archived: true }]
  const today = todayLocalDate()
  const recordDate = dateMode === 'today' ? today : dateMode === 'yesterday' ? yesterdayLocalDate() : customDate

  const changes: RecordUpdatePayload = {}
  if (hobbyId !== record.hobbyId) changes.hobbyId = hobbyId
  if (recordDate !== record.recordDate) changes.recordDate = recordDate
  const trimmedContent = content.trim()
  if (trimmedContent !== record.content) changes.content = trimmedContent
  if (minutes !== record.minutes) changes.minutes = minutes
  const trimmedDifficulty = difficulty.trim() || null
  if (trimmedDifficulty !== (record.difficulty ?? null)) changes.difficulty = trimmedDifficulty
  if (feeling !== record.feeling) changes.feeling = feeling
  const hasChanges = Object.keys(changes).length > 0

  async function handleSave() {
    if (!content.trim()) {
      setContentError('한 일을 입력해 주세요')
      return
    }
    setContentError(null)
    setSaveError(null)
    try {
      await updateRecord.mutateAsync({ id: record.id, payload: changes })
      navigate(`/records/${record.id}`, { replace: true })
    } catch (error) {
      setSaveError(error instanceof ApiError ? error.message : DEFAULT_ERROR_MESSAGE)
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
    <div className="flex flex-col gap-4 lg:max-w-[760px]">
      <h1 className="font-serif text-xl font-semibold lg:text-[30px]">기록 수정</h1>

      <RecordFormFields
        activeHobbies={pickerHobbies}
        hobbyId={hobbyId}
        hobbyPickerOpen={hobbyPickerOpen}
        onToggleHobbyPicker={() => setHobbyPickerOpen((open) => !open)}
        onHobbyChange={(nextId) => {
          setHobbyId(nextId)
          setHobbyPickerOpen(false)
        }}
        content={content}
        onContentChange={(value) => {
          setContent(value)
          if (contentError) setContentError(null)
        }}
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
      />

      {contentError ? <Note variant="sand">{contentError}</Note> : null}
      {saveError ? <Note variant="sand">{saveError}</Note> : null}

      <div className="flex gap-3">
        <Button
          type="button"
          disabled={!hasChanges || updateRecord.isPending}
          onClick={handleSave}
          className="flex-1 lg:flex-none"
        >
          저장
        </Button>
        <Button
          type="button"
          variant="secondary"
          onClick={() => navigate(`/records/${record.id}`)}
          className="flex-1 lg:flex-none"
        >
          취소
        </Button>
      </div>
    </div>
  )
}
