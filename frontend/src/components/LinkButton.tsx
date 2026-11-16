import type { AnchorHTMLAttributes } from 'react'
import { Link, type LinkProps } from 'react-router-dom'

type Props = LinkProps & AnchorHTMLAttributes<HTMLAnchorElement>

export function LinkButton({ className = '', children, ...props }: Props) {
  return (
    <Link
      className={[
        'inline-flex min-h-11 items-center gap-1 font-medium text-base-ink no-underline rounded-full px-2 -mx-2 hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)] focus-visible:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      {...props}
    >
      {children}
    </Link>
  )
}
