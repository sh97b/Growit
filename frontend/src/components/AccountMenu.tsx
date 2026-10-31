import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import { useCurrentUser } from '../hooks/useCurrentUser'

export function AccountMenu({ size = 40 }: { size?: number }) {
  const { logout } = useAuth()
  const navigate = useNavigate()
  const { data: currentUser } = useCurrentUser()
  const [open, setOpen] = useState(false)
  const initial = (currentUser?.displayName || currentUser?.email || '?').trim().charAt(0).toUpperCase()

  function handleLogout() {
    setOpen(false)
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="relative">
      <button
        type="button"
        aria-label="내 계정 메뉴"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
        style={{ width: size, height: size }}
        className="flex items-center justify-center rounded-full border border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_9%,white)] text-sm font-semibold text-base-ink"
      >
        {initial}
      </button>
      {open ? (
        <div className="absolute right-0 top-full z-10 mt-2 flex w-[212px] flex-col gap-0.5 rounded-xl bg-white p-1.5 shadow-[0_8px_24px_-8px_rgba(43,40,35,0.18),0_0_0_1px_var(--color-line)]">
          <Link
            to="/settings"
            onClick={() => setOpen(false)}
            className="flex min-h-11 items-center gap-2.5 rounded-lg px-2.5 text-sm font-medium text-ink no-underline"
          >
            설정
          </Link>
          <Link
            to="/settings/hobbies"
            onClick={() => setOpen(false)}
            className="flex min-h-11 items-center gap-2.5 rounded-lg px-2.5 text-sm font-medium text-ink no-underline"
          >
            활동 분야 관리
          </Link>
          <button
            type="button"
            onClick={handleLogout}
            className="flex min-h-11 items-center gap-2.5 rounded-lg border-0 bg-transparent px-2.5 text-left text-sm font-medium text-ink"
          >
            로그아웃
          </button>
        </div>
      ) : null}
    </div>
  )
}
