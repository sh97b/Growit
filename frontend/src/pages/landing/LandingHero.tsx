import { CheckCircleIcon, SparkleIcon } from '../../components/icons'
import { EvChipStatic, Eyebrow, LandingBtn, SproutIllustration, StaticHeatmap, WRAP } from './shared'

function PhoneMockup() {
  return (
    <div
      className="relative h-[600px] w-[292px] flex-none rounded-[46px] p-[9px]"
      style={{ background: 'var(--color-ink)', boxShadow: '0 18px 40px -18px rgba(43,40,35,0.28), 0 2px 6px rgba(43,40,35,0.06)' }}
    >
      <div
        className="absolute left-1/2 top-[10px] h-[24px] w-[84px] -translate-x-1/2 rounded-full"
        style={{ background: 'var(--color-ink)', zIndex: 3 }}
      />
      <div className="relative flex h-full w-full flex-col overflow-hidden rounded-[38px]" style={{ background: 'var(--color-paper)' }}>
        <div className="flex h-[44px] flex-none items-end justify-between px-[24px] pb-[4px] text-[12px] font-semibold">
          <span>9:41</span>
          <span>●●●</span>
        </div>
        <div className="flex flex-1 flex-col gap-[12px] px-[18px] pt-[8px]">
          <div className="flex flex-col gap-[2px]">
            <span className="text-[12px] text-ink-2">10월 28일 수요일</span>
            <span className="font-serif text-[19px] font-semibold leading-[1.45] text-ink">
              서현님, 오늘도
              <br />한 줄이면 충분해요
            </span>
          </div>
          <div className="rounded-[14px] bg-surface px-[12px] pb-[10px] pt-[12px]">
            <div className="mb-[6px] flex items-center justify-between">
              <span className="text-[12px] font-semibold text-ink">최근 7일</span>
              <span className="text-[12px] text-ink-2">5일 이어졌어요</span>
            </div>
            <div className="grid grid-cols-7 gap-[4px]">
              {[true, true, false, true, true, false, true].map((on, index) => (
                <span
                  key={index}
                  className="mx-auto h-[22px] w-[22px] rounded-full border-2"
                  style={{
                    background: on ? 'var(--color-base)' : 'transparent',
                    borderColor: on ? 'var(--color-base)' : 'var(--color-line-2)',
                    boxShadow: index === 6 ? '0 0 0 3px var(--color-paper), 0 0 0 5px var(--color-ink)' : undefined,
                  }}
                />
              ))}
            </div>
            <div className="mt-[4px] grid grid-cols-7 gap-[4px] text-center text-[10px] text-ink-3">
              {['목', '금', '토', '일', '월', '화', '수'].map((d) => (
                <span key={d}>{d}</span>
              ))}
            </div>
          </div>
          <div className="rounded-[14px] p-[14px]" style={{ background: 'color-mix(in srgb, var(--color-base) 9%, #fff)', border: '1px solid color-mix(in srgb, var(--color-base) 28%, #fff)' }}>
            <span className="text-[12px] font-semibold text-base-ink">다음 작은 행동 · 5분</span>
            <p className="my-[4px] font-serif text-[15px] font-semibold leading-[1.65] text-ink">
              메트로놈 70으로 올려서 C→G 전환을 열 번 해 보세요.
            </p>
            <EvChipStatic date="10/28" text="C에서 G로 바꾸는 걸…" maxWidth={120} />
          </div>
        </div>
        <div className="flex h-[58px] flex-none items-center justify-around border-t border-line bg-white px-[6px] text-[11px] text-ink-2">
          <span>홈</span>
          <span className="rounded-[10px] bg-base px-[12px] py-[6px] font-semibold text-white">기록하기</span>
          <span>돌아보기</span>
        </div>
      </div>
    </div>
  )
}

function CoachFloatCard({ className = '' }: { className?: string }) {
  return (
    <div className={['rounded-[16px] border border-line bg-white p-[16px_18px]', className].join(' ')}>
      <div className="flex flex-col gap-[10px]">
        <span className="flex items-center gap-[6px] text-[13px] font-semibold text-base-ink">
          <SparkleIcon width={16} height={16} />
          AI 회고 · 10월 14일–28일
        </span>
        <p className="font-serif text-[17px] font-semibold leading-[1.7] text-ink">
          10/24엔 박자가 끊겼는데, 오늘은 열 번 이어졌어요.
        </p>
        <div className="flex flex-wrap gap-[8px]">
          <EvChipStatic date="10/24" text="C에서 G로 넘어갈 때마다…" />
          <EvChipStatic date="10/28" text="C에서 G로 바꾸는 걸…" />
        </div>
      </div>
    </div>
  )
}

