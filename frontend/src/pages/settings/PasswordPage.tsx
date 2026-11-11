import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Button } from '../../components/Button'
import { Note } from '../../components/Note'
import { PasswordChecklist } from '../../components/PasswordChecklist'
import { PasswordField } from '../../components/PasswordField'
import { ApiError } from '../../lib/api'
import { useAuth } from '../../lib/auth'
import { PASSWORD_HINT } from '../../lib/passwordRules'
import { changePassword } from '../../lib/users'

export function PasswordPage() {
  const { setSessionToken } = useAuth()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [fieldError, setFieldError] = useState<string | null>(null)
  const [done, setDone] = useState(false)

  const mutation = useMutation({
    mutationFn: changePassword,
    onSuccess: (response) => {
      setSessionToken(response.accessToken)
      setDone(true)
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
    },
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setDone(false)
    setFieldError(null)
    if (newPassword !== confirmPassword) {
      setFieldError('새 비밀번호가 서로 달라요')
      return
    }
    mutation.mutate({ currentPassword, newPassword })
  }

  return (
    <div className="flex flex-col gap-5 lg:mx-auto lg:max-w-[720px]">
      <nav aria-label="현재 위치" className="hidden items-center gap-1.5 text-[13px] text-ink-3 lg:flex">
        <span>설정</span>
        <span aria-hidden="true">›</span>
        <span>비밀번호 바꾸기</span>
      </nav>
      <h1 className="text-page-title">비밀번호 바꾸기</h1>

      {done ? <Note variant="calm">바꿨어요</Note> : null}

      <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
        <PasswordField
          id="current-password"
          label="현재 비밀번호"
          value={currentPassword}
          onChange={(event) => setCurrentPassword(event.target.value)}
          autoComplete="current-password"
        />

        <div className="flex flex-col gap-1.5">
          <PasswordField
            id="new-password"
            label="새 비밀번호"
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
            autoComplete="new-password"
            hint={PASSWORD_HINT}
          />
          <PasswordChecklist password={newPassword} />
        </div>

        <PasswordField
          id="confirm-password"
          label="새 비밀번호 확인"
          value={confirmPassword}
          onChange={(event) => setConfirmPassword(event.target.value)}
          autoComplete="new-password"
        />

        {fieldError ? <Note variant="sand">{fieldError}</Note> : null}
        {mutation.isError ? (
          <Note variant="sand">
            {mutation.error instanceof ApiError ? mutation.error.message : '잠시 후 다시 시도해 주세요'}
          </Note>
        ) : null}

        <Button type="submit" block disabled={mutation.isPending}>
          {mutation.isPending ? '바꾸는 중이에요' : '바꾸기'}
        </Button>
      </form>
    </div>
  )
}
