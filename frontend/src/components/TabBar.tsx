import { NavLink } from 'react-router-dom'
import { NAV_ITEMS } from './nav-items'
import { PlusIcon } from './icons'

export function TabBar() {
  return (
    <nav
      aria-label="하단 메뉴"
      className="grid flex-none grid-cols-5 border-t border-line bg-white md:hidden"
      style={{ height: 64 }}
    >
      {NAV_ITEMS.slice(0, 2).map(({ to, shortLabel, icon: ItemIcon, end }) => (
        <NavLink
          key={to}
          to={to}
          end={end}
          className="flex flex-col items-center justify-center gap-0.5 text-[13px] font-medium text-ink no-underline"
        >
          {({ isActive }) => (
            <>
              <span
                className={[
                  'flex h-7 w-12 items-center justify-center rounded-full',
                  isActive ? 'bg-[color-mix(in_srgb,var(--color-base)_16%,white)]' : '',
                ].join(' ')}
              >
                <ItemIcon width={22} height={22} />
              </span>
              <span className={isActive ? 'font-semibold' : ''}>{shortLabel}</span>
            </>
          )}
        </NavLink>
      ))}
      <NavLink
        to="/records/new"
        className="flex flex-col items-center justify-center gap-0.5 text-[13px] font-semibold text-ink no-underline"
      >
        <span className="flex h-[34px] w-14 items-center justify-center rounded-xl bg-base text-white">
          <PlusIcon width={24} height={24} />
        </span>
        기록하기
      </NavLink>
      {NAV_ITEMS.slice(2).map(({ to, shortLabel, icon: ItemIcon, end }) => (
        <NavLink
          key={to}
          to={to}
          end={end}
          className="flex flex-col items-center justify-center gap-0.5 text-[13px] font-medium text-ink no-underline"
        >
          {({ isActive }) => (
            <>
              <span
                className={[
                  'flex h-7 w-12 items-center justify-center rounded-full',
                  isActive ? 'bg-[color-mix(in_srgb,var(--color-base)_16%,white)]' : '',
                ].join(' ')}
              >
                <ItemIcon width={22} height={22} />
              </span>
              <span className={isActive ? 'font-semibold' : ''}>{shortLabel}</span>
            </>
          )}
        </NavLink>
      ))}
    </nav>
  )
}
