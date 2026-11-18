import { useEffect, useMemo, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useDotPresets, useDotPreviews, useStartDot } from '../hooks/useDots'
import { ApiError } from '../lib/api'
import { MAX_GOAL_DAYS, MIN_GOAL_DAYS, type DotPreview } from '../lib/dots'
import { fetchSummary } from '../lib/stats'
import { Button } from './Button'
import { Dialog } from './Dialog'
import { DotPicture } from './DotPicture'
import { Field } from './Field'
import { XIcon } from './icons'
import { Input } from './Input'
import { Note } from './Note'

const GOAL_DAY_CHIPS = [14, 30, 50, 100, 150, 200]
/** 이 일수 이하의 짧은 목표에서는 칸이 적어도 모양이 잘 남는 그림을 먼저 보여 준다. */
const SHORT_GOAL_MAX_DAYS = 30
const RECOMMENDED_FOR_SHORT_GOALS = ['dumbbell', 'sprout-pot']
const STEP_TITLES = ['도트 그림으로 기록을 쌓아 볼까요?', '며칠 기록하면 완성할까요?', '어떤 그림으로 채울까요?', '이대로 시작할까요?']
const TITLE_ID = 'dot-start-title'
const LARGE_PREVIEW_HEIGHT_PX = 210
const THUMB_BOX_PX = 84

/** 숫자가 아니거나 범위 밖이면 value는 null이고 차분한 안내 문구를 함께 돌려준다. 비어 있으면 안내 없이 null이다. */
function parseGoalDays(text: string): { value: number | null; message: string | null } {
  const trimmed = text.trim()
  if (trimmed === '') return { value: null, message: null }
  if (!/^\d+$/.test(trimmed)) return { value: null, message: '숫자만 입력해 주세요' }
  const value = Number(trimmed)
  if (value < MIN_GOAL_DAYS || value > MAX_GOAL_DAYS) {
    return { value: null, message: `${MIN_GOAL_DAYS}일부터 ${MAX_GOAL_DAYS}일까지 정할 수 있어요` }
  }
  return { value, message: null }
}

function startErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'DOT_GOAL_ACTIVE') return '이미 진행 중인 도트 그림이 있어요. 창을 닫고 확인해 주세요.'
    if (error.status >= 400 && error.status < 500) return error.message
  }
  return '지금은 시작하지 못했어요. 인터넷 연결을 확인하고 잠시 후 다시 시도해 주세요.'
}

function PreviewSkeleton({ size }: { size: number }) {
  return (
    <div
      aria-hidden="true"
      className="rounded-xl bg-rest"
      style={{ width: size, height: size, animation: 'growit-pulse 1.4s ease-in-out infinite' }}
    />
  )
}

function PreviewPicture({
  preview,
  failed,
  box,
  maxHeight,
}: {
  preview: DotPreview | undefined
  failed: boolean
  box?: number
  maxHeight?: number
}) {
  if (preview) {
    return (
      <DotPicture
        width={preview.width}
        height={preview.height}
        palette={preview.palette}
        cells={preview.cells}
        filledCount={preview.cells.length}
        box={box}
        maxHeight={maxHeight}
        ariaLabel={`${preview.label} ${preview.goalDays}칸 도트 그림 미리보기`}
      />
    )
  }
  if (failed) {
    return <p className="text-caption-min text-ink-3">미리보기를 불러오지 못했어요</p>
  }
  return <PreviewSkeleton size={box ?? LARGE_PREVIEW_HEIGHT_PX - 20} />
}

