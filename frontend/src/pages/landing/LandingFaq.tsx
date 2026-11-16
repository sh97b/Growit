import type { ReactNode } from 'react'
import { useState } from 'react'
import { ChevronDownIcon } from '../../components/icons'
import { Eyebrow, SECTION, SectionTitle, WRAP } from './shared'

const FAQS: { q: string; a: ReactNode }[] = [
  {
    q: '매일 써야 하나요?',
    a: (
      <>
        아니요. 하루 빠져도 괜찮아요. 쉬어 간 날은 실패로 표시하지 않고, 연속 일수도 강조하지 않아요.
        <br className="hidden lg:block" />
        {' '}기록한 날이 쌓이는 흐름만 활동 지도에 보여 드려요.
      </>
    ),
  },
  {
    q: '기록은 얼마나 써야 해요?',
    a: "한 줄이면 충분해요. 꼭 적어야 하는 건 '한 일' 하나이고, 시간·어려웠던 점·느낌은 선택이에요. 기록이 쌓일수록 변화 비교(3개부터)와 회고(5개부터)가 열려요.",
  },
  {
    q: 'AI가 제 기록을 바꾸거나 평가하나요?',
    a: "아니요. AI는 쓴 글을 고치지 않고, 점수나 순위로 평가하지도 않아요. 기록에 적힌 말만 근거로 달라진 점을 짚고, 근거가 된 기록은 날짜 칩으로 함께 보여 줘요. 맞지 않는 문장은 '달라요'를 눌러 고칠 수 있어요.",
  },
  {
    q: 'AI 정리를 끄고 쓸 수 있나요?',
    a: '네. 설정의 AI 정리에서 언제든 끄고, 이미 만들어진 AI 결과도 지울 수 있어요. 끄면 기록과 활동 지도는 그대로 쓸 수 있어요. 켜 두면 기록 내용이 정리와 회고를 만들기 위해 외부 AI 서비스(OpenAI)로 전송돼요.',
  },
  {
    q: '어떤 활동에 쓸 수 있어요?',
    a: '악기, 운동, 코딩처럼 꾸준히 이어 가고 싶은 활동이면 무엇이든 돼요. 활동 분야를 직접 만들어 쓰고, 분야별로 기록과 변화를 따로 볼 수 있어요.',
  },
]

function FaqItem({ index, q, a, open, onToggle }: { index: number; q: string; a: ReactNode; open: boolean; onToggle: () => void }) {
  const panelId = `landing-faq-panel-${index}`
  const buttonId = `landing-faq-button-${index}`
  return (
    <div className="border-t border-line last:border-b">
      <button
        type="button"
        id={buttonId}
        aria-expanded={open}
        aria-controls={panelId}
        onClick={onToggle}
        className="flex min-h-[60px] w-full items-center justify-between gap-[16px] border-0 bg-transparent p-0 text-left font-serif text-[17px] font-semibold leading-[1.3] tracking-[-0.02em] text-ink lg:min-h-[64px] lg:text-[19px]"
      >
        <span>{q}</span>
        <span
          className="flex-none text-ink-2 transition-transform duration-200"
          style={{ transform: open ? 'rotate(0deg)' : 'rotate(-90deg)' }}
          aria-hidden="true"
        >
          <ChevronDownIcon width={22} height={22} />
        </span>
      </button>
      <div id={panelId} role="region" aria-labelledby={buttonId} className={['grid-collapse', open ? '' : 'is-collapsed'].join(' ')}>
        <div>
          <p className="max-w-[760px] pb-[22px] text-[16px] leading-[1.75] text-ink-2">{a}</p>
        </div>
      </div>
    </div>
  )
}

export function LandingFaq() {
  const [openSet, setOpenSet] = useState<Set<number>>(() => new Set([0]))

  function toggle(index: number) {
    setOpenSet((prev) => {
      const next = new Set(prev)
      if (next.has(index)) next.delete(index)
      else next.add(index)
      return next
    })
  }

  return (
    <section id="faq" className={[SECTION, 'scroll-mt-[72px] border-t border-line bg-surface lg:scroll-mt-[88px]'].join(' ')}>
      <div className={[WRAP, 'flex flex-col gap-[20px] lg:flex-row lg:items-start lg:gap-[80px]'].join(' ')}>
        <div className="flex flex-col gap-[12px] lg:w-[340px] lg:flex-none lg:gap-[14px]">
          <Eyebrow>자주 묻는 질문</Eyebrow>
          <SectionTitle>
            궁금한 점을
            <br />
            미리 답해 드려요
          </SectionTitle>
        </div>
        <div className="flex-1">
          {FAQS.map((faq, index) => (
            <FaqItem key={faq.q} index={index} q={faq.q} a={faq.a} open={openSet.has(index)} onToggle={() => toggle(index)} />
          ))}
        </div>
      </div>
    </section>
  )
}
