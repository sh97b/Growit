import { useId, useState, type InputHTMLAttributes } from 'react'
import { Field } from './Field'
import { Input } from './Input'

type PasswordFieldProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'id' | 'type'> & {
  id?: string
  label: string
  hint?: string
  error?: string
}

export function PasswordField({ id, label, hint, error, ...props }: PasswordFieldProps) {
  const autoId = useId()
  const inputId = id ?? autoId
  const [visible, setVisible] = useState(false)

  return (
    <Field label={label} htmlFor={inputId} hint={hint} error={error}>
      <div className="relative">
        <Input id={inputId} type={visible ? 'text' : 'password'} className="pr-[52px]" {...props} />
        <button
          type="button"
          aria-label={visible ? '비밀번호 숨기기' : '비밀번호 보기'}
          onClick={() => setVisible((v) => !v)}
          className="absolute right-0.5 top-0.5 flex h-11 w-11 items-center justify-center rounded-lg text-ink-2"
        >
          <svg
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z" />
            <circle cx="12" cy="12" r="3" />
          </svg>
        </button>
      </div>
    </Field>
  )
}
