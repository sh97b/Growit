import { Link } from 'react-router-dom'
import { LandingAudienceBand } from './landing/LandingAudienceBand'
import { LandingCta } from './landing/LandingCta'
import { LandingExamples } from './landing/LandingExamples'
import { LandingFaq } from './landing/LandingFaq'
import { LandingFeatures } from './landing/LandingFeatures'
import { LandingFooter } from './landing/LandingFooter'
import { LandingHero } from './landing/LandingHero'
import { LandingHowTo } from './landing/LandingHowTo'
import { LandingPromise } from './landing/LandingPromise'
import { LandingWhy } from './landing/LandingWhy'
import { LandingHeader } from './landing/LandingHeader'

/*
 * N2: 비로그인 방문자를 위한 랜딩 페이지. AppShell(앱 껍데기)과 앱의 "한 화면 고정·문서 스크롤 금지"
 * 규칙을 쓰지 않는 독립 라우트다 — 문서가 세로로 스크롤되는 일반 페이지이고, 이 라우트에서만 그 규칙이
 * 적용되지 않는다(App.tsx의 ProtectedRoute가 비로그인 "/"에서만 AppLayout 없이 이 컴포넌트를 바로
 * 렌더링한다). 모든 글자·간격·크기는 랜딩 전용 부품(./landing/shared.tsx)의 리터럴 px로 쓴다.
 */
export default function LandingPage() {
  return (
    <div className="landing min-h-dvh bg-paper text-ink">
      <title>Growit — 쌓인 기록이, 다음 작은 걸음이 돼요</title>
      <meta
        name="description"
        content="매일 한 줄만 남기면 흐름이 보이고, AI가 기록을 근거로 달라진 점과 다음에 할 작은 일 하나를 알려 드리는 AI 성장 노트."
      />
      <LandingHeader />
      <main className="pb-[84px] lg:pb-0">
        <LandingHero />
        <LandingAudienceBand />
        <LandingWhy />
        <LandingFeatures />
        <LandingHowTo />
        <LandingPromise />
        <LandingExamples />
        <LandingFaq />
        <LandingCta />
      </main>
      <LandingFooter />

      <div className="fixed inset-x-0 bottom-0 z-[8] flex gap-[8px] border-t border-line bg-white p-[12px_20px_18px] lg:hidden">
        <Link
          to="/login"
          className="inline-flex min-h-[44px] flex-1 items-center justify-center rounded-[8px] border border-line-2 bg-white px-[20px] text-[15px] font-medium text-ink no-underline"
        >
          로그인
        </Link>
        <Link
          to="/signup"
          className="inline-flex min-h-[44px] flex-[2] items-center justify-center rounded-[8px] border border-transparent bg-base px-[20px] text-[15px] font-medium text-white no-underline"
        >
          무료로 시작하기
        </Link>
      </div>
    </div>
  )
}