function HeatmapFloatCard() {
  return (
    <div className="w-[262px] rounded-[16px] border border-line bg-white p-[14px]">
      <div className="mb-[10px] flex items-center justify-between">
        <span className="text-[13px] font-semibold text-ink">최근 12주 활동 지도</span>
      </div>
      <StaticHeatmap cell={14} labelWidth={30} />
    </div>
  )
}

export function LandingHero() {
  return (
    <section className="overflow-hidden" id="top">
      <div className={[WRAP, 'flex flex-col gap-[20px] pb-[40px] pt-[32px] lg:flex-row lg:items-center lg:gap-[48px] lg:pb-[96px] lg:pt-[72px]'].join(' ')}>
        <div className="flex min-w-0 flex-col gap-[16px] lg:max-w-[600px] lg:flex-[1_1_0%] lg:gap-[24px]">
          <Eyebrow>
            <SparkleIcon width={14} height={14} />
            AI 성장 노트
          </Eyebrow>
          <h1 className="font-serif text-[32px] font-semibold leading-[1.38] tracking-[-0.03em] text-ink lg:text-[56px] lg:leading-[1.28]">
            쌓인 기록이,
            <br />
            다음 작은 걸음이
            <br />
            돼요
          </h1>
          <p className="text-[16px] leading-[1.75] text-ink-2 lg:max-w-[520px] lg:text-[19px]">
            새로운 걸 배우다 보면 변화가 눈에 보이기까지 시간이 걸려요. 매일 한 줄만 남기면 흐름이 보이고, AI가 내 기록을
            근거로 달라진 점과 다음에 할 작은 일 하나를 알려 드려요.
          </p>
          <div className="flex flex-col gap-[10px] lg:flex-row lg:gap-[12px]">
            <LandingBtn to="/signup" block className="lg:w-auto">
              무료로 시작하기
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M5 12h14M13 6l6 6-6 6" />
              </svg>
            </LandingBtn>
            <LandingBtn href="#why" variant="secondary" block className="lg:w-auto">
              서비스 둘러보기
            </LandingBtn>
          </div>
          <div className="flex flex-col gap-[4px] text-[14px] text-ink-2 lg:flex-row lg:flex-wrap lg:gap-[20px]">
            <span className="flex items-center gap-[6px]">
              <CheckCircleIcon width={16} height={16} />
              쉬는 날도 괜찮아요
            </span>
            <span className="flex items-center gap-[6px]">
              <CheckCircleIcon width={16} height={16} />
              문장마다 근거 기록
            </span>
            <span className="hidden items-center gap-[6px] lg:flex">
              <CheckCircleIcon width={16} height={16} />
              AI는 언제든 끌 수 있어요
            </span>
          </div>
        </div>

        {/* 데스크톱: 폰 목업 + 떠 있는 카드 두 개 + 새싹 */}
        <div className="relative hidden h-[660px] w-[640px] flex-none lg:block">
          <div className="absolute inset-[20px_0_0_40px] rounded-[40px] border border-[color-mix(in_srgb,var(--color-base)_28%,#fff)]" style={{ background: 'color-mix(in srgb, var(--color-base) 9%, #fff)' }} />
          <div className="absolute bottom-[6px] right-[30px]">
            <SproutIllustration width={190} />
          </div>
          <div className="absolute left-[70px] top-[34px]">
            <PhoneMockup />
          </div>
          <CoachFloatCard className="absolute right-0 top-[60px] w-[262px] shadow-[0_18px_40px_-18px_rgba(43,40,35,0.28),0_2px_6px_rgba(43,40,35,0.06)]" />
          <div className="absolute bottom-[170px] right-0 shadow-[0_18px_40px_-18px_rgba(43,40,35,0.28),0_2px_6px_rgba(43,40,35,0.06)]">
            <HeatmapFloatCard />
          </div>
        </div>

        {/* 모바일: 폰 목업 + AI 회고 카드 하나 */}
        <div className="relative mt-[8px] -mx-[20px] h-[640px] lg:hidden">
          <div className="absolute inset-[24px_20px_0_20px] rounded-[32px] border border-[color-mix(in_srgb,var(--color-base)_28%,#fff)]" style={{ background: 'color-mix(in srgb, var(--color-base) 9%, #fff)' }} />
          <div className="absolute left-1/2 top-[44px] -translate-x-1/2">
            <PhoneMockup />
          </div>
          <CoachFloatCard className="absolute bottom-[14px] left-[34px] right-[34px] shadow-[0_18px_40px_-18px_rgba(43,40,35,0.28),0_2px_6px_rgba(43,40,35,0.06)]" />
        </div>
      </div>
    </section>
  )
}
