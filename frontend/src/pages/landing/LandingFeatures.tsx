import type { ReactNode } from 'react'
import { SparkleIcon } from '../../components/icons'
import { CheckItem, EvChipStatic, Eyebrow, LandingCard, Lead, SECTION, SectionTitle, Shadow, StaticHeatmap, SubTitle, WRAP } from './shared'

function FeatureRow({ eyebrow, title, lead, items, visual, reverse = false }: {
  eyebrow: string
  title: ReactNode
  lead: string
  items: string[]
  visual: ReactNode
  reverse?: boolean
}) {
  return (
    <div className={['flex flex-col gap-[24px] lg:items-center lg:gap-[72px]', reverse ? 'lg:flex-row-reverse' : 'lg:flex-row'].join(' ')}>
      <div className="flex flex-col gap-[14px] lg:flex-[1_1_0%] lg:gap-[18px]">
        <Eyebrow>{eyebrow}</Eyebrow>
        <SubTitle>{title}</SubTitle>
        <Lead>{lead}</Lead>
        <ul className="m-0 flex list-none flex-col gap-[10px] p-0">
          {items.map((item) => (
            <CheckItem key={item}>{item}</CheckItem>
          ))}
        </ul>
      </div>
      <div className="flex justify-center lg:flex-[1.1_1_0%]">{visual}</div>
    </div>
  )
}

function ActivityMapVisual() {
  return (
    <Shadow className="w-full max-w-[540px]">
      <LandingCard className="flex flex-col gap-[18px] p-[28px]">
        <div className="flex items-center justify-between">
          <span className="font-serif text-[18px] font-semibold text-ink">최근 12주 활동 지도</span>
          <span className="inline-flex min-h-[24px] items-center rounded-[6px] bg-tag-topic-bg px-[8px] text-[12px] font-medium text-tag-topic-fg">
            3개 분야
          </span>
        </div>
        <StaticHeatmap cell={30} labelWidth={48} />
        <div className="mt-[2px] flex items-center gap-1 text-[13px] text-ink-2">
          <span>적음</span>
          <i className="h-[14px] w-[14px] rounded-[4px]" style={{ background: 'color-mix(in srgb, var(--color-base) 35%, var(--color-paper))' }} />
          <i className="h-[14px] w-[14px] rounded-[4px]" style={{ background: 'color-mix(in srgb, var(--color-base) 65%, var(--color-paper))' }} />
          <i className="h-[14px] w-[14px] rounded-[4px]" style={{ background: 'var(--color-base)' }} />
          <span>많음</span>
        </div>
        <div className="flex gap-[12px] border-t border-line pt-[16px]">
          <div className="flex flex-1 flex-col gap-[2px]">
            <span className="text-[13px] text-ink-2">기록한 날</span>
            <span className="font-serif text-[20px] font-semibold tabular-nums text-ink">12일</span>
          </div>
          <div className="flex flex-1 flex-col gap-[2px]">
            <span className="text-[13px] text-ink-2">쌓인 시간</span>
            <span className="font-serif text-[20px] font-semibold tabular-nums text-ink">8시간 30분</span>
          </div>
          <div className="flex flex-1 flex-col gap-[2px]">
            <span className="text-[13px] text-ink-2">시작한 지</span>
            <span className="font-serif text-[20px] font-semibold tabular-nums text-ink">63일</span>
          </div>
        </div>
      </LandingCard>
    </Shadow>
  )
}

function ChangeVisual() {
  return (
    <Shadow className="w-full max-w-[540px]">
      <LandingCard className="flex flex-col gap-[16px] p-[28px]">
        <div className="flex items-center justify-between">
          <span className="font-serif text-[18px] font-semibold text-ink">나의 변화</span>
          <div className="inline-flex rounded-[8px] border border-line-2 bg-white p-[3px]">
            <button type="button" className="min-h-[38px] rounded-[6px] px-[14px] text-[14px] font-medium text-ink-2">1주 전</button>
            <button type="button" className="min-h-[38px] rounded-[6px] bg-ink px-[14px] text-[14px] font-medium text-white">한 달 전</button>
          </div>
        </div>
        <div className="flex items-stretch gap-[12px]">
          <div className="flex flex-1 flex-col gap-[8px] rounded-[16px] bg-surface p-[16px]">
            <span className="text-[13px] font-semibold text-ink">9월 25일 · 그때</span>
            <p className="font-serif text-[16px] leading-[1.75] text-ink">
              코드 이름이 낯설다. 줄 어디를 눌러야 하는지 계속 악보를 본다.
            </p>
          </div>
          <div
            className="flex flex-1 flex-col gap-[8px] rounded-[16px] p-[16px]"
            style={{ background: 'color-mix(in srgb, var(--color-base) 9%, #fff)', border: '1px solid color-mix(in srgb, var(--color-base) 28%, #fff)' }}
          >
            <span className="text-[13px] font-semibold text-base-ink">10월 28일 · 오늘</span>
            <p className="font-serif text-[16px] leading-[1.75] text-ink">
              C에서 G로 바꾸는 걸 메트로놈 60에 맞춰 열 번 했다. 처음으로 한 번도 안 끊겼다.
            </p>
          </div>
        </div>
        <div className="flex flex-col gap-[10px] border-t border-line pt-[16px]">
          <span className="flex items-center gap-[6px] text-[13px] font-semibold text-base-ink">
            <SparkleIcon width={16} height={16} />
            AI 해석
          </span>
          <p className="font-serif text-[17px] font-semibold leading-[1.7] text-ink">
            9/25엔 코드 이름이 낯설다고 적었는데, 10/28 기록에서는 C→G 전환을 열 번 이어 갔어요.
          </p>
          <div className="flex flex-wrap gap-[8px]">
            <EvChipStatic date="9/25" text="코드 이름이 낯설다…" />
            <EvChipStatic date="10/28" text="C에서 G로 바꾸는 걸…" />
          </div>
        </div>
      </LandingCard>
    </Shadow>
  )
}

