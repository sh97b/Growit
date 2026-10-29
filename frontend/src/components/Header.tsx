import { useState } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import { NAV_ITEMS } from './nav-items'
import { DotsIcon, PlusIcon } from './icons'

export function Header() {
  const { logout } = useAuth()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)

  function handleLogout() {
    setMenuOpen(false)
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <header className="hidden h-[68px] flex-none border-b border-line bg-paper md:block">
      <div className="mx-auto flex h-full max-w-[1120px] items-center px-10">
        <Link to="/" className="mr-11 font-serif text-xl font-semibold text-ink no-underline">
          Growit
        </Link>
        <nav className="flex h-full gap-7">
          {NAV_ITEMS.map(({ to, label, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                [
                  'flex items-center border-b-2 text-[15px] font-medium no-underline',
                  isActive ? 'border-ink font-semibold text-ink' : 'border-transparent text-ink-2',
                ].join(' ')
              }
            >
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="relative ml-auto flex items-center gap-2">
          <Link
            to="/records/new"
            className="inline-flex min-h-11 items-center gap-2 rounded-lg bg-base px-5 text-[15px] font-medium text-white no-underline"
          >
            <PlusIcon width={18} height={18} />
            기록하기
          </Link>
          <button
            type="button"
            aria-label="설정 메뉴"
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((open) => !open)}
            className="flex h-11 min-w-11 items-center justify-center rounded-full border-0 bg-transparent text-ink"
          >
            <DotsIcon width={20} height={20} />
          </button>
          {menuOpen ? (
            <div className="absolute right-0 top-14 z-10 flex w-[212px] flex-col gap-0.5 rounded-xl bg-white p-1.5 shadow-[0_8px_24px_-8px_rgba(43,40,35,0.18),0_0_0_1px_var(--color-line)]">
              <Link
                to="/settings"
                onClick={() => setMenuOpen(false)}
                className="flex min-h-11 items-center gap-2.5 rounded-lg px-2.5 text-sm font-medium text-ink no-underline"
              >
                설정
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
      </div>
    </header>
  )
}
