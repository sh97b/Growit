import { Link, Outlet, useLocation } from 'react-router-dom'
import { ReviewTabs } from './ReviewTabs'

const SECONDARY_LINKS: Record<string, { to: string; label: string }> = {
  '/changes': { to: '/changes/metrics', label: '변화 지표 상세 보기' },
  '/coach': { to: '/coach/history', label: '이전 회고 목록' },
}

export function ReviewSectionLayout() {
  const location = useLocation()
  const secondaryLink = SECONDARY_LINKS[location.pathname]

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col items-end gap-2 lg:flex-row lg:items-center">
        <ReviewTabs />
        {secondaryLink ? (
          <Link
            to={secondaryLink.to}
            className="inline-flex min-h-11 items-center whitespace-nowrap text-sm font-medium text-base-ink no-underline underline underline-offset-4 lg:ml-auto"
          >
            {secondaryLink.label}
          </Link>
        ) : null}
      </div>
      <Outlet />
    </div>
  )
}
