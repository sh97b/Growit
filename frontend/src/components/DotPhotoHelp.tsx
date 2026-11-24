import { useEffect, useMemo, useRef, useState } from 'react'
import { cropRectFor, photoToDotGrid, type SourceImage } from '../lib/photoDots'
import { goodExamplePhoto, hardExamplePhoto } from '../lib/photoExamples'
import { Dialog } from './Dialog'
import { DotPicture } from './DotPicture'
import { XIcon } from './icons'

const TITLE_ID = 'dot-photo-help-title'
const EXAMPLE_GOAL_DAYS = 100
const EXAMPLE_BOX_PX = 128

const TIPS = [
  '배경이 단순할수록 잘 돼요',
  '피사체를 크게 담을수록 잘 돼요',
  '밝고 어두운 대비가 뚜렷할수록 잘 돼요',
  '한 가지 물체일수록 잘 돼요',
]

function ExamplePhoto({ image, label }: { image: SourceImage; label: string }) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  useEffect(() => {
    const context = canvasRef.current?.getContext('2d')
    if (!context) return
    const pixels = new Uint8ClampedArray(image.data.length)
    pixels.set(image.data)
    context.putImageData(new ImageData(pixels, image.width, image.height), 0, 0)
  }, [image])
  return (
    <canvas
      ref={canvasRef}
      width={image.width}
      height={image.height}
      role="img"
      aria-label={label}
      className="rounded-lg border border-line"
      style={{ width: EXAMPLE_BOX_PX, height: EXAMPLE_BOX_PX }}
    />
  )
}

/** 예시 사진 → 앱의 실제 변환 함수(N=100)로 만든 도트 그림. 이 컴포넌트는 도움말 창이 열릴 때만 그려져서 그때만 계산한다. */
function ExamplePair({ image, photoLabel, dotLabel }: { image: SourceImage; photoLabel: string; dotLabel: string }) {
  const grid = useMemo(
    () => photoToDotGrid(image, cropRectFor(image.width, image.height, 1, 1, 0, 0), EXAMPLE_GOAL_DAYS, { removeBackground: true }),
    [image],
  )
  return (
    <div className="flex flex-wrap items-center gap-3">
      <ExamplePhoto image={image} label={photoLabel} />
      <span aria-hidden="true" className="text-ink-3">→</span>
      <div className="flex items-center justify-center" style={{ width: EXAMPLE_BOX_PX, height: EXAMPLE_BOX_PX }}>
        <DotPicture
          width={grid.width}
          height={grid.height}
          palette={grid.palette}
          cells={grid.cells.map((cell) => ({ x: cell.x, y: cell.y, color: cell.c }))}
          filledCount={grid.cells.length}
          box={EXAMPLE_BOX_PX}
          ariaLabel={dotLabel}
        />
      </div>
    </div>
  )
}

function HelpContent({ onClose }: { onClose: () => void }) {
  const good = useMemo(() => goodExamplePhoto(), [])
  const hard = useMemo(() => hardExamplePhoto(), [])
  return (
    <Dialog onClose={onClose} labelledBy={TITLE_ID}>
      <div className="flex items-start justify-between gap-3">
        <h2 id={TITLE_ID} className="font-serif t-section font-semibold">
          어떤 사진이 좋은가요?
        </h2>
        <button
          type="button"
          onClick={onClose}
          aria-label="닫기"
          className="tap-min -mr-2 -mt-1 inline-flex flex-none items-center justify-center rounded-full text-ink-2 hover:bg-surface"
        >
          <XIcon width={20} height={20} />
        </button>
      </div>
      <div className="mt-4 flex flex-col gap-5">
        <section className="flex flex-col gap-2">
          <h3 className="text-body-min font-medium text-ink">이런 사진이 잘 돼요</h3>
          <ExamplePair image={good} photoLabel="좋은 예시 사진: 단색 배경 위의 사과 하나" dotLabel="좋은 예시를 도트로 바꾼 그림" />
          <p className="text-caption-min leading-[1.6] text-ink-3">단색 배경 위에 단순한 물체 하나. 색도 두세 가지뿐이에요.</p>
        </section>
        <section className="flex flex-col gap-2">
          <h3 className="text-body-min font-medium text-ink">이런 사진은 어려워요</h3>
          <ExamplePair image={hard} photoLabel="어려운 예시 사진: 복잡한 무늬 위에 작은 물체 여러 개" dotLabel="어려운 예시를 도트로 바꾼 그림" />
          <p className="text-caption-min leading-[1.6] text-ink-3">복잡한 무늬 위에 작은 물체가 여러 개 흩어져 있어요. 도트로 바꾸면 모양이 흐려져요.</p>
        </section>
        <section className="flex flex-col gap-2">
          <h3 className="text-body-min font-medium text-ink">짧은 팁</h3>
          <ul className="m-0 flex list-disc flex-col gap-1 pl-5 text-body-min text-ink">
            {TIPS.map((tip) => (
              <li key={tip}>{tip}</li>
            ))}
          </ul>
        </section>
      </div>
    </Dialog>
  )
}

/** 사진 안내 문구 옆의 작은 물음표 버튼. 누르면 도움말 창이 뜨고, 창이 열릴 때만 예시 변환을 돌린다. */
export function DotPhotoHelpButton() {
  const [open, setOpen] = useState(false)
  return (
    <>
      <button
        type="button"
        aria-label="어떤 사진이 좋은가요?"
        aria-haspopup="dialog"
        onClick={() => setOpen(true)}
        className="tap-min inline-flex flex-none items-center justify-center rounded-full"
      >
        <span
          aria-hidden="true"
          className="inline-flex h-6 w-6 items-center justify-center rounded-full border border-line-2 bg-white text-caption-min font-medium text-ink-2"
        >
          ?
        </span>
      </button>
      {open ? <HelpContent onClose={() => setOpen(false)} /> : null}
    </>
  )
}
