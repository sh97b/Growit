import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { Button } from '../../components/Button'
import { Note } from '../../components/Note'
import { PasswordField } from '../../components/PasswordField'
import { ApiError } from '../../lib/api'
import { useAuth } from '../../lib/auth'
import { resetSessionState } from '../../lib/sessionReset'
import { deleteAccount } from '../../lib/users'

export function DeleteAccountPage() {
  const navigate = useNavigate()
  const { logout } = useAuth()
  const [password, setPassword] = useState('')
  const [confirming, setConfirming] = useState(false)

  const mutation = useMutation({
    mutationFn: deleteAccount,
    onSuccess: () => {
      resetSessionState(null)
      logout()
      navigate('/login', { replace: true })
    },
    onError: () => setConfirming(false),
  })

  function handleFirstSubmit(event: FormEvent) {
    event.preventDefault()
    if (!password) return
    setConfirming(true)
  }

  function handleConfirm() {
    mutation.mutate(password)
  }

  return (
    <div className="flex flex-col gap-5 lg:mx-auto lg:max-w-[720px]">
      <nav aria-label="현재 위치" className="hidden items-center gap-1.5 text-[13px] text-ink-3 lg:flex">
        <span>설정</span>
        <span aria-hidden="true">›</span>
        <span>계정 삭제</span>
      </nav>
      <h1 className="font-serif text-xl font-semibold lg:text-[30px]">계정 삭제</h1>

      <Note variant="sand">
        계정을 삭제하면 활동 분야, 기록, AI 정리·변화·회고 결과가 모두 사라져요. 되돌릴 수 없어요.
      </Note>

      {!confirming ? (
        <form className="flex flex-col gap-4" onSubmit={handleFirstSubmit} noValidate>
          <PasswordField
            id="delete-password"
            label="비밀번호"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="current-password"
          />
          <Button type="submit" variant="secondary" block disabled={!password}>
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
