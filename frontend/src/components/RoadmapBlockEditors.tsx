import { useState, type FormEvent } from 'react'
import { Button } from './Button'
import { Input } from './Input'
import { LINK_CATEGORY_LABELS, type BlockKind, type BlockPayload, type LinkCategory } from '../lib/roadmap'

const MEMO_MAX = 500
const SEARCH_MAX = 40
const SUBLIST_MAX = 8

type EditableKind = Exclude<BlockKind, 'AI_HOWTO'>

type EditorProps = {
  kind: EditableKind
  /** 수정이면 기존 payload, 새로 만들면 null. */
  initial: BlockPayload | null
  saving: boolean
  error: string | null
  onSubmit: (payload: BlockPayload) => void
  onCancel: () => void
}

const textareaClass =
  'w-full resize-y rounded-lg border border-input bg-white px-3.5 py-2.5 text-body-min text-ink outline-none'

/** 블록 하나를 만들거나 고치는 작은 폼. 종류별 입력만 다르고 저장·취소 줄은 같다. 서버가 다시 검증한다. */
export function BlockEditor({ kind, initial, saving, error, onSubmit, onCancel }: EditorProps) {
  const [text, setText] = useState(String(initial?.text ?? ''))
  const [title, setTitle] = useState(String(initial?.title ?? ''))
  const [url, setUrl] = useState(String(initial?.url ?? ''))
  const [category, setCategory] = useState<LinkCategory>((initial?.category as LinkCategory) ?? 'OTHER')
  const [minutes, setMinutes] = useState(String(initial?.minutes ?? 10))
  const [query, setQuery] = useState(String(initial?.query ?? ''))
  const initialItems = (Array.isArray(initial?.items) ? initial.items : []) as { text: string; done: boolean }[]
  const [items, setItems] = useState<{ text: string; done: boolean }[]>(
    initialItems.length > 0 ? initialItems.map((item) => ({ text: item.text, done: !!item.done })) : [{ text: '', done: false }],
  )
  const [localError, setLocalError] = useState<string | null>(null)

  function submit(event: FormEvent) {
    event.preventDefault()
    setLocalError(null)
    if (kind === 'MEMO') {
      if (!text.trim()) return setLocalError('메모를 적어 주세요')
      onSubmit({ text: text.trim() })
    } else if (kind === 'LINK') {
      if (!url.trim()) return setLocalError('링크 주소를 적어 주세요')
      onSubmit({ title: title.trim() || undefined, url: url.trim(), category })
    } else if (kind === 'TIMER') {
      const value = Number(minutes)
      if (!Number.isInteger(value) || value < 1 || value > 120) return setLocalError('1~120분 사이로 정해 주세요')
      onSubmit({ minutes: value })
    } else if (kind === 'SEARCH') {
      if (!query.trim()) return setLocalError('검색어를 적어 주세요')
      onSubmit({ query: query.trim() })
    } else {
      const cleaned = items.map((item) => ({ text: item.text.trim(), done: item.done })).filter((item) => item.text)
      if (cleaned.length === 0) return setLocalError('하위 체크를 하나 이상 적어 주세요')
      onSubmit({ items: cleaned })
    }
  }

  const message = localError ?? error

  return (
    <form onSubmit={submit} className="flex min-w-0 flex-col gap-2.5 rounded-xl border border-line-2 bg-white p-3">
      {kind === 'MEMO' ? (
        <label className="flex flex-col gap-1">
          <span className="text-xs text-ink-2">메모 ({text.length}/{MEMO_MAX})</span>
          <textarea
            value={text}
            maxLength={MEMO_MAX}
            rows={4}
            autoFocus
            onChange={(event) => setText(event.target.value)}
            className={`${textareaClass} font-serif`}
            aria-label="메모"
          />
        </label>
      ) : null}

      {kind === 'LINK' ? (
        <>
          <label className="flex flex-col gap-1">
            <span className="text-xs text-ink-2">링크 주소(http 또는 https)</span>
            <Input
              type="url"
              inputMode="url"
              value={url}
              maxLength={500}
              autoFocus
              placeholder="https://"
              onChange={(event) => setUrl(event.target.value)}
              aria-label="링크 주소"
            />
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-xs text-ink-2">제목(선택)</span>
            <Input value={title} maxLength={60} onChange={(event) => setTitle(event.target.value)} aria-label="링크 제목" />
          </label>
          <div className="flex flex-wrap gap-1.5" role="radiogroup" aria-label="링크 구분">
            {(Object.keys(LINK_CATEGORY_LABELS) as LinkCategory[]).map((key) => (
              <button
                key={key}
                type="button"
                role="radio"
                aria-checked={category === key}
                onClick={() => setCategory(key)}
                className={[
                  'min-h-11 rounded-full border px-4 text-sm lg:min-h-9',
                  category === key ? 'border-base bg-tag-topic-bg font-medium text-tag-topic-fg' : 'border-line-2 bg-white text-ink-2',
                ].join(' ')}
              >
                {LINK_CATEGORY_LABELS[key]}
              </button>
            ))}
          </div>
        </>
      ) : null}

      {kind === 'TIMER' ? (
        <label className="flex items-center gap-2">
          <span className="text-sm text-ink-2">시간</span>
          <Input
            type="number"
            min={1}
            max={120}
            value={minutes}
            autoFocus
            onChange={(event) => setMinutes(event.target.value)}
            className="!w-24"
            aria-label="타이머 분"
          />
          <span className="text-sm text-ink-2">분 (1~120)</span>
        </label>
      ) : null}

      {kind === 'SEARCH' ? (
        <label className="flex flex-col gap-1">
          <span className="text-xs text-ink-2">검색어 ({query.length}/{SEARCH_MAX})</span>
          <Input value={query} maxLength={SEARCH_MAX} autoFocus onChange={(event) => setQuery(event.target.value)} aria-label="검색어" />
        </label>
      ) : null}

      {kind === 'SUBLIST' ? (
        <div className="flex flex-col gap-2">
          {items.map((item, index) => (
            <div key={index} className="flex items-center gap-2">
              <Input
                value={item.text}
                maxLength={60}
                autoFocus={index === items.length - 1 && items.length > 1}
                onChange={(event) =>
                  setItems((prev) => prev.map((entry, i) => (i === index ? { ...entry, text: event.target.value } : entry)))
                }
                aria-label={`하위 체크 ${index + 1}`}
              />
              <button
                type="button"
                aria-label={`하위 체크 ${index + 1} 지우기`}
                disabled={items.length === 1}
                onClick={() => setItems((prev) => prev.filter((_, i) => i !== index))}
                className="inline-flex h-11 w-11 flex-none items-center justify-center rounded-lg text-ink-2 hover:bg-surface disabled:opacity-40"
              >
                ✕
              </button>
            </div>
          ))}
          {items.length < SUBLIST_MAX ? (
            <button
              type="button"
              onClick={() => setItems((prev) => [...prev, { text: '', done: false }])}
              className="inline-flex min-h-11 w-fit items-center rounded-lg px-2 text-sm font-medium text-base-ink lg:min-h-9"
            >
              + 하위 체크 추가 ({items.length}/{SUBLIST_MAX})
            </button>
          ) : null}
        </div>
      ) : null}

      {message ? (
        <p role="alert" className="text-sm text-ink-2">
          {message}
        </p>
      ) : null}
      <div className="flex gap-2">
        <Button type="submit" size="sm" disabled={saving}>
          저장
        </Button>
        <Button type="button" size="sm" variant="secondary" onClick={onCancel}>
          취소
        </Button>
      </div>
    </form>
  )
}
