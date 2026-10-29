import type { AnchorHTMLAttributes } from 'react'
import { Link, type LinkProps } from 'react-router-dom'

type Props = LinkProps & AnchorHTMLAttributes<HTMLAnchorElement>

export function LinkButton({ className = '', children, ...props }: Props) {
  return (
    <Link
      className={[
        'inline-flex min-h-11 items-center gap-1 font-medium text-base-ink underline underline-offset-4',
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
