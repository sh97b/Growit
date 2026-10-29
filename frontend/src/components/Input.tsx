import { forwardRef, type InputHTMLAttributes } from 'react'

type InputProps = InputHTMLAttributes<HTMLInputElement> & {
  invalid?: boolean
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { invalid = false, className = '', ...props },
  ref,
) {
  return (
    <input
      ref={ref}
      className={[
        'h-12 w-full rounded-lg border bg-white px-3.5 text-[15px] text-ink outline-none',
        invalid ? 'border-[#B7803F]' : 'border-input',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      {...props}
    />
  )
})
