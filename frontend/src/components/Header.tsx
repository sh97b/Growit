import { Link, useLocation } from 'react-router-dom'
import { AccountMenu } from './AccountMenu'
import { isReviewPath } from './nav-items'
import { PlusIcon } from './icons'

export function Header() {
  const location = useLocation()

  const homeActive = location.pathname === '/'
  const reviewActive = isReviewPath(location.pathname)

  return (
    <header className="hidden h-[68px] flex-none border-b border-line bg-paper lg:block">
      <div className="mx-auto flex h-full w-full max-w-[1200px] items-center px-10">
        <Link to="/" className="mr-11 font-serif text-xl font-semibold text-ink no-underline">
          Growit
        </Link>
        <nav className="flex h-full gap-7" aria-label="주 메뉴">
          <Link
            to="/"
            aria-current={homeActive ? 'page' : undefined}
            className={[
              'flex items-center border-b-2 text-[15px] font-medium no-underline',
              homeActive ? 'border-ink font-semibold text-ink' : 'border-transparent text-ink-2',
            ].join(' ')}
          >
            홈
          </Link>
          <Link
            to="/records"
            aria-current={reviewActive ? 'page' : undefined}
            className={[
              'flex items-center border-b-2 text-[15px] font-medium no-underline',
              reviewActive ? 'border-ink font-semibold text-ink' : 'border-transparent text-ink-2',
            ].join(' ')}
          >
            돌아보기
          </Link>
        </nav>
        <div className="ml-auto flex items-center gap-2">
          <Link
            to="/records/new"
            className="inline-flex min-h-10 items-center gap-2 rounded-lg bg-base px-4 text-sm font-medium text-white no-underline"
          >
            <PlusIcon width={16} height={16} />
            기록하기
          </Link>
          <AccountMenu size={40} />
        </div>
      </div>
    </header>
  )
}
