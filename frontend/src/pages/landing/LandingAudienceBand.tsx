import { Tag, WRAP } from './shared'

const TOPICS = ['악기', '운동', '코딩', '새로 배우는 모든 것']

export function LandingAudienceBand() {
  return (
    <section className="border-y border-line bg-surface">
      <div className={[WRAP, 'flex flex-col gap-[10px] py-[20px] lg:flex-row lg:items-center lg:justify-between lg:py-[28px]'].join(' ')}>
        <span className="text-[14px] font-semibold text-ink lg:text-[15px]">이런 걸 키우는 분들을 위해 만들었어요</span>
        <div className="flex flex-wrap gap-[8px] lg:gap-[12px]">
          {TOPICS.map((topic) => (
            <Tag key={topic} className="min-h-[36px] px-[14px] text-[14px] lg:min-h-[40px] lg:px-[18px] lg:text-[15px]">
              {topic}
            </Tag>
          ))}
        </div>
      </div>
    </section>
  )
}
