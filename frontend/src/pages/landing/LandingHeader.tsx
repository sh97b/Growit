import { Link } from 'react-router-dom'
import { WRAP } from './shared'

const NAV_LINKS = [
  { href: '#why', label: '소개' },
  { href: '#features', label: '기능' },
  { href: '#howto', label: '이용 방법' },
  { href: '#faq', label: '자주 묻는 질문' },
]

export function LandingHeader() {
  return (
    <header className="sticky top-0 z-[9] border-b border-line bg-[color-mix(in_srgb,var(--color-paper)_94%,transparent)]">
      <div className={[WRAP, 'flex h-[56px] items-center gap-1 lg:h-[72px]'].join(' ')}>
        <Link to="/" className="mr-auto inline-flex items-center font-serif text-[20px] font-semibold text-ink no-underline lg:mr-[36px] lg:text-[22px]">
          Growit
        </Link>
        <nav className="hidden items-center gap-[6px] lg:flex" aria-label="랜딩 섹션 이동">
          {NAV_LINKS.map((link) => (
            <a
              key={link.href}
              href={link.href}
              className="inline-flex min-h-[44px] items-center px-[14px] text-[15px] font-medium text-ink-2 no-underline"
            >
              {link.label}
            </a>
          ))}
        </nav>
        <div className="ml-0 flex items-center gap-2 lg:ml-auto">
          <Link
            to="/login"
            className="hidden min-h-[40px] items-center rounded-[8px] border border-line-2 bg-white px-[16px] text-[14px] font-medium text-ink no-underline lg:inline-flex"
          >
            로그인
          </Link>
          <Link
            to="/login"
            className="inline-flex min-h-[44px] items-center rounded-[8px] px-[12px] text-[14px] font-medium text-ink no-underline lg:hidden"
          >
            로그인
          </Link>
          <Link
            to="/signup"
            className="hidden min-h-[40px] items-center rounded-[8px] border border-transparent bg-base px-[16px] text-[14px] font-medium text-white no-underline lg:inline-flex"
          >
            무료로 시작하기
          </Link>
          <Link
            to="/signup"
            className="inline-flex min-h-[44px] items-center rounded-[8px] border border-transparent bg-base px-[14px] text-[14px] font-medium text-white no-underline lg:hidden"
          >
            시작하기
          </Link>
        </div>
      </div>
    </header>
  )
}
