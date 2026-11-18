import { useRef, type ReactNode } from 'react'
import { Field } from './Field'
import { HobbyInlineCreate } from './HobbyInlineCreate'
import { Input } from './Input'
import { VoiceInputButton } from './VoiceInputButton'
import { ChevronDownIcon } from './icons'
import { useVoiceInput } from '../hooks/useVoiceInput'
import { FEELINGS, MINUTE_PRESETS, pickClass } from '../lib/recordForm'
import type { DateMode } from '../lib/recordForm'
import type { Feeling } from '../lib/records'
import { CustomFeelingChips } from './CustomFeelingChips'

type Hobby = { id: number; name: string }

export function RecordFormFields({
  activeHobbies,
  hobbyId,
  hobbyPickerOpen,
  onToggleHobbyPicker,
  onHobbyChange,
  content,
  onContentChange,
  moreOpen,
  onToggleMore,
  dateMode,
  onDateModeChange,
  customDate,
  onCustomDateChange,
  today,
  minutes,
  onToggleMinutes,
  onMinutesInput,
  difficulty,
  onDifficultyChange,
  feeling,
  onToggleFeeling,
  customFeelingId,
  onSelectCustomFeeling,
  banner,
  suggestionChip,
  submitting = false,
}: {
  activeHobbies: Hobby[]
  hobbyId: number
  hobbyPickerOpen: boolean
  onToggleHobbyPicker: () => void
  onHobbyChange: (id: number) => void
  content: string
  onContentChange: (value: string) => void
  moreOpen: boolean
  onToggleMore: () => void
  dateMode: DateMode
  onDateModeChange: (mode: DateMode) => void
  customDate: string
  onCustomDateChange: (value: string) => void
  today: string
  minutes: number | null
  onToggleMinutes: (preset: number) => void
  onMinutesInput: (value: string) => void
  difficulty: string
  onDifficultyChange: (value: string) => void
  feeling: Feeling | null
  onToggleFeeling: (value: Feeling) => void
  customFeelingId: number | null
  onSelectCustomFeeling: (id: number | null) => void
  banner?: ReactNode
  suggestionChip?: ReactNode
  /** 저장 중에는 입력(마이크 포함)을 받지 않는다(O1 F). */
  submitting?: boolean
}) {
  const selectedHobby = activeHobbies.find((hobby) => hobby.id === hobbyId) ?? activeHobbies[0]
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const contentRef = useRef(content)
  contentRef.current = content

  // O1(F): 확정된 말은 커서 위치(없으면 끝)에 공백과 함께 이어 붙인다. 이미 입력된 글과 사용자가
  // 직접 고친 글은 지우지 않는다(textarea의 선택 영역만 다루고 나머지 값은 그대로 둔다).
  function insertAtCursor(text: string) {
    const el = textareaRef.current
    const base = contentRef.current
    const start = el?.selectionStart ?? base.length
    const end = el?.selectionEnd ?? base.length
    const before = base.slice(0, start)
    const after = base.slice(end)
    const needsLeadingSpace = before.length > 0 && !/\s$/.test(before)
    const insertion = `${needsLeadingSpace ? ' ' : ''}${text}`
    onContentChange(`${before}${insertion}${after}`)
    if (el) {
      const nextPos = before.length + insertion.length
      requestAnimationFrame(() => {
        el.setSelectionRange(nextPos, nextPos)
        el.focus()
      })
    }
  }

  const voice = useVoiceInput({ onFinalResult: insertAtCursor, disabled: submitting })

  return (
    <>
      {banner}

      <div className="relative">
        <textarea
          ref={textareaRef}
          value={content}
          onChange={(event) => onContentChange(event.target.value)}
          disabled={submitting}
          placeholder="오늘은 무엇을 했나요?"
          aria-label="오늘 한 일을 한 줄로 적어 보세요"
          className="h-[9.375rem] w-full resize-none rounded-xl border border-input bg-white p-4 pb-14 font-serif text-lg leading-[1.85] text-ink outline-none focus:border-base focus:shadow-[0_0_0_1px_var(--color-base)] disabled:bg-surface lg:h-[9.375rem]"
        />
        {voice.supported ? (
          <VoiceInputButton
            listening={voice.listening}
            interimText={voice.interimText}
            denied={voice.denied}
            gaveUp={voice.gaveUp}
            onToggle={() => (voice.listening ? voice.stop() : voice.start())}
          />
        ) : null}
      </div>

      {suggestionChip}

      <div className="relative flex flex-wrap items-center gap-2">
        <button
          type="button"
          aria-pressed="true"
          aria-expanded={hobbyPickerOpen}
          aria-label={`분야 변경: ${selectedHobby.name}`}
          onClick={onToggleHobbyPicker}
          className={pickClass(true)}
        >
          {selectedHobby.name}
          <ChevronDownIcon width={16} height={16} className={hobbyPickerOpen ? 'rotate-180' : ''} />
        </button>
        {hobbyPickerOpen
          ? activeHobbies
              .filter((hobby) => hobby.id !== selectedHobby.id)
              .map((hobby) => (
                <button
                  key={hobby.id}
                  type="button"
                  aria-pressed={false}
                  onClick={() => onHobbyChange(hobby.id)}
                  className={pickClass(false)}
                >
                  {hobby.name}
                </button>
              ))
          : null}
      </div>

      {hobbyPickerOpen ? (
        <HobbyInlineCreate onCreated={(hobby) => onHobbyChange(hobby.id)} />
      ) : null}

      <button
        type="button"
        onClick={onToggleMore}
        className="inline-flex min-h-11 w-fit items-center gap-1 border-0 bg-transparent p-0 text-sm font-medium text-base-ink"
      >
        <ChevronDownIcon width={14} height={14} className={moreOpen ? '' : 'rotate-[-90deg]'} />
        {moreOpen ? '더 적기 접기' : '더 적기'}
      </button>

      {moreOpen ? (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2 lg:gap-x-8 lg:gap-y-4">
          <div className="flex flex-col gap-2">
            <span className="text-sm font-medium">날짜</span>
            <div className="inline-flex w-fit gap-0.5 rounded-lg border border-line-2 bg-white p-[0.1875rem]">
              {(['today', 'yesterday', 'custom'] as DateMode[]).map((mode) => (
                <button
                  key={mode}
                  type="button"
                  aria-pressed={dateMode === mode}
                  onClick={() => onDateModeChange(mode)}
                  className={[
                    'min-h-10 rounded-md px-3.5 text-sm font-medium whitespace-nowrap',
                    dateMode === mode ? 'bg-ink text-white' : 'bg-transparent text-ink-2',
                  ].join(' ')}
                >
                  {mode === 'today' ? '오늘' : mode === 'yesterday' ? '어제' : '직접'}
                </button>
              ))}
            </div>
            {dateMode === 'custom' ? (
              <input
                type="date"
                value={customDate}
                max={today}
                onChange={(event) => onCustomDateChange(event.target.value)}
                className="h-12 w-full rounded-lg border border-input bg-white px-3.5 text-body-min text-ink outline-none"
              />
            ) : null}
          </div>

          <div className="flex flex-col gap-2">
            <span className="text-sm font-medium">시간</span>
            <div className="flex flex-wrap gap-1.5">
              {MINUTE_PRESETS.map((preset) => (
                <button
                  key={preset}
                  type="button"
                  aria-pressed={minutes === preset}
                  onClick={() => onToggleMinutes(preset)}
                  className={pickClass(minutes === preset)}
                >
                  {preset}분
                </button>
              ))}
            </div>
            <Input
              type="number"
              inputMode="numeric"
              min={0}
              max={1440}
              value={minutes ?? ''}
              onChange={(event) => onMinutesInput(event.target.value)}
              placeholder="직접 입력(분)"
              aria-label="연습 시간(분), 0에서 1440 사이"
            />
          </div>

          <Field label="막힌 점이나 나아진 점" htmlFor="difficulty">
            <Input
              id="difficulty"
              value={difficulty}
              onChange={(event) => onDifficultyChange(event.target.value)}
              placeholder="있다면 한 줄로"
            />
          </Field>

          <div className="flex flex-col gap-2">
            <span className="text-sm font-medium">느낌</span>
            <div className="flex flex-wrap gap-1.5">
              {FEELINGS.map(({ value, label }) => (
                <button
                  key={value}
                  type="button"
                  aria-pressed={feeling === value}
                  onClick={() => onToggleFeeling(value)}
                  className={[
                    'inline-flex min-h-11 items-center justify-center rounded-full bg-tag-feel-bg px-3 text-sm font-medium text-tag-feel-fg',
                    feeling === value ? 'shadow-[inset_0_0_0_2px_var(--color-tag-feel-fg)]' : '',
                  ].join(' ')}
                >
                  {label}
                </button>
              ))}
              <CustomFeelingChips selectedId={customFeelingId} onSelect={onSelectCustomFeeling} />
            </div>
          </div>
        </div>
      ) : null}
    </>
  )
}
