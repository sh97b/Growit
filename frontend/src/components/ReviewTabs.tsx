import { NavLink } from 'react-router-dom'

const TABS = [
  { to: '/records', label: '기록' },
  { to: '/changes', label: '변화' },
  { to: '/coach', label: '회고' },
] as const

export function ReviewTabs() {
  return (
    <div className="flex gap-0.5 rounded-lg border border-line-2 bg-white p-[3px]" role="group" aria-label="돌아보기">
      {TABS.map(({ to, label }) => (
        <NavLink
          key={to}
          to={to}
          className={({ isActive }) =>
            [
              'flex min-h-10 flex-1 items-center justify-center rounded-md text-sm font-medium no-underline',
              isActive ? 'bg-ink text-white' : 'text-ink-2',
            ].join(' ')
          }
        >
          {label}
        </NavLink>
      ))}
    </div>
  )
}