function CoachVisual() {
  return (
    <Shadow className="w-full max-w-[540px]">
      <LandingCard className="flex flex-col gap-[16px] p-[28px]">
        <div className="flex items-center justify-between">
          <span className="font-serif text-[18px] font-semibold text-ink">AI 회고</span>
          <span className="text-[14px] text-ink-2">10월 14일–28일</span>
        </div>
        <p className="font-serif text-[17px] leading-[1.75] text-ink">F 코드가 처음으로 소리 났고, C→G 전환은 열 번 이어졌어요.</p>
        <div className="flex flex-wrap gap-[8px]">
          <EvChipStatic date="10/26" text="F 코드를 처음으로…" />
          <EvChipStatic date="10/28" text="C에서 G로 바꾸는 걸…" />
        </div>
        <div className="flex flex-col gap-[8px] rounded-[16px] p-[20px]" style={{ background: 'color-mix(in srgb, var(--color-base) 9%, #fff)', border: '1px solid color-mix(in srgb, var(--color-base) 28%, #fff)' }}>
          <span className="text-[13px] font-semibold text-base-ink">다음 작은 행동 · 5분</span>
          <p className="font-serif text-[19px] font-semibold leading-[1.6] text-ink">메트로놈 70으로 올려서 C→G 전환을 열 번 해 보세요.</p>
        </div>
        <div className="flex flex-wrap gap-[8px]">
          <span className="inline-flex min-h-[44px] items-center rounded-[8px] border border-line-2 bg-white px-[16px] text-[14px] font-medium text-ink">해봤어요</span>
          <span className="inline-flex min-h-[44px] items-center rounded-[8px] border border-line-2 bg-white px-[16px] text-[14px] font-medium text-ink">오늘은 쉴게요</span>
          <span className="inline-flex min-h-[44px] items-center rounded-[8px] border border-line-2 bg-white px-[16px] text-[14px] font-medium text-ink">너무 어려워요</span>
        </div>
      </LandingCard>
    </Shadow>
  )
}

export function LandingFeatures() {
  return (
    <section id="features" className={[SECTION, 'scroll-mt-[72px] border-y border-line bg-surface lg:scroll-mt-[88px]'].join(' ')}>
      <div className={[WRAP, 'flex flex-col gap-[56px] lg:gap-[112px]'].join(' ')}>
        <div className="flex flex-col gap-[14px] lg:max-w-[760px]">
          <Eyebrow>Growit이 다른 점</Eyebrow>
          <SectionTitle>
            숫자가 아니라,
            <br />
            내가 쓴 기록이 변화를 말해 줘요
          </SectionTitle>
        </div>

        <FeatureRow
          eyebrow="꾸준함 가시화"
          title={
            <>
              쉬어 간 날까지 포함한
              <br />
              활동의 흐름을 보여 드려요
            </>
          }
          lead="활동 지도, 주간 통계, 타임라인으로 쌓인 기록을 한눈에 봐요. 연속 일수 대신, 기간에 걸쳐 어떤 활동을 이어 왔는지를 보여 줘요."
          items={['분야별 활동 지도(홈 26주, 지난 기록 최근 1년)', '이번 주 이어 온 활동을 AI가 한 줄로 요약', '쌓인 기록과 시간은 작고 조용하게']}
          visual={<ActivityMapVisual />}
        />

        <FeatureRow
          eyebrow="나의 변화"
          title={
            <>
              그때의 기록과 오늘의 기록을
              <br />
              나란히 놓고 봐요
            </>
          }
          lead="1주 전, 2주 전, 한 달 전, 몇 개월 전의 기록을 오늘과 비교해요. AI 해석에는 항상 근거가 된 기록이 날짜 칩으로 붙어요."
          items={['내가 쓴 글이 주인공이에요', '확인되는 변화만 짚고 단정하지 않아요', '칩을 누르면 그날의 기록이 열려요']}
          visual={<ChangeVisual />}
          reverse
        />

        <FeatureRow
          eyebrow="AI 회고"
          title={
            <>
              돌아보고, 다음에 할
              <br />
              작은 일 하나를 찾아요
            </>
          }
          lead="선택한 기간을 돌아보며 반복되는 어려움을 찾고, 5~10분이면 끝나는 작은 행동 하나를 제안해요. 오래 쉬었다면 부담 없는 복귀 행동을 먼저 권해요."
          items={['너무 어려우면 더 작게 바꿔 줘요', '기록이 5개 쌓이면 자동 회고가 도착해요', '오늘은 쉴게요, 도 괜찮아요']}
          visual={<CoachVisual />}
        />
      </div>
    </section>
  )
}
