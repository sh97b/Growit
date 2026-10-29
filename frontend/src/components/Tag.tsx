import type { ReactNode } from 'react'

type TagVariant = 'topic' | 'hard' | 'feel'

const variantClasses: Record<TagVariant, string> = {
  topic: 'bg-tag-topic-bg text-tag-topic-fg rounded-md',
  hard: 'bg-tag-hard-bg text-tag-hard-fg rounded-md',
  feel: 'rounded-full bg-tag-feel-bg px-3 text-tag-feel-fg',
}

export function Tag({ variant, children }: { variant: TagVariant; children: ReactNode }) {
  return (
    <span
      className={[
        'inline-flex min-h-7 items-center gap-1 whitespace-nowrap px-2.5 text-[13px] font-medium',
        variantClasses[variant],
      ].join(' ')}
    >
      {children}
    </span>
  )
}
