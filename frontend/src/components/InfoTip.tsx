import { useEffect, useId, useRef, useState } from 'react'

/**
 * ⓘ 아이콘 하나만 두고, 마우스 올림·키보드 포커스·탭(모바일)에서 설명 문장을 말풍선으로 띄운다(aria-describedby).
 * 말풍선은 아이콘 오른쪽 끝에 맞춰 왼쪽으로 펼쳐지고 최대 폭을 화면 안으로 제한해 화면 밖으로 나가지 않는다.
 */
export function InfoTip({ text }: { text: string }) {
  const [open, setOpen] = useState(false)
  const id = useId()
  const ref = useRef<HTMLSpanElement>(null)

  useEffect(() => {
    if (!open) return
    function onPointerDown(event: PointerEvent) {
      if (ref.current && !ref.current.contains(event.target as Node)) setOpen(false)
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  return (
    <span ref={ref} className="relative inline-flex flex-none" onMouseEnter={() => setOpen(true)} onMouseLeave={() => setOpen(false)}>
      <button
        type="button"
        aria-label="비교 기준 안내"
        aria-describedby={open ? id : undefined}
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        className="t-cap inline-flex h-8 w-8 items-center justify-center rounded-full text-ink-2 hover:bg-[color-mix(in_srgb,var(--color-ink)_6%,transparent)] lg:h-7 lg:w-7"
      >
        <span aria-hidden="true" className="inline-flex h-5 w-5 items-center justify-center rounded-full border border-line-2">
          i
        </span>
      </button>
      {open ? (
        <span
          id={id}
          role="tooltip"
          className="t-sub absolute right-0 top-full z-30 mt-1 w-max max-w-[min(18rem,calc(100vw-2rem))] rounded-lg border border-line-2 bg-white px-3 py-2 text-ink shadow-md"
        >
          {text}
        </span>
      ) : null}
    </span>
  )
}
