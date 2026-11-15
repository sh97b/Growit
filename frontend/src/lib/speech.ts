type SpeechRecognitionResultLike = {
  readonly length: number
  isFinal: boolean
  [index: number]: { transcript: string }
}

type SpeechRecognitionEventLike = {
  resultIndex: number
  results: ArrayLike<SpeechRecognitionResultLike>
}

type SpeechRecognitionErrorEventLike = { error: string }

type SpeechRecognitionLike = {
  lang: string
  interimResults: boolean
  continuous: boolean
  onresult: ((event: SpeechRecognitionEventLike) => void) | null
  onerror: ((event: SpeechRecognitionErrorEventLike) => void) | null
  onend: (() => void) | null
  start: () => void
  stop: () => void
}

type SpeechRecognitionConstructor = new () => SpeechRecognitionLike

function getSpeechRecognitionConstructor(): SpeechRecognitionConstructor | null {
  const w = window as unknown as {
    SpeechRecognition?: SpeechRecognitionConstructor
    webkitSpeechRecognition?: SpeechRecognitionConstructor
  }
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null
}

export function isSpeechRecognitionSupported(): boolean {
  return getSpeechRecognitionConstructor() != null
}

/** O1(F): 버튼을 다시 누를 때까지 계속 듣도록 continuous+interimResults를 켠다. */
export function createKoreanSpeechRecognition(): SpeechRecognitionLike | null {
  const Ctor = getSpeechRecognitionConstructor()
  if (!Ctor) return null
  const recognition = new Ctor()
  recognition.lang = 'ko-KR'
  recognition.interimResults = true
  recognition.continuous = true
  return recognition
}

export type { SpeechRecognitionEventLike, SpeechRecognitionErrorEventLike, SpeechRecognitionLike }
