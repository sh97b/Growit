import { MicIcon } from './icons'

type Props = {
  listening: boolean
  interimText: string
  denied: boolean
  gaveUp: boolean
  onToggle: () => void
}

/** 상태는 useVoiceInput 훅(호출부)이 들고 있고, 이 컴포넌트는 버튼·안내만 그린다. */
export function VoiceInputButton({ listening, interimText, denied, gaveUp, onToggle }: Props) {
  return (
    <div className="absolute bottom-2.5 right-2.5 flex flex-col items-end gap-1.5">
      {denied ? (
        <span className="rounded-md bg-[color-mix(in_srgb,var(--color-ink)_85%,white)] px-2.5 py-1 text-caption-min text-white">
          마이크 권한이 꺼져 있어요
        </span>
      ) : null}
      {gaveUp ? (
        <span className="rounded-md bg-[color-mix(in_srgb,var(--color-ink)_85%,white)] px-2.5 py-1 text-caption-min text-white">
          음성 인식이 멈췄어요. 다시 눌러 주세요
        </span>
      ) : null}
      {listening && interimText ? (
        <span className="max-w-[14rem] truncate rounded-md bg-white/90 px-2.5 py-1 text-caption-min text-ink-2 shadow-sm">
          {interimText}
        </span>
      ) : null}
      <button
        type="button"
        aria-pressed={listening}
        aria-label={listening ? '음성 인식 중지' : '음성으로 입력'}
        onClick={onToggle}
        className={[
          'inline-flex h-11 w-11 items-center justify-center rounded-full border',
          listening ? 'border-transparent bg-base text-white' : 'border-line-2 bg-white text-ink',
        ].join(' ')}
      >
        <MicIcon width={20} height={20} />
      </button>
      <span aria-live="polite" className="sr-only">
        {listening ? '듣는 중' : ''}
      </span>
    </div>
  )
}