/** 도트 그림 시작 흐름: 안내 → 목표 일수 → 그림 고르기(미리보기) → 확인과 시작. 홈 카드 위에 모달로 열린다. */
export function DotStartFlow({ onClose }: { onClose: () => void }) {
  const [step, setStep] = useState(1)
  const [daysText, setDaysText] = useState('30')
  const [pickedKey, setPickedKey] = useState<string | null>(null)
  const headingRef = useRef<HTMLHeadingElement>(null)
  // 같은 순간에 두 번 눌려도 시작 요청이 한 번만 나가게 한다(상태 갱신보다 빠른 연속 클릭 방지).
  const startingRef = useRef(false)

  const parsed = parseGoalDays(daysText)
  const goalDays = parsed.value
  const presetsQuery = useDotPresets(true)
  const start = useStartDot()
  const summaryQuery = useQuery({ queryKey: ['stats', 'summary'], queryFn: fetchSummary })

  const orderedPresets = useMemo(() => {
    const presets = presetsQuery.data ?? []
    if (goalDays == null || goalDays > SHORT_GOAL_MAX_DAYS) return presets
    const rank = (key: string) => {
      const index = RECOMMENDED_FOR_SHORT_GOALS.indexOf(key)
      return index === -1 ? RECOMMENDED_FOR_SHORT_GOALS.length : index
    }
    return [...presets].sort((a, b) => rank(a.key) - rank(b.key))
  }, [presetsQuery.data, goalDays])

  const previewQueries = useDotPreviews(
    orderedPresets.map((preset) => preset.key),
    goalDays,
    step >= 3,
  )
  const previewOf = (key: string) => {
    const index = orderedPresets.findIndex((preset) => preset.key === key)
    return index === -1 ? undefined : previewQueries[index]
  }

  const selectedKey = pickedKey ?? orderedPresets[0]?.key ?? null
  const selectedPreset = orderedPresets.find((preset) => preset.key === selectedKey)
  const selectedQuery = selectedKey ? previewOf(selectedKey) : undefined
  const showRecommended = goalDays != null && goalDays <= SHORT_GOAL_MAX_DAYS

  useEffect(() => {
    headingRef.current?.focus()
  }, [step])

  const handleStart = () => {
    if (startingRef.current || start.isPending || goalDays == null || !selectedKey) return
    startingRef.current = true
    start.mutate(
      { presetKey: selectedKey, goalDays },
      {
        onSuccess: () => onClose(),
        onSettled: () => {
          startingRef.current = false
        },
      },
    )
  }

  const goBack = () => {
    start.reset()
    setStep((current) => current - 1)
  }

  return (
    <Dialog onClose={onClose} labelledBy={TITLE_ID}>
      <div className="flex items-start justify-between gap-3">
        <div className="flex flex-col gap-1">
          <p className="text-caption-min text-ink-3">{step}/4단계</p>
          <h2 id={TITLE_ID} ref={headingRef} tabIndex={-1} className="font-serif text-xl font-semibold outline-none">
            {STEP_TITLES[step - 1]}
          </h2>
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label="닫기"
          className="tap-min -mr-2 -mt-1 inline-flex flex-none items-center justify-center rounded-full text-ink-2 hover:bg-surface"
        >
          <XIcon width={20} height={20} />
        </button>
      </div>

      <div className="mt-4 flex flex-col gap-4">
        {step === 1 ? (
          <div className="flex flex-col gap-2">
            <p className="text-body-min leading-[1.7] text-ink">기록한 날마다 한 칸씩 채워져요. 쉬는 날이 있어도 괜찮아요.</p>
            <p className="text-caption-min leading-[1.6] text-ink-3">
              같은 날 여러 번 기록해도 한 칸이에요. 한 번 채운 칸은 그대로 남아요.
            </p>
          </div>
        ) : null}

        {step === 2 ? (
          <div className="flex flex-col gap-4">
            <div role="group" aria-label="목표 일수 고르기" className="flex flex-wrap gap-2">
              {GOAL_DAY_CHIPS.map((days) => (
                <button
                  key={days}
                  type="button"
                  aria-pressed={goalDays === days}
                  onClick={() => setDaysText(String(days))}
                  className={[
                    'tap-min rounded-lg border px-4 text-body-min font-medium',
                    goalDays === days ? 'border-ink bg-ink text-white' : 'border-line-2 bg-white text-ink',
                  ].join(' ')}
                >
                  {days}일
                </button>
              ))}
            </div>
            <Field
              label="직접 입력"
              htmlFor="dot-goal-days"
              hint={`${MIN_GOAL_DAYS}일부터 ${MAX_GOAL_DAYS}일까지 정할 수 있어요`}
              error={parsed.message ?? undefined}
            >
              <Input
                id="dot-goal-days"
                inputMode="numeric"
                autoComplete="off"
                value={daysText}
                invalid={parsed.message != null}
                aria-invalid={parsed.message != null}
                onChange={(event) => setDaysText(event.target.value)}
                className="max-w-[10rem]"
              />
            </Field>
            <div className="flex min-h-[3.5rem] flex-col gap-1" aria-live="polite">
              {goalDays != null ? <p className="text-body-min font-medium text-ink">{goalDays}일 기록하면 완성돼요</p> : null}
              <p className="text-caption-min leading-[1.6] text-ink-3">
                달력 기간이 아니라 기록한 날 수예요. 쉬는 날은 세지 않고, 기한도 없어요.
              </p>
            </div>
          </div>
        ) : null}

        {step === 3 ? (
          <div className="flex flex-col gap-3">
            {showRecommended ? (
              <p className="text-caption-min leading-[1.6] text-ink-3">
                짧은 목표에는 칸이 적어도 모양이 잘 보이는 그림을 먼저 보여 드려요. 다른 그림도 고를 수 있어요.
              </p>
            ) : null}
            <div
              className="flex items-center justify-center rounded-xl bg-surface"
              style={{ height: LARGE_PREVIEW_HEIGHT_PX }}
            >
              {presetsQuery.isError ? (
                <p className="text-caption-min text-ink-3">그림 목록을 불러오지 못했어요. 잠시 후 다시 열어 주세요.</p>
              ) : (
                <PreviewPicture
                  preview={selectedQuery?.data}
                  failed={selectedQuery?.isError ?? false}
                  maxHeight={LARGE_PREVIEW_HEIGHT_PX - 20}
                />
              )}
            </div>
            <fieldset className="m-0 border-0 p-0">
              <legend className="sr-only">그림 고르기</legend>
              <div className="grid grid-cols-3 gap-2">
                {orderedPresets.map((preset, index) => {
                  const query = previewQueries[index]
                  const recommended = showRecommended && RECOMMENDED_FOR_SHORT_GOALS.includes(preset.key)
                  return (
                    <label
                      key={preset.key}
                      className="relative flex cursor-pointer flex-col items-center gap-1.5 rounded-xl border border-line-2 bg-white p-2 has-[:checked]:border-2 has-[:checked]:border-base has-[:checked]:p-[calc(0.5rem-1px)] has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-base"
                    >
                      <input
                        type="radio"
                        name="dot-preset"
                        value={preset.key}
                        checked={selectedKey === preset.key}
                        onChange={() => setPickedKey(preset.key)}
                        className="sr-only"
                      />
                      <div className="flex items-center justify-center" style={{ width: THUMB_BOX_PX, height: THUMB_BOX_PX }}>
                        <PreviewPicture preview={query?.data} failed={query?.isError ?? false} box={THUMB_BOX_PX} />
                      </div>
                      <span className="text-caption-min font-medium text-ink">{preset.label}</span>
                      <span className="flex h-6 items-center">
                        {recommended ? (
                          <span className="inline-flex h-6 items-center rounded-md bg-tag-topic-bg px-2 text-caption-min font-medium leading-none text-tag-topic-fg">
                            추천
                          </span>
                        ) : null}
                      </span>
                    </label>
                  )
                })}
              </div>
            </fieldset>
          </div>
        ) : null}

        {step === 4 ? (
          <div className="flex flex-col gap-3">
            <p className="text-body-min font-medium text-ink">
              {selectedPreset?.label} · {goalDays}칸 · 오늘부터
            </p>
            <div
              className="flex items-center justify-center rounded-xl bg-surface"
              style={{ height: LARGE_PREVIEW_HEIGHT_PX }}
            >
              <PreviewPicture
                preview={selectedQuery?.data}
                failed={selectedQuery?.isError ?? false}
                maxHeight={LARGE_PREVIEW_HEIGHT_PX - 20}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <p className="text-caption-min leading-[1.6] text-ink-2">
                시작한 뒤에는 목표를 바꿀 수 없어요. 그만두면 이 그림은 보관만 돼요.
              </p>
              {summaryQuery.data?.todayRecorded ? (
                <p className="text-caption-min leading-[1.6] text-ink-2">오늘 기록한 날은 바로 한 칸으로 세어져요.</p>
              ) : null}
            </div>
            {start.isError ? <Note variant="sand">{startErrorMessage(start.error)}</Note> : null}
          </div>
        ) : null}
      </div>

      {/* 내용이 길어 모달 안에서 스크롤돼도 이전·다음 버튼은 항상 보이게 아래에 붙여 둔다. */}
      <div className="sticky -bottom-5 -mx-5 mt-5 flex gap-2 bg-paper px-5 pb-5 pt-3 lg:-bottom-6 lg:-mx-6 lg:justify-end lg:px-6 lg:pb-6">
        {step > 1 ? (
          <Button type="button" variant="secondary" onClick={goBack} disabled={start.isPending} className="flex-1! lg:flex-none!">
            이전
          </Button>
        ) : (
          <Button type="button" variant="secondary" onClick={onClose} className="flex-1! lg:flex-none!">
            다음에 할게요
          </Button>
        )}
        {step < 4 ? (
          <Button
            type="button"
            onClick={() => setStep((current) => current + 1)}
            disabled={(step === 2 && goalDays == null) || (step === 3 && !selectedKey)}
            className="flex-1! lg:flex-none!"
          >
            다음
          </Button>
        ) : (
          <Button type="button" onClick={handleStart} disabled={start.isPending || goalDays == null} className="flex-1! lg:flex-none!">
            {start.isPending ? '시작하는 중이에요' : '시작하기'}
          </Button>
        )}
      </div>
    </Dialog>
  )
}
