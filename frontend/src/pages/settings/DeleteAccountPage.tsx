import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { Button } from '../../components/Button'
import { Field } from '../../components/Field'
import { Input } from '../../components/Input'
import { Note } from '../../components/Note'
import { PasswordField } from '../../components/PasswordField'
import { useCurrentUser } from '../../hooks/useCurrentUser'
import { ApiError } from '../../lib/api'
import { useAuth } from '../../lib/auth'
import { resetSessionState } from '../../lib/sessionReset'
import { deleteAccount, deleteSocialAccount } from '../../lib/users'

const CONFIRMATION_TEXT = '계정 삭제'

export function DeleteAccountPage() {
  const navigate = useNavigate()
  const { logout } = useAuth()
  const { data: currentUser } = useCurrentUser()
  const isSocialAccount = currentUser?.hasPassword === false
  const [password, setPassword] = useState('')
  const [confirmationInput, setConfirmationInput] = useState('')
  const [confirming, setConfirming] = useState(false)

  const mutation = useMutation({
    mutationFn: () => (isSocialAccount ? deleteSocialAccount(confirmationInput) : deleteAccount(password)),
    onSuccess: () => {
      resetSessionState(null)
      logout()
      navigate('/login', { replace: true })
    },
    onError: () => setConfirming(false),
  })

  const readyForFirstSubmit = isSocialAccount ? confirmationInput === CONFIRMATION_TEXT : Boolean(password)

  function handleFirstSubmit(event: FormEvent) {
    event.preventDefault()
    if (!readyForFirstSubmit) return
    setConfirming(true)
  }

  function handleConfirm() {
    mutation.mutate()
  }

  return (
    <div className="flex flex-col gap-5 lg:mx-auto lg:max-w-[720px]">
      <nav aria-label="현재 위치" className="hidden items-center gap-1.5 text-[13px] text-ink-3 lg:flex">
        <span>설정</span>
        <span aria-hidden="true">›</span>
        <span>계정 삭제</span>
      </nav>
      <h1 className="text-page-title">계정 삭제</h1>

      <Note variant="sand">
        계정을 삭제하면 활동 분야, 기록, AI 정리·변화·회고 결과가 모두 사라져요. 되돌릴 수 없어요.
      </Note>

      {!confirming ? (
        <form className="flex flex-col gap-4" onSubmit={handleFirstSubmit} noValidate>
          {isSocialAccount ? (
            <Field label={`확인을 위해 "${CONFIRMATION_TEXT}"를 입력해 주세요`} htmlFor="delete-confirmation">
              <Input
                id="delete-confirmation"
                value={confirmationInput}
                onChange={(event) => setConfirmationInput(event.target.value)}
              />
            </Field>
          ) : (
            <PasswordField
              id="delete-password"
              label="비밀번호"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="current-password"
            />
          )}
          <Button type="submit" variant="secondary" block disabled={!readyForFirstSubmit}>
            계정 삭제 계속하기
          </Button>
        </form>
      ) : (
        <div className="flex flex-col gap-3">
          <Note variant="sand">
            <div className="flex flex-col gap-3">
              <p>정말로 계정을 삭제할까요? 지운 뒤에는 되돌릴 수 없어요.</p>
              <div className="flex gap-2">
                <Button type="button" variant="secondary" size="sm" onClick={() => setConfirming(false)}>
                  취소
                </Button>
                <Button type="button" size="sm" disabled={mutation.isPending} onClick={handleConfirm}>
                  {mutation.isPending ? '삭제하는 중이에요' : '삭제하기'}
                </Button>
              </div>
            </div>
          </Note>
        </div>
      )}

      {mutation.isError ? (
        <Note variant="sand">
          {mutation.error instanceof ApiError ? mutation.error.message : '잠시 후 다시 시도해 주세요'}
        </Note>
      ) : null}
    </div>
  )
}
