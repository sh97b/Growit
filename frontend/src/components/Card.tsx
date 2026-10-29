import type { ReactNode } from 'react'

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <div className={['rounded-xl border border-line bg-white p-6', className].filter(Boolean).join(' ')}>
      {children}
    </div>
  )
}

export function Band({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <div
      className={[
        'rounded-2xl p-7',
        'bg-[color-mix(in_srgb,var(--color-base)_9%,white)]',
        'border border-[color-mix(in_srgb,var(--color-base)_28%,white)]',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {children}
    </div>
  )
}
