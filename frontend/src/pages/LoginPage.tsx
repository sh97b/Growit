import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '../components/Button'
import { Field } from '../components/Field'
import { Input } from '../components/Input'
import { Note } from '../components/Note'
import { PasswordField } from '../components/PasswordField'
import { ApiError } from '../lib/api'
import { useAuth } from '../lib/auth'

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

type Mode = 'login' | 'signup'

export function LoginPage() {
  const [mode, setMode] = useState<Mode>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fieldError, setFieldError] = useState<string | null>(null)
  const [serverError, setServerError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const { login, signup } = useAuth()
  const navigate = useNavigate()

  function switchMode(next: Mode) {
    setMode(next)
    setFieldError(null)
    setServerError(null)
  }

  function validate(): string | null {
    if (!EMAIL_PATTERN.test(email)) {
      return '올바른 이메일 형식으로 입력해 주세요'
    }
    if (password.length < 8 || password.length > 72) {
      return '비밀번호는 8~72자로 입력해 주세요'
    }
    return null
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setServerError(null)
    const validationError = validate()
    setFieldError(validationError)
    if (validationError) return

    setSubmitting(true)
    try {
      if (mode === 'login') {
        await login(email, password)
      } else {
        await signup(email, password)
      }
      navigate('/', { replace: true })
    } catch (error) {
      setServerError(error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="flex min-h-dvh flex-col bg-paper">
      <div className="mx-auto flex w-full max-w-[420px] flex-1 flex-col gap-6 px-5 py-8">
        <span className="font-serif text-xl font-semibold text-ink">Growit</span>

        <div className="flex gap-0.5 rounded-lg border border-line-2 bg-white p-[3px]" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'login'}
            onClick={() => switchMode('login')}
            className={[
              'min-h-10 flex-1 rounded-md text-sm font-medium',
              mode === 'login' ? 'bg-ink text-white' : 'bg-transparent text-ink-2',
            ].join(' ')}
          >
            로그인
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'signup'}
            onClick={() => switchMode('signup')}
            className={[
              'min-h-10 flex-1 rounded-md text-sm font-medium',
              mode === 'signup' ? 'bg-ink text-white' : 'bg-transparent text-ink-2',
            ].join(' ')}
          >
            회원가입
          </button>
        </div>

        <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
          <h1 className="font-serif text-xl font-semibold leading-[1.45]">
            {mode === 'login' ? '다시 만나서 반가워요' : '첫 기록을 시작해 볼까요'}
          </h1>

          <Field label="이메일" htmlFor="email">
            <Input
              id="email"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              autoComplete="email"
              placeholder="name@example.com"
            />
          </Field>

          <PasswordField
            id="password"
            label="비밀번호"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            hint={mode === 'signup' ? '8~72자로 입력해 주세요' : undefined}
          />

          {fieldError ? <Note variant="sand">{fieldError}</Note> : null}
          {serverError ? <Note variant="sand">{serverError}</Note> : null}

          <Button type="submit" block disabled={submitting}>
            {submitting ? '처리하는 중이에요' : mode === 'login' ? '로그인' : '가입하기'}
          </Button>
        </form>
      </div>
    </main>
  )
}
