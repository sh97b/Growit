import { forwardRef, type ButtonHTMLAttributes } from 'react'

type Variant = 'primary' | 'secondary' | 'ghost'
type Size = 'md' | 'sm'

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: Variant
  size?: Size
  block?: boolean
}

const variantClasses: Record<Variant, string> = {
  primary: 'bg-base text-white',
  secondary: 'bg-white text-ink border border-line-2',
  ghost: 'bg-transparent text-ink',
}

const sizeClasses: Record<Size, string> = {
  md: 'min-h-11 px-5 text-[15px]',
  sm: 'min-h-10 px-4 text-sm',
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', size = 'md', block = false, className = '', ...props },
  ref,
) {
  return (
    <button
      ref={ref}
      className={[
        'inline-flex items-center justify-center gap-2 rounded-lg border border-transparent font-medium whitespace-nowrap',
        'disabled:cursor-default disabled:border-transparent disabled:bg-line disabled:text-[#5F594F]',
        variantClasses[variant],
        sizeClasses[size],
        block ? 'w-full' : '',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      {...props}
    />
  )
})
