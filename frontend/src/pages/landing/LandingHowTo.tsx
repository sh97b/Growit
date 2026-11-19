import { CheckIcon, PlusIcon, SparkleIcon } from '../../components/icons'
import { Eyebrow, IconBadge, LandingCard, SECTION, SectionTitle, StepBadge, Tag, WRAP } from './shared'

const STEPS = [
  {
    icon: <PlusIcon width={22} height={22} />,
    title: '기록해요',
    lead: '날짜, 분야, 한 일만 적어요. 시간·어려웠던 점·감정은 선택이에요.',
    tag: '30초',
  },
  {
    icon: <SparkleIcon width={22} height={22} />,
    title: 'AI가 정리해요',
    lead: '기록에서 주제와 어려웠던 점을 뽑아 태그로 정리해 줘요.',
    tag: '저장하면 자동',
  },
  {
    icon: <CheckIcon width={22} height={22} />,
    title: '다음 걸음을 찾아요',
    lead: '흐름을 돌아보고, 오늘 할 수 있는 작은 행동 하나를 받아요.',
    tag: '5~10분',
  },
]

export function LandingHowTo() {
  return (
    <section id="howto" className={[SECTION, 'scroll-mt-[72px] lg:scroll-mt-[88px]'].join(' ')}>
      <div className={[WRAP, 'flex flex-col gap-[24px] lg:gap-[56px]'].join(' ')}>
        <div className="flex flex-col gap-[12px] lg:items-center lg:gap-[14px] lg:text-center">
          <Eyebrow>이용 방법</Eyebrow>
          <SectionTitle>한 줄 쓰면, 나머지는 같이 해요</SectionTitle>
        </div>

        <div className="flex flex-col gap-[14px] lg:flex-row lg:items-stretch lg:gap-[24px]">
          {STEPS.map((step, index) => (
            <LandingCard key={step.title} className="flex flex-1 flex-row items-start gap-[14px] lg:flex-col lg:gap-[16px] lg:p-[32px]">
              <StepBadge>{index + 1}</StepBadge>
              <div className="flex min-w-0 flex-1 flex-col gap-[4px] lg:gap-[16px]">
                <div className="hidden items-center justify-between lg:flex">
                  <IconBadge>{step.icon}</IconBadge>
                </div>
                <div className="flex items-center justify-between gap-[8px]">
                  <h3 className="font-serif text-[18px] font-semibold leading-[1.5] text-ink">{step.title}</h3>
                  <Tag className="lg:hidden">{step.tag}</Tag>
                </div>
                <p className="text-[15px] leading-[1.7] text-ink-2 lg:text-[16px]">{step.lead}</p>
                <Tag className="hidden self-start lg:mt-auto lg:inline-flex">{step.tag}</Tag>
              </div>
            </LandingCard>
          ))}
        </div>
      </div>
    </section>
  )
}
