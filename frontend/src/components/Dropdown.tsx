import { useEffect, useId, useRef, useState } from 'react'
import { CheckIcon, ChevronDownIcon } from './icons'

export type DropdownOption<T extends string> = { value: T; label: string }

type DropdownProps<T extends string> = {
  value: T
  options: DropdownOption<T>[]
  onChange: (value: T) => void
  'aria-label': string
  /** 팝오버 하나를 드롭다운 패널 아래에 추가로 띄운다(예: 기간의 "직접 선택" 날짜 입력). */
  extra?: { show: boolean; content: (close: () => void) => React.ReactNode }
  /** 알약 모양(작고 둥근 버튼, 예: 홈 한 줄 기록의 분야 선택 "러닝 ▾"). 기본은 ReviewToolbar류의 각진 버튼. */
  pill?: boolean
}

/**
 * 네이티브 select 대신 쓰는 가벼운 키보드·스크린리더 접근 가능한 드롭다운(O1 C.2, R1 1번).
 * 새 라이브러리 없이 button + role="listbox" 패턴으로 직접 구현한다. 팝오버는 항상 absolute로
 * 떠서(aria-expanded만 바뀜) 아래 요소를 밀지 않는다.
 */
export function Dropdown<T extends string>({ value, options, onChange, extra, pill, ...aria }: DropdownProps<T>) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const listId = useId()
  const current = options.find((option) => option.value === value)

  useEffect(() => {
    if (!open) return
    function handlePointerDown(event: PointerEvent) {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) setOpen(false)
    }
    function handleKey(event: KeyboardEvent) {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleKey)
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('keydown', handleKey)
    }
  }, [open])

  function moveFocus(delta: number) {
    const index = options.findIndex((option) => option.value === value)
    const nextIndex = Math.min(options.length - 1, Math.max(0, (index === -1 ? 0 : index) + delta))
    onChange(options[nextIndex].value)
  }

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-label={aria['aria-label']}
        onClick={() => setOpen((prev) => !prev)}
        onKeyDown={(event) => {
          if (event.key === 'ArrowDown') {
            event.preventDefault()
            if (!open) setOpen(true)
            else moveFocus(1)
          } else if (event.key === 'ArrowUp') {
            event.preventDefault()
            moveFocus(-1)
          }
        }}
        className={
          pill
            ? 'flex h-9 flex-none items-center gap-1 whitespace-nowrap rounded-full border border-line-2 bg-white px-3 text-sm font-medium text-ink'
            : 'flex h-10 w-full min-h-11 items-center justify-between gap-1.5 whitespace-nowrap rounded-lg border border-line-2 bg-white px-3 text-sm font-medium text-ink lg:w-auto lg:min-h-10 lg:justify-start'
        }
      >
        {current?.label ?? '선택'}
        <ChevronDownIcon width={14} height={14} className="flex-none text-ink-2" />
      </button>

      {open ? (
        <div className="absolute left-0 top-full z-20 mt-1.5 min-w-[9.5rem] rounded-lg border border-line-2 bg-white p-1.5 shadow-sm">
          <ul id={listId} role="listbox" aria-label={aria['aria-label']} className="flex flex-col">
            {options.map((option) => (
              <li key={option.value} role="presentation">
                <button
                  type="button"
                  role="option"
                  aria-selected={option.value === value}
                  onClick={() => {
                    onChange(option.value)
                    if (!extra?.show) setOpen(false)
                  }}
                  className={[
                    'flex min-h-10 w-full items-center justify-between gap-2 rounded-md px-3 text-left text-sm',
                    option.value === value ? 'bg-surface font-semibold text-ink' : 'text-ink-2',
                  ].join(' ')}
                >
                  {option.label}
                  {option.value === value ? <CheckIcon width={14} height={14} className="flex-none text-base" /> : null}
                </button>
              </li>
            ))}
          </ul>
          {extra?.show ? (
            <div className="mt-1.5 border-t border-line pt-1.5">{extra.content(() => setOpen(false))}</div>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
