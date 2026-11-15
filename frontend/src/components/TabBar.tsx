import { Link, useLocation } from 'react-router-dom'
import { isReviewPath } from './nav-items'
import { HomeIcon, ListIcon, SettingsIcon } from './icons'

/**
 * S1 C-2단계: 탭 3개(홈/돌아보기/설정)로 바꾼다 — 가운데의 큰 "기록하기" 알약은 삭제하고
 * 그 자리의 기록 만들기는 모든 화면에 뜨는 FAB(RecordFab)가 대신한다. 설정 탭은 하위 설정
 * 화면(/settings/*)에서도 강조된다.
 */
export function TabBar() {
  const location = useLocation()
  const homeActive = location.pathname === '/'
  const reviewActive = isReviewPath(location.pathname)
  const settingsActive = location.pathname.startsWith('/settings')

  return (
    <nav
      aria-label="하단 메뉴"
      className="grid flex-none grid-cols-3 border-t border-line bg-white lg:hidden"
      style={{ height: '3.5rem', paddingBottom: 'env(safe-area-inset-bottom)' }}
    >
      <Link
        to="/"
        aria-current={homeActive ? 'page' : undefined}
        className="flex flex-col items-center justify-center gap-0.5 text-caption-min font-medium text-ink no-underline"
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
        to="/records"
        aria-current={reviewActive ? 'page' : undefined}
        className="flex flex-col items-center justify-center gap-0.5 text-caption-min font-medium text-ink no-underline"
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

      <Link
        to="/settings"
        aria-current={settingsActive ? 'page' : undefined}
        className="flex flex-col items-center justify-center gap-0.5 text-caption-min font-medium text-ink no-underline"
      >
        <span
          className={[
            'flex h-7 w-12 items-center justify-center rounded-full',
            settingsActive ? 'bg-[color-mix(in_srgb,var(--color-base)_16%,white)]' : '',
          ].join(' ')}
        >
          <SettingsIcon width={22} height={22} />
        </span>
        <span className={settingsActive ? 'font-semibold' : ''}>설정</span>
      </Link>
    </nav>
  )
}
