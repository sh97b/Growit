import { useNavigate } from 'react-router-dom'
import { Button } from '../components/Button'
import { Note } from '../components/Note'
import { useAuth } from '../lib/auth'

export function SettingsPage() {
  const { logout } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="flex flex-col gap-6">
      <h1 className="font-serif text-xl font-semibold">설정</h1>
      <Note variant="calm">활동 분야 관리 같은 설정 화면은 아직 준비 중이에요.</Note>
      <Button type="button" variant="secondary" onClick={handleLogout} className="self-start">
        로그아웃
      </Button>
    </div>
  )
}
