import { useEffect, useRef } from 'react'
import { PHOTO_TITLE_MAX, RATIOS, type PhotoDraft } from '../hooks/usePhotoDraft'
import { MAX_COLORS, MIN_COLORS } from '../lib/photoDots'
import { Field } from './Field'
import { Input } from './Input'
import { Note } from './Note'

/** 자른 사진을 보여 주는 작은 화면의 가로 크기(px). */
const PREVIEW_WIDTH_PX = 144

function Slider({
  label, value, min, max, step, onChange, valueText,
}: {
  label: string
  value: number
  min: number
  max: number
  step: number
  onChange: (value: number) => void
  valueText?: string
}) {
  return (
    <label className="flex flex-col gap-1 text-caption-min text-ink-2">
      <span className="flex justify-between">
        <span>{label}</span>
        {valueText ? <span className="text-ink-3">{valueText}</span> : null}
      </span>
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(event) => onChange(Number(event.target.value))}
        className="tap-min w-full accent-[var(--color-base)]"
      />
    </label>
  )
}

/** 사진 고르기, 자르기(확대·위치), 색 수 조절. 변환 결과는 부모가 draft.grid로 읽어 큰 미리보기에 보여 준다. */
export function DotPhotoMaker({ draft }: { draft: PhotoDraft }) {
  const previewRef = useRef<HTMLCanvasElement>(null)
  const { working, crop } = draft

  useEffect(() => {
    const canvas = previewRef.current
    if (!canvas || !working || !crop) return
    const context = canvas.getContext('2d')
    if (!context) return
    context.clearRect(0, 0, canvas.width, canvas.height)
    context.drawImage(working.canvas, crop.x, crop.y, crop.w, crop.h, 0, 0, canvas.width, canvas.height)
  }, [working, crop])

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-line bg-white p-3">
      <p className="text-caption-min leading-[1.6] text-ink-3">
        사진은 이 기기에서만 도트로 바꾸고 서버에는 올리지 않아요. 칸이 적을수록 단순하고 대비가 큰 사진이 좋아요.
      </p>
      <div>
        <label htmlFor="dot-photo-file" className="sr-only">
          사진 고르기
        </label>
        <input
          id="dot-photo-file"
          type="file"
          accept="image/*"
          onChange={(event) => {
            void draft.loadFile(event.target.files?.[0])
            event.target.value = '' // 같은 사진을 다시 골라도 반응하고, 파일 선택 상태도 남기지 않는다
          }}
          className="block w-full text-caption-min text-ink-2 file:mr-3 file:min-h-11 file:cursor-pointer file:rounded-lg file:border file:border-line-2 file:bg-white file:px-4 file:text-body-min file:font-medium file:text-ink"
        />
      </div>
      {draft.loading ? <p className="text-caption-min text-ink-3">사진을 읽고 있어요</p> : null}
      {draft.error ? <Note variant="sand">{draft.error}</Note> : null}

      {working && crop ? (
        <div className="flex flex-col gap-3">
          <div className="flex flex-wrap items-start gap-3">
            <canvas
              ref={previewRef}
              width={PREVIEW_WIDTH_PX}
              height={Math.max(1, Math.round(PREVIEW_WIDTH_PX / draft.aspect))}
              role="img"
              aria-label="자른 사진 미리보기"
              className="rounded-lg border border-line"
            />
            <div className="flex min-w-[10rem] flex-1 flex-col gap-2">
              <div role="group" aria-label="자르는 비율" className="flex gap-2">
                {RATIOS.map((ratio, index) => (
                  <button
                    key={ratio.label}
                    type="button"
                    aria-pressed={draft.ratioIndex === index}
                    onClick={() => draft.setRatioIndex(index)}
                    className={[
                      'tap-min rounded-lg border px-3 text-caption-min font-medium',
                      draft.ratioIndex === index ? 'border-ink bg-ink text-white' : 'border-line-2 bg-white text-ink',
                    ].join(' ')}
                  >
                    {ratio.label}
                  </button>
                ))}
              </div>
              <Slider label="확대" value={draft.zoom} min={1} max={3} step={0.05} valueText={`${draft.zoom.toFixed(2)}배`} onChange={draft.setZoom} />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <Slider label="좌우 위치" value={draft.offsetX} min={-1} max={1} step={0.02} onChange={draft.setOffsetX} />
            <Slider label="위아래 위치" value={draft.offsetY} min={-1} max={1} step={0.02} onChange={draft.setOffsetY} />
          </div>
          <Slider
            label="색 수"
            value={draft.colorCount}
            min={MIN_COLORS}
            max={MAX_COLORS}
            step={1}
            valueText={`${draft.colorCount}색`}
            onChange={draft.setColorCount}
          />
          <Field label="그림 이름" htmlFor="dot-photo-title" hint={`비워 두면 "내 사진"으로 저장돼요 (${PHOTO_TITLE_MAX}자까지)`}>
            <Input
              id="dot-photo-title"
              value={draft.title}
              maxLength={PHOTO_TITLE_MAX}
              placeholder="내 사진"
              autoComplete="off"
              onChange={(event) => draft.setTitle(event.target.value)}
            />
          </Field>
        </div>
      ) : null}
    </div>
  )
}
