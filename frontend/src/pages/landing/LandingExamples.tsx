import { Eyebrow, EvChipStatic, LandingCard, SECTION, SectionTitle, Tag, WRAP } from './shared'

const EXAMPLES = [
  {
    topic: '기타',
    category: '악기',
    quote: 'F 코드를 처음으로 소리 내 봤다. 줄이 한두 개 먹혔지만, 검지로 누르는 감각은 조금 알 것 같다.',
    date: '10/26',
    preview: 'F 코드를 처음으로…',
  },
  {
    topic: '러닝',
    category: '운동',
    quote: '3km를 쉬지 않고 달렸다. 다리보다 호흡이 먼저 무너졌다.',
    date: '10/21',
    preview: '3km를 쉬지 않고…',
  },
  {
    topic: '코딩',
    category: '코딩',
    quote: '배열 반복문을 처음으로 내 손으로 풀었다. 오류 메시지를 읽는 일이 덜 무섭다.',
    date: '10/18',
    preview: '배열 반복문을 처음으로…',
  },
]

export function LandingExamples() {
  return (
    <section className={SECTION}>
      <div className={[WRAP, 'flex flex-col gap-[24px] lg:gap-[48px]'].join(' ')}>
        <div className="flex flex-col gap-[12px] lg:gap-[14px]">
          <Eyebrow>이런 기록이 쌓여요</Eyebrow>
          <SectionTitle>
            악기도, 운동도, 코딩도
            <br />
            한 줄에서 시작해요
          </SectionTitle>
        </div>

        <div className="flex flex-col gap-[14px] lg:flex-row lg:items-stretch lg:gap-[24px]">
          {EXAMPLES.map((example) => (
            <LandingCard key={example.topic} className="flex flex-1 flex-col gap-[12px] lg:gap-[16px] lg:p-[28px]">
              <div className="flex items-center justify-between">
                <Tag>{example.topic}</Tag>
                <span className="text-[13px] text-ink-2">{example.category}</span>
              </div>
              <p className="font-serif text-[16px] font-normal leading-[1.8] text-ink lg:text-[18px]">{example.quote}</p>
              <EvChipStatic date={example.date} text={example.preview} maxWidth={170} />
            </LandingCard>
          ))}
        </div>
      </div>
    </section>
  )
}
