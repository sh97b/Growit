import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { SettingsShell } from './PageLayout'
import { Button } from './Button'
import { ChevronLeftIcon, ChevronRightIcon } from './icons'
import { useCurrentUser } from '../hooks/useCurrentUser'
import { useAuth } from '../lib/auth'

type MenuItem = { to: string; label: string; requiresPassword?: boolean }

const MENU_ITEMS: MenuItem[] = [
  { to: '/settings/hobbies', label: '활동 분야 관리' },
  { to: '/settings/ai', label: 'AI 정리' },
  { to: '/settings/notifications', label: '알림 설정' },
  { to: '/settings/password', label: '비밀번호 바꾸기', requiresPassword: true },
  { to: '/settings/delete', label: '계정 삭제' },
  { to: '/privacy', label: '개인정보 안내' },
]

/**
 * 설정과 그 하위 화면(활동 분야 관리·AI 정리·알림 설정·비밀번호 바꾸기·계정 삭제·개인정보 안내)을
 * 하나의 쉘로 묶는다(LAYOUT-V2 4단계). 데스크톱은 왼쪽 메뉴(sticky)와 오른쪽 내용을 항상 같이 보여주고,
 * 모바일은 지금처럼 /settings가 메뉴 목록 화면이고 하위 경로가 내용 화면이다(둘 중 하나만 보임).
 */
export function SettingsLayout() {
  const location = useLocation()
  const navigate = useNavigate()
  const { logout } = useAuth()
  const { data: currentUser } = useCurrentUser()

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  const isMenuView = location.pathname === '/settings'
  const visibleItems = MENU_ITEMS.filter((item) => !item.requiresPassword || currentUser?.hasPassword !== false)

  const menu = (
    <nav aria-label="설정 메뉴" className="flex flex-col">
      {visibleItems.map((item) => {
        const active = location.pathname === item.to
        return (
          <Link
            key={item.to}
            to={item.to}
            aria-current={active ? 'page' : undefined}
            className={[
              // S1 D-6단계: 모바일 행 높이 52px, 오른쪽 › 아이콘.
              'flex min-h-[3.25rem] items-center justify-between gap-2 border-t border-line px-1 text-body-min text-ink no-underline last:border-b',
              'lg:min-h-11 lg:rounded-lg lg:border-t-0 lg:px-3 lg:last:border-b-0',
              active ? 'lg:bg-surface lg:font-semibold lg:text-ink' : 'lg:text-ink-2',
            ].join(' ')}
          >
            {item.label}
            <ChevronRightIcon width={16} height={16} className="flex-none text-ink-3 lg:hidden" />
          </Link>
        )
      })}
      <Button
        type="button"
        variant="secondary"
        onClick={handleLogout}
        className="mt-3 w-full lg:mt-5 lg:w-auto lg:self-start"
      >
        로그아웃
      </Button>
    </nav>
  )

  return (
    <div className="flex flex-col gap-5 lg:min-h-0 lg:flex-1">
      {/* Q1 3번: 모바일 하위 상세 화면은 큰 "설정" 제목 대신 작은 뒤로가기 링크만 두고,
          그 아래 각 화면(SettingsPage)이 자기 제목을 한 번만 그린다. 목록 화면은 그대로 큰 제목. */}
      {isMenuView ? (
        <h1 className="text-page-title lg:hidden">설정</h1>
      ) : (
        <Link
          to="/settings"
          className="inline-flex min-h-9 w-fit items-center gap-1 text-caption-min text-ink-2 no-underline lg:hidden"
        >
          <ChevronLeftIcon width={14} height={14} />
          설정
        </Link>
      )}
      <div className="lg:hidden">{isMenuView ? menu : <Outlet />}</div>
      <div className="hidden lg:block lg:min-h-0 lg:flex-1">
        <SettingsShell menu={menu} content={<Outlet />} />
      </div>
    </div>
  )
}
