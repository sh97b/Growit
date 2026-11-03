import { Link, useNavigate } from 'react-router-dom'
import { Button } from '../components/Button'
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
      <div className="flex flex-col">
        <Link
          to="/settings/hobbies"
          className="flex min-h-11 items-center border-t border-line py-3 text-ink no-underline"
        >
          활동 분야 관리
        </Link>
        <Link
          to="/settings/ai"
          className="flex min-h-11 items-center border-t border-line py-3 text-ink no-underline last:border-b"
        >
          AI 정리
        </Link>
      </div>
      <Button type="button" variant="secondary" onClick={handleLogout} className="self-start">
        로그아웃
      </Button>
    </div>
  )
}
