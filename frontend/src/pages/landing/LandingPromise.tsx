import { CheckIcon, PencilIcon, RestIcon, SparkleIcon } from '../../components/icons'
import { Eyebrow, IconBadge, LandingCard, Lead, SECTION, SectionTitle, WRAP } from './shared'

const CARDS = [
  {
    icon: <RestIcon width={22} height={22} />,
    title: '쉬어 간 날은 실패가 아니에요',
    lead: '빨강도 X 표시도 없고, 연속 일수를 강조하지 않아요.',
  },
  {
    icon: <SparkleIcon width={22} height={22} />,
    title: '문장마다 근거가 붙어요',
    lead: 'AI가 한 말에는 항상 날짜 칩이 붙어서, 어떤 기록을 읽었는지 바로 확인할 수 있어요.',
  },
  {
    icon: <CheckIcon width={22} height={22} />,
    title: '다음 행동은 작게, 하나만',
    lead: '5~10분 안에 끝나는 일 하나만 제안해요.',
  },
  {
    icon: <PencilIcon width={22} height={22} />,
    title: '내가 쓴 글이 주인공이에요',
    lead: '내 글은 세리프체로 크게, 숫자는 작고 조용하게 보여 줘요.',
  },
]

function StaticSwitch() {
  return (
    <span className="relative inline-block h-[28px] w-[48px] flex-none rounded-full bg-base" aria-hidden="true">
      <span className="absolute right-[3px] top-[3px] h-[22px] w-[22px] rounded-full bg-white" />
    </span>
  )
}

export function LandingPromise() {
  return (
    <section className={[SECTION, 'border-t border-line bg-surface'].join(' ')}>
      <div className={[WRAP, 'flex flex-col gap-[32px] lg:flex-row lg:items-start lg:gap-[64px]'].join(' ')}>
        <div className="flex flex-col gap-[14px] lg:w-[400px] lg:flex-none">
          <Eyebrow>Growit의 약속</Eyebrow>
          <SectionTitle>
            부담은 덜고,
            <br />
            근거는 더해요
          </SectionTitle>
          <Lead>
            AI 정리는 설정에서 언제든 끌 수 있어요. 끄면 기록과 활동 지도는 그대로 쓰고, 내 기록이 AI 정리와 회고에 쓰이는
            방식도 설정에서 확인할 수 있어요.
          </Lead>
          <div className="mt-[6px] flex items-center gap-[12px]">
            <StaticSwitch />
            <span className="t-body text-ink">AI 정리 켜기 / 끄기</span>
          </div>
        </div>

        <div className="grid flex-1 grid-cols-1 gap-[14px] lg:grid-cols-2 lg:gap-[20px]">
          {CARDS.map((card) => (
            <LandingCard key={card.title} className="flex flex-row items-start gap-[14px] lg:flex-col lg:gap-[12px] lg:p-[28px]">
              <IconBadge>{card.icon}</IconBadge>
              <div className="flex min-w-0 flex-col gap-[2px] lg:gap-0">
                <h3 className="font-serif t-body-lg font-semibold leading-[1.5] text-ink">{card.title}</h3>
                <p className="text-[14px] leading-[1.6] text-ink-2 lg:text-[15px]">{card.lead}</p>
              </div>
            </LandingCard>
          ))}
        </div>
      </div>
    </section>
  )
}
