import { Link, useLocation } from 'react-router-dom'
import { AccountMenu } from './AccountMenu'
import { isGrowthPath, isReviewPath, isRoadmapPath } from './nav-items'
import { PlusIcon } from './icons'
import growitLogo from '../assets/brand/growit-logo.svg'

export function Header() {
  const location = useLocation()

  const homeActive = location.pathname === '/'
  const reviewActive = isReviewPath(location.pathname)
  const roadmapActive = isRoadmapPath(location.pathname)
  const growthActive = isGrowthPath(location.pathname)

  return (
    <header className="hidden h-[4.25rem] flex-none border-b border-line bg-paper lg:block">
      <div className="app-shell-container flex h-full items-center">
        <Link to="/" className="mr-11 inline-flex items-center no-underline">
          <img src={growitLogo} alt="Growit" className="h-7 w-auto max-w-[9rem]" />
        </Link>
        <nav className="flex h-full gap-7" aria-label="주 메뉴">
          <Link
            to="/"
            aria-current={homeActive ? 'page' : undefined}
            className={[
              'flex items-center border-b-2 text-body-min font-medium no-underline',
              homeActive ? 'border-ink font-semibold text-ink' : 'border-transparent text-ink-2',
            ].join(' ')}
          >
            홈
          </Link>
          <Link
            to="/roadmap"
            aria-current={roadmapActive ? 'page' : undefined}
            className={[
              'flex items-center border-b-2 text-body-min font-medium no-underline',
              roadmapActive ? 'border-ink font-semibold text-ink' : 'border-transparent text-ink-2',
            ].join(' ')}
          >
            로드맵
          </Link>
          <Link
            to="/records"
            aria-current={reviewActive ? 'page' : undefined}
            className={[
              'flex items-center border-b-2 text-body-min font-medium no-underline',
              reviewActive ? 'border-ink font-semibold text-ink' : 'border-transparent text-ink-2',
            ].join(' ')}
          >
            돌아보기
          </Link>
          <Link
            to="/growth"
            aria-current={growthActive ? 'page' : undefined}
            className={[
              'flex items-center border-b-2 text-body-min font-medium no-underline',
              growthActive ? 'border-ink font-semibold text-ink' : 'border-transparent text-ink-2',
            ].join(' ')}
          >
            성장
          </Link>
        </nav>
        <div className="ml-auto flex items-center gap-2">
          <Link
            to="/records/new"
            className="inline-flex min-h-10 items-center gap-2 rounded-lg bg-base px-4 t-sub font-medium text-white no-underline"
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
