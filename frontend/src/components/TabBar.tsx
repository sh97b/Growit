import { Link, useLocation } from 'react-router-dom'
import { isReviewPath } from './nav-items'
import { HomeIcon, ListIcon, PlusIcon } from './icons'

export function TabBar() {
  const location = useLocation()
  const homeActive = location.pathname === '/'
  const reviewActive = isReviewPath(location.pathname)

  return (
    <nav
      aria-label="하단 메뉴"
      className="grid flex-none grid-cols-3 border-t border-line bg-white lg:hidden"
      style={{ height: 64 }}
    >
      <Link
        to="/"
        aria-current={homeActive ? 'page' : undefined}
        className="flex flex-col items-center justify-center gap-0.5 text-[13px] font-medium text-ink no-underline"
      >
        <span
          className={[
            'flex h-7 w-12 items-center justify-center rounded-full',
            homeActive ? 'bg-[color-mix(in_srgb,var(--color-base)_16%,white)]' : '',
          ].join(' ')}
        >
          <HomeIcon width={22} height={22} />
        </span>
        <span className={homeActive ? 'font-semibold' : ''}>홈</span>
      </Link>

      <Link
        to="/records/new"
        className="flex flex-col items-center justify-center gap-0.5 text-[13px] font-semibold text-ink no-underline"
      >
        <span className="flex h-[34px] w-14 items-center justify-center rounded-xl bg-base text-white">
          <PlusIcon width={24} height={24} />
        </span>
        기록하기
      </Link>

      <Link
        to="/records"
        aria-current={reviewActive ? 'page' : undefined}
        className="flex flex-col items-center justify-center gap-0.5 text-[13px] font-medium text-ink no-underline"
      >
        <span
          className={[
            'flex h-7 w-12 items-center justify-center rounded-full',
            reviewActive ? 'bg-[color-mix(in_srgb,var(--color-base)_16%,white)]' : '',
          ].join(' ')}
        >
          <ListIcon width={22} height={22} />
        </span>
        <span className={reviewActive ? 'font-semibold' : ''}>돌아보기</span>
      </Link>
    </nav>
  )
}
