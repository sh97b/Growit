import type { ReactNode } from 'react'

type FieldProps = {
  label: string
  htmlFor: string
  hint?: string
  error?: string
  children: ReactNode
}

export function Field({ label, htmlFor, hint, error, children }: FieldProps) {
  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={htmlFor} className="text-sm font-medium">
        {label}
      </label>
      {children}
      {error ? (
        <span className="flex items-center gap-1.5 text-[13px] leading-[1.55] text-[#6B3F0F]">{error}</span>
      ) : hint ? (
        <span className="text-[13px] leading-[1.55] text-ink-2">{hint}</span>
      ) : null}
    </div>
  )
}
