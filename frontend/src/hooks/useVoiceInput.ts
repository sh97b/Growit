import { useEffect, useRef, useState } from 'react'
import { createKoreanSpeechRecognition, isSpeechRecognitionSupported, type SpeechRecognitionLike } from '../lib/speech'

const MAX_CONSECUTIVE_FAILURES = 5
const MIN_RESTART_INTERVAL_MS = 250

type UseVoiceInputOptions = {
  /** 확정된(isFinal) 말이 나올 때마다 한 번씩 불린다. */
  onFinalResult: (text: string) => void
  /** true가 되면 즉시 끈다(저장 중 등). */
  disabled?: boolean
}

/**
 * 버튼을 한 번 누르면 켜지고 다시 누를 때까지 계속 듣는 연속 음성 인식(O1 F). 브라우저가 소리 없음·
 * 시간 제한으로 세션을 끝내면(onend) 사용자가 끄지 않았다면 자동으로 다시 시작한다(최소 250ms 간격,
 * 연속 5회 실패하면 포기). 권한 거절(not-allowed·service-not-allowed)은 재시작하지 않는다.
 */
export function useVoiceInput({ onFinalResult, disabled = false }: UseVoiceInputOptions) {
  const [listening, setListening] = useState(false)
  const [interimText, setInterimText] = useState('')
  const [denied, setDenied] = useState(false)
  const [gaveUp, setGaveUp] = useState(false)

  const recognitionRef = useRef<SpeechRecognitionLike | null>(null)
  const manuallyStoppedRef = useRef(true)
  const lastFinalTextRef = useRef<string | null>(null)
  const suppressNextDuplicateRef = useRef(false)
  const lastStartAtRef = useRef(0)
  const consecutiveFailuresRef = useRef(0)
  const restartTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const onFinalResultRef = useRef(onFinalResult)
  onFinalResultRef.current = onFinalResult

  function clearRestartTimer() {
    if (restartTimerRef.current != null) {
      clearTimeout(restartTimerRef.current)
      restartTimerRef.current = null
    }
  }

  function teardown() {
    const recognition = recognitionRef.current
    if (recognition) {
      recognition.onresult = null
      recognition.onerror = null
      recognition.onend = null
    }
    recognitionRef.current = null
  }

  function launch() {
    const recognition = createKoreanSpeechRecognition()
    if (!recognition) return

    recognition.onresult = (event) => {
      let interim = ''
      for (let i = event.resultIndex; i < event.results.length; i += 1) {
        const item = event.results[i]
        const transcript = item[0]?.transcript ?? ''
        if (item.isFinal) {
          const trimmed = transcript.trim()
          const isDuplicate = suppressNextDuplicateRef.current && trimmed === lastFinalTextRef.current
          suppressNextDuplicateRef.current = false
          if (trimmed && !isDuplicate) {
            lastFinalTextRef.current = trimmed
            onFinalResultRef.current(trimmed)
          }
        } else {
          interim += transcript
        }
      }
      setInterimText(interim.trim())
    }

    recognition.onerror = (event) => {
      if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
        manuallyStoppedRef.current = true
        setDenied(true)
        setListening(false)
      }
      // 그 외(no-speech 등)는 onend에서 재시작 여부를 판단한다.
    }

    recognition.onend = () => {
      teardown()
      setInterimText('')
      if (manuallyStoppedRef.current || disabled) {
        setListening(false)
        return
      }
      consecutiveFailuresRef.current += 1
      if (consecutiveFailuresRef.current > MAX_CONSECUTIVE_FAILURES) {
        manuallyStoppedRef.current = true
        setListening(false)
        setGaveUp(true)
        return
      }
      suppressNextDuplicateRef.current = true
      const elapsed = Date.now() - lastStartAtRef.current
      const wait = Math.max(0, MIN_RESTART_INTERVAL_MS - elapsed)
      restartTimerRef.current = setTimeout(() => {
        if (manuallyStoppedRef.current || disabled) return
        lastStartAtRef.current = Date.now()
        launch()
      }, wait)
    }

    recognitionRef.current = recognition
    recognition.start()
  }

  function start() {
    if (!isSpeechRecognitionSupported() || listening) return
    clearRestartTimer()
    setDenied(false)
    setGaveUp(false)
    manuallyStoppedRef.current = false
    consecutiveFailuresRef.current = 0
    lastFinalTextRef.current = null
    suppressNextDuplicateRef.current = false
    lastStartAtRef.current = Date.now()
    setListening(true)
    launch()
  }

  function stop() {
    clearRestartTimer()
    manuallyStoppedRef.current = true
    setListening(false)
    setInterimText('')
    recognitionRef.current?.stop()
  }

  // disabled(저장 중 등)가 되면 즉시 끈다.
  useEffect(() => {
    if (disabled) stop()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [disabled])

  // 탭이 숨겨지거나 이 컴포넌트를 떠나면 자동으로 끈다.
  useEffect(() => {
    function handleVisibilityChange() {
      if (document.hidden) stop()
    }
    document.addEventListener('visibilitychange', handleVisibilityChange)
    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange)
      stop()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return {
    supported: isSpeechRecognitionSupported(),
    listening,
    interimText,
    denied,
    gaveUp,
    start,
    stop,
  }
}
