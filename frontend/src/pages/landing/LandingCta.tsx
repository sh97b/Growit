import { LandingBtn, SECTION, SproutIllustration, WRAP } from './shared'

export function LandingCta() {
  return (
    <section className={[SECTION, 'pb-[56px] lg:pb-[96px]'].join(' ')}>
      <div className={WRAP}>
        <div
          className="relative flex flex-col gap-[14px] overflow-hidden rounded-[24px] bg-ink px-[24px] pb-0 pt-[36px] lg:flex-row lg:items-center lg:gap-[48px] lg:px-[80px] lg:py-[72px]"
        >
          <div className="flex min-w-0 flex-1 flex-col gap-[14px] lg:gap-[18px]">
            <h2 className="font-serif text-[26px] font-semibold leading-[1.42] tracking-[-0.025em] text-white lg:text-[38px] lg:leading-[1.38]">
              오늘 한 줄로,
              <br />
              첫 기록을 남겨 보세요
            </h2>
            <p className="text-[15px] leading-[1.75] text-[#D8D3C8] lg:text-[17px]">
              가입하고 분야 하나만 고르면 바로 시작할 수 있어요. 하루 빠져도 괜찮아요.
            </p>
            <div className="flex flex-col gap-[10px] lg:flex-row lg:gap-[12px]">
              <LandingBtn
                to="/signup"
                block
                className="bg-white text-ink lg:w-auto"
              >
                무료로 시작하기
              </LandingBtn>
              <LandingBtn
                to="/login"
                variant="secondary"
                block
                className="border-[#6B655A] bg-transparent text-white lg:w-auto"
              >
                로그인
              </LandingBtn>
            </div>
          </div>
          <div className="flex-none self-center opacity-95 lg:-mb-[34px]">
            <SproutIllustration width={200} />
          </div>
        </div>
      </div>
    </section>
  )
}
