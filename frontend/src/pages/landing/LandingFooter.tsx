import { Link } from 'react-router-dom'
import { WRAP } from './shared'
import growitLogo from '../../assets/brand/growit-logo.svg'

export function LandingFooter() {
  return (
    <footer className="border-t border-line py-[36px]">
      <div className={[WRAP, 'flex flex-col items-start gap-[16px] lg:flex-row lg:items-start lg:justify-between'].join(' ')}>
        <div className="flex flex-col gap-[6px]">
          <img src={growitLogo} alt="Growit" className="h-6 w-auto max-w-[8rem]" />
          <span className="t-cap text-ink-3">쌓인 기록을 읽고, 다음 작은 행동을 찾는 AI 성장 노트</span>
        </div>
        <nav className="flex flex-wrap items-center gap-[20px] t-cap">
          {/* 이용약관·문의는 실제 페이지가 없어 누를 수 없는 텍스트로 둔다(보고 대상). */}
          <span className="text-ink-3">이용약관</span>
          <Link to="/privacy" className="text-ink-3 no-underline">
            개인정보 처리방침
          </Link>
          <span className="text-ink-3">문의</span>
          <span className="text-ink-3">© 2026 Growit</span>
        </nav>
      </div>
    </footer>
  )
}
