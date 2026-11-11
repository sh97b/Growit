import { useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { AuthShell } from '../components/AuthShell'
import { Button } from '../components/Button'
import { Field } from '../components/Field'
import { Input } from '../components/Input'
import { LinkButton } from '../components/LinkButton'
import { Note } from '../components/Note'
import { PasswordField } from '../components/PasswordField'
import { SocialLoginButtons } from '../components/SocialLoginButtons'
import { ApiError } from '../lib/api'
import { useAuth } from '../lib/auth'

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fieldError, setFieldError] = useState<string | null>(null)
  const [serverError, setServerError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const { login } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const socialFailed = searchParams.get('error') === 'social_failed'

  function validate(): string | null {
    if (!EMAIL_PATTERN.test(email)) {
      return '올바른 이메일 형식으로 입력해 주세요'
    }
    if (password.length === 0) {
      return '비밀번호를 입력해 주세요'
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
      await login(email, password)
      navigate('/', { replace: true })
    } catch (error) {
      setServerError(error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthShell>
      <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
        <h1 className="font-serif text-xl font-semibold leading-[1.45] lg:text-[30px]">다시 만나서 반가워요</h1>

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
          autoComplete="current-password"
        />

        {socialFailed ? <Note variant="sand">소셜 로그인에 실패했어요. 다시 시도해 주세요</Note> : null}
        {fieldError ? <Note variant="sand">{fieldError}</Note> : null}
        {serverError ? <Note variant="sand">{serverError}</Note> : null}

        <Button type="submit" block disabled={submitting}>
          {submitting ? '처리하는 중이에요' : '로그인'}
        </Button>

        <div className="flex items-center justify-between">
          <span className="inline-flex min-h-11 items-center text-sm font-medium text-ink-3">비밀번호 찾기</span>
          <LinkButton to="/signup">회원가입</LinkButton>
        </div>

        <SocialLoginButtons />
      </form>
    </AuthShell>
  )
}
