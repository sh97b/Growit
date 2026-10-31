import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { AuthShell } from '../components/AuthShell'
import { Button } from '../components/Button'
import { Field } from '../components/Field'
import { Input } from '../components/Input'
import { LinkButton } from '../components/LinkButton'
import { Note } from '../components/Note'
import { PasswordField } from '../components/PasswordField'
import { ApiError } from '../lib/api'
import { useAuth } from '../lib/auth'

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function SignupPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fieldError, setFieldError] = useState<string | null>(null)
  const [serverError, setServerError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const { signup } = useAuth()
  const navigate = useNavigate()

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
      await signup(email, password)
      navigate('/onboarding', { replace: true })
    } catch (error) {
      setServerError(error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthShell>
      <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
        <h1 className="font-serif text-xl font-semibold leading-[1.45] lg:text-[30px]">첫 기록을 시작해 볼까요</h1>

        <Field label="이메일" htmlFor="signup-email">
          <Input
            id="signup-email"
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            autoComplete="email"
            placeholder="name@example.com"
          />
        </Field>

        <PasswordField
          id="signup-password"
          label="비밀번호"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          autoComplete="new-password"
          hint="8~72자로 입력해 주세요"
        />

        {fieldError ? <Note variant="sand">{fieldError}</Note> : null}
        {serverError ? <Note variant="sand">{serverError}</Note> : null}

        <Button type="submit" block disabled={submitting}>
          {submitting ? '처리하는 중이에요' : '가입하기'}
        </Button>

        <p className="text-center text-sm text-ink-2">
          이미 계정이 있나요? <LinkButton to="/login">로그인</LinkButton>
        </p>
      </form>
    </AuthShell>
  )
}
