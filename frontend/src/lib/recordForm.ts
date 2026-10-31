import type { Feeling } from './records'

export type DateMode = 'today' | 'yesterday' | 'custom'

export const MINUTE_PRESETS = [15, 30, 45, 60]

export const FEELINGS: { value: Feeling; label: string }[] = [
  { value: 'PROUD', label: '뿌듯함' },
  { value: 'STUCK', label: '답답함' },
  { value: 'RELIEVED', label: '개운함' },
  { value: 'NEUTRAL', label: '담담함' },
]

export function pickClass(active: boolean): string {
  return [
    'inline-flex min-h-11 items-center justify-center gap-1.5 rounded-lg border px-4 text-sm font-medium whitespace-nowrap',
    active ? 'border-base bg-[color-mix(in_srgb,var(--color-base)_12%,white)] text-ink' : 'border-line-2 bg-white text-ink',
  ].join(' ')
}
