import { useEffect, useRef, useState } from 'react'
import { createKoreanSpeechRecognition, isSpeechRecognitionSupported } from '../lib/speech'
import { MicIcon } from './icons'

type Props = {
  onTranscript: (text: string) => void
}

/** 브라우저가 음성 인식을 지원할 때만 보인다(기능 감지). 지원하지 않으면 아무것도 그리지 않는다. */
export function VoiceInputButton({ onTranscript }: Props) {
  const [listening, setListening] = useState(false)
  const [denied, setDenied] = useState(false)
  const recognitionRef = useRef<ReturnType<typeof createKoreanSpeechRecognition>>(null)

  useEffect(() => {
    return () => {
      recognitionRef.current?.stop()
    }
  }, [])

  if (!isSpeechRecognitionSupported()) {
    return null
  }

  function handleClick() {
    if (listening) {
      recognitionRef.current?.stop()
      return
    }

    const recognition = createKoreanSpeechRecognition()
    if (!recognition) return

    setDenied(false)
    recognition.onresult = (event) => {
      const transcript = event.results[event.results.length - 1]?.[0]?.transcript
      if (transcript) {
        onTranscript(transcript.trim())
      }
    }
    recognition.onerror = (event) => {
      if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
        setDenied(true)
      }
    }
    recognition.onend = () => setListening(false)

    recognitionRef.current = recognition
    setListening(true)
    recognition.start()
  }

  return (
    <div className="absolute bottom-2.5 right-2.5 flex flex-col items-end gap-1.5">
      {denied ? (
        <span className="rounded-md bg-[color-mix(in_srgb,var(--color-ink)_85%,white)] px-2.5 py-1 text-caption-min text-white">
          마이크 권한이 꺼져 있어요
        </span>
      ) : null}
      <button
        type="button"
        aria-pressed={listening}
        aria-label={listening ? '음성 인식 중지' : '음성으로 입력'}
        onClick={handleClick}
        className={[
          'inline-flex h-11 w-11 items-center justify-center rounded-full border',
          listening ? 'border-transparent bg-base text-white' : 'border-line-2 bg-white text-ink',
        ].join(' ')}
      >
        <MicIcon width={20} height={20} />
      </button>
    </div>
  )
}
