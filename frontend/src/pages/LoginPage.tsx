import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '../components/Button'
import { Field } from '../components/Field'
import { Footer } from '../components/Footer'
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
        navigate('/', { replace: true })
      } else {
        await signup(email, password)
        navigate('/onboarding', { replace: true })
      }
    } catch (error) {
      setServerError(error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요')
    } finally {
      setSubmitting(false)
    }
  }

  const authForm = (
    <>
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
        <h1 className="font-serif text-xl font-semibold leading-[1.45] lg:text-[30px]">
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
    </>
  )

  return (
    <div className="flex min-h-dvh flex-col bg-paper">
      <header className="hidden h-[68px] flex-none items-center border-b border-line bg-paper lg:flex">
        <div className="mx-auto w-full max-w-[1200px] px-10">
          <span className="font-serif text-xl font-semibold text-ink">Growit</span>
        </div>
      </header>

      <main className="flex flex-1 flex-col lg:items-center lg:justify-center">
        <div className="mx-auto flex w-full max-w-[420px] flex-1 flex-col gap-6 px-5 py-8 lg:hidden">
          <span className="font-serif text-xl font-semibold text-ink">Growit</span>
          {authForm}
        </div>

        <div className="mx-auto hidden w-full max-w-[1200px] grid-cols-[1fr_380px] items-center gap-[140px] px-10 py-16 lg:grid">
          <div className="flex max-w-[440px] flex-col gap-4">
            <p className="font-serif text-[36px] font-semibold leading-[1.3]">
              꾸준함이 눈에 보이고, 내가 쓴 말이 먼저 읽히는 곳
            </p>
            <p className="text-[17px] leading-[1.75] text-ink-2">
              쉬어 간 날도 흐름의 일부예요. 오늘 한 줄이면 충분해요.
            </p>
          </div>
          <div className="flex flex-col gap-4">{authForm}</div>
        </div>
      </main>

      <Footer />
    </div>
  )
}
