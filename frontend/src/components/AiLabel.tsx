import type { ReactNode } from 'react'
import { SparkleIcon } from './icons'

export function AiLabel({ children }: { children: ReactNode }) {
  return (
    <span className="inline-flex w-fit items-center gap-1.5 self-start rounded-md bg-[color-mix(in_srgb,var(--color-base)_12%,white)] px-2.5 py-1 text-caption-min font-semibold text-ink">
      <SparkleIcon width={14} height={14} />
      {children}
    </span>
  )
}
