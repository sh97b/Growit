import type { ReactNode } from 'react'

type EmptyStateProps = {
  title: string
  description?: string
  action?: ReactNode
}

export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-input bg-white px-5 py-9 text-center">
      <p className="font-serif text-lg font-semibold">{title}</p>
      {description ? <p className="text-sm text-ink-2">{description}</p> : null}
      {action}
    </div>
  )
}
