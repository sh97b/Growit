import type { ReactNode } from 'react'

type NoteVariant = 'sand' | 'slate' | 'calm'

const variantClasses: Record<NoteVariant, string> = {
  sand: 'bg-note-sand-bg border border-note-sand-line text-note-sand-fg',
  slate: 'bg-note-slate-bg border border-note-slate-line text-note-slate-fg',
  calm: 'bg-surface text-ink-2',
}

export function Note({
  variant = 'calm',
  children,
}: {
  variant?: NoteVariant
  children: ReactNode
}) {
  return (
    <div
      role="status"
      className={[
        'flex items-start gap-3 rounded-xl px-[18px] py-4 text-sm leading-[1.7]',
        variantClasses[variant],
      ].join(' ')}
    >
      {children}
    </div>
  )
}
