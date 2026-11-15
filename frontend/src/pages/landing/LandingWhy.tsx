import { Eyebrow, Lead, SECTION, SectionTitle, StaticHeatmap, WRAP } from './shared'

export function LandingWhy() {
  return (
    <section id="why" className={[SECTION, 'scroll-mt-[72px] lg:scroll-mt-[88px]'].join(' ')}>
      <div className={[WRAP, 'flex flex-col gap-[48px]'].join(' ')}>
        <div className="flex flex-col gap-[14px] lg:max-w-[760px]">
          <Eyebrow>왜 만들었나요</Eyebrow>
          <SectionTitle>
            하루 빠졌다고,
            <br />
            흐름이 끊긴 건 아니에요
          </SectionTitle>
          <Lead>
            기존 습관 앱은 연속 일수와 달성률을 앞에 둬서, 쉬는 날이 생기면 오히려 부담이 돼요. Growit은 쉬어 간 날도
            이어 가는 과정의 일부로 보고, 기록의 내용으로 변화를 보여 드려요.
          </Lead>
        </div>

        <div className="flex flex-col gap-[16px] lg:flex-row lg:items-stretch lg:gap-[24px]">
          <div className="flex flex-1 flex-col gap-[12px] rounded-[16px] border border-line bg-surface p-[18px] lg:gap-[18px] lg:p-[24px]">
            <span className="text-[14px] font-semibold text-ink">일반적인 습관 앱</span>
            <div className="flex flex-col gap-[6px]">
              <span className="font-sans text-[36px] font-semibold leading-[1.1] tabular-nums text-ink-3 lg:text-[46px]">
                연속 0일
              </span>
              <span className="text-[14px] text-ink-2">하루 빠지면 숫자가 처음으로 돌아가요</span>
            </div>
            <div className="grid grid-cols-7 gap-[6px]">
              {[true, true, true, true, true, false, false].map((on, index) => (
                <span
                  key={index}
                  className="mx-auto h-[30px] w-[30px] rounded-full border-2"
                  style={{ background: on ? 'var(--color-line-2)' : 'transparent', borderColor: 'var(--color-line-2)' }}
                />
              ))}
            </div>
            <p className="text-[15px] text-ink-2">달성률과 연속 일수만 보여 줘서, 무엇이 달라졌는지는 알기 어려워요.</p>
          </div>

          <div
            className="flex flex-[1.25] flex-col gap-[12px] rounded-[16px] border p-[18px] lg:gap-[18px] lg:p-[24px]"
            style={{ borderColor: 'color-mix(in srgb, var(--color-base) 28%, #fff)', background: 'color-mix(in srgb, var(--color-base) 9%, #fff)' }}
          >
            <span className="text-[14px] font-semibold text-base-ink">Growit</span>
            <div className="flex flex-col gap-[6px]">
              <span className="font-serif text-[22px] font-semibold leading-[1.4] text-ink lg:text-[28px]">
                쉬어 간 날도 흐름의 일부예요
              </span>
              <span className="text-[14px] text-ink-2">빨강도, X 표시도, 연속 일수도 없어요</span>
            </div>
            <div className="rounded-[12px] bg-white p-[12px] lg:p-[14px]">
              <StaticHeatmap cell={24} labelWidth={40} />
            </div>
            <p className="text-[15px] text-ink">기록한 날이 쌓이는 모양으로 꾸준함을 보여 드려요.</p>
          </div>
        </div>
      </div>
    </section>
  )
}
