import { Fragment, type AnchorHTMLAttributes, type CSSProperties, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { CheckCircleIcon } from '../../components/icons'

/*
 * N2: 랜딩 전용 부품. 앱 전역의 rem 스케일(index.css의 html font-size clamp)을 따르지 않게,
 * 모든 글자·간격·크기를 리터럴 px(Tailwind 대괄호 값)로 쓴다 — rem은 항상 :root(html)의 font-size를
 * 기준으로 계산되므로, 중첩된 요소에 font-size를 다시 지정해도 rem 단위 자체는 영향을 받지 않는다.
 * 값은 design/landing/desktop/LandingDesktop.dc.html·growit.css, design/landing/mobile/LandingMobile.dc.html
 * 에서 그대로 읽었다. 색·글꼴 패밀리는 기존 Tailwind 테마 토큰(index.css의 @theme)을 그대로 쓴다.
 */

export const WRAP = 'mx-auto w-full max-w-[1200px] px-[20px] lg:px-[40px]'
export const SECTION = 'py-[56px] lg:py-[104px]'

export function Eyebrow({ children }: { children: ReactNode }) {
  return (
    <span className="inline-flex w-fit items-center gap-1.5 self-start rounded-full border border-[color-mix(in_srgb,var(--color-base)_28%,#fff)] bg-[color-mix(in_srgb,var(--color-base)_9%,#fff)] px-[12px] py-[4px] text-[13px] font-semibold leading-[1.5] text-base-ink">
      {children}
    </span>
  )
}

export function SectionTitle({ children }: { children: ReactNode }) {
  return (
    <h2 className="font-serif text-[26px] font-semibold leading-[1.42] tracking-[-0.025em] text-ink lg:text-[38px] lg:leading-[1.38]">
      {children}
    </h2>
  )
}

export function SubTitle({ children }: { children: ReactNode }) {
  return (
    <h3 className="font-serif text-[23px] font-semibold leading-[1.4] tracking-[-0.02em] text-ink lg:text-[32px] lg:leading-[1.4]">
      {children}
    </h3>
  )
}

export function Lead({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <p className={['text-[16px] leading-[1.75] text-ink-2 lg:text-[17px]', className].join(' ')}>{children}</p>
}

export function LandingCard({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <div className={['rounded-[16px] border border-line bg-white p-[18px] lg:p-[24px]', className].join(' ')}>
      {children}
    </div>
  )
}

export function Shadow({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <div className={['shadow-[0_18px_40px_-18px_rgba(43,40,35,0.28),0_2px_6px_rgba(43,40,35,0.06)]', className].join(' ')}>
      {children}
    </div>
  )
}

type BtnProps = AnchorHTMLAttributes<HTMLAnchorElement> & {
  to?: string
  variant?: 'primary' | 'secondary'
  size?: 'md' | 'sm'
  block?: boolean
}

/** 외부 링크 없이(#) 쓰는 경우는 실제 라우트로 보낼 때 to를 쓰고, 그 외엔 Link 대신 평범한 a로 둔다. */
export function LandingBtn({ to, variant = 'primary', size = 'md', block = false, className = '', children, ...rest }: BtnProps) {
  const base =
    'inline-flex items-center justify-center gap-[8px] whitespace-nowrap rounded-[8px] border font-medium no-underline'
  const sizeCls =
    size === 'md' ? 'min-h-[44px] px-[20px] text-[15px] lg:min-h-[56px] lg:px-[28px] lg:text-[17px]' : 'min-h-[40px] px-[16px] text-[14px]'
  const variantCls =
    variant === 'primary'
      ? 'border-transparent bg-base text-white'
      : 'border-line-2 bg-white text-ink'
  const cls = [base, sizeCls, variantCls, block ? 'w-full' : '', className].filter(Boolean).join(' ')
  if (to) {
    return (
      <Link to={to} className={cls}>
        {children}
      </Link>
    )
  }
  return (
    <a className={cls} {...rest}>
      {children}
    </a>
  )
}

export function CheckItem({ children }: { children: ReactNode }) {
  return (
    <li className="flex items-start gap-[10px] text-[16px] text-ink-2">
      <span className="mt-[2px] flex-none text-base-ink">
        <CheckCircleIcon width={18} height={18} />
      </span>
      {children}
    </li>
  )
}

export function Tag({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <span
      className={[
        'inline-flex min-h-[28px] items-center gap-1 whitespace-nowrap rounded-[6px] bg-tag-topic-bg px-[10px] text-[13px] font-medium text-tag-topic-fg',
        className,
      ].join(' ')}
    >
      {children}
    </span>
  )
}

/** 근거 기록 칩의 정적 목업(랜딩 전용, 실제 기록으로 이동하지 않는다). */
export function EvChipStatic({ date, text, maxWidth = 150 }: { date: string; text: string; maxWidth?: number }) {
  return (
    <span
      className="inline-flex min-h-[44px] max-w-full items-center gap-[8px] rounded-[8px] border border-line-2 bg-white px-[12px] text-[13px] text-ink"
      title={text}
    >
      <span className="flex-none font-semibold text-base-ink">{date}</span>
      <span className="min-w-0 overflow-hidden text-ellipsis whitespace-nowrap text-ink-2" style={{ maxWidth }}>
        {text}
      </span>
    </span>
  )
}

export function IconBadge({ children }: { children: ReactNode }) {
  return (
    <span className="flex h-[44px] w-[44px] flex-none items-center justify-center rounded-[12px] border border-[color-mix(in_srgb,var(--color-base)_28%,#fff)] bg-[color-mix(in_srgb,var(--color-base)_9%,#fff)] text-base-ink">
      {children}
    </span>
  )
}

export function StepBadge({ children }: { children: ReactNode }) {
  return (
    <span className="flex h-[40px] w-[40px] flex-none items-center justify-center rounded-full bg-base font-sans text-[16px] font-semibold text-white">
      {children}
    </span>
  )
}

/** 활동 지도 칸 색(ActivityHeatmap.tsx와 같은 단계 색). 랜딩은 정적 예시 데이터만 쓴다. */
const LEVEL_BG: Record<0 | 1 | 2 | 3, string> = {
  0: 'var(--color-rest)',
  1: 'color-mix(in srgb, var(--color-base) 35%, var(--color-paper))',
  2: 'color-mix(in srgb, var(--color-base) 65%, var(--color-paper))',
  3: 'var(--color-base)',
}

export type HeatmapRow = { label: string; levels: (0 | 1 | 2 | 3)[] }

export const HEATMAP_DEMO_ROWS: HeatmapRow[] = [
  { label: '기타', levels: [2, 0, 3, 0, 0, 1, 1, 0, 1, 1, 0, 2] },
  { label: '러닝', levels: [2, 0, 1, 0, 1, 1, 0, 1, 3, 2, 2, 2] },
  { label: '코딩', levels: [0, 0, 0, 0, 0, 0, 1, 2, 0, 3, 0, 1] },
]

/** 호버 안내문이 없는 순수 장식용 정적 활동 지도(랜딩 전용 예시 데이터, 실제 기록 아님). */
export function StaticHeatmap({ cell, labelWidth = 44, gap = 5 }: { cell: number; labelWidth?: number; gap?: number }) {
  return (
    <div
      role="img"
      aria-label="활동 지도 예시"
      style={{ display: 'grid', gridTemplateColumns: `${labelWidth}px repeat(12, minmax(0, 1fr))`, gap, alignItems: 'center' }}
    >
      {HEATMAP_DEMO_ROWS.map((row) => (
        <Fragment key={row.label}>
          <span className="text-[13px] font-medium text-ink">{row.label}</span>
          {row.levels.map((level, index) => (
            <i
              key={index}
              style={{ display: 'block', height: cell, borderRadius: 5, background: LEVEL_BG[level] }}
            />
          ))}
        </Fragment>
      ))}
    </div>
  )
}

export function HeatmapLegend() {
  return (
    <div className="flex items-center gap-1 text-[13px] text-ink-2">
      <span>적음</span>
      <i className="h-[14px] w-[14px] rounded-[4px]" style={{ background: LEVEL_BG[1] }} />
      <i className="h-[14px] w-[14px] rounded-[4px]" style={{ background: LEVEL_BG[2] }} />
      <i className="h-[14px] w-[14px] rounded-[4px]" style={{ background: LEVEL_BG[3] }} />
      <span>많음</span>
    </div>
  )
}

/** 디자인 원본의 새싹 SVG를 그대로 가져온 것(design/landing/desktop/LandingDesktop.dc.html). */
export function SproutIllustration({ width = 190, opacity }: { width?: number; opacity?: number }) {
  const style: CSSProperties = opacity != null ? { opacity } : {}
  return (
    <svg width={width} viewBox="0 0 260 220" aria-hidden="true" fill="none" strokeLinecap="round" strokeLinejoin="round" style={style}>
      <ellipse cx="130" cy="204" rx="104" ry="9" fill="var(--color-line-2)" opacity=".6" />
      <path d="M130 202V104" stroke="var(--color-base-ink)" strokeWidth="5" />
      <path
        d="M130 150C130 118 104 100 70 102 70 134 94 152 130 150Z"
        fill="color-mix(in srgb, var(--color-base) 65%, var(--color-paper))"
        stroke="var(--color-base-ink)"
        strokeWidth="4"
      />
      <path
        d="M130 124C130 90 154 70 192 72 192 106 166 126 130 124Z"
        fill="var(--color-base)"
        stroke="var(--color-base-ink)"
        strokeWidth="4"
      />
      <path
        d="M130 104C130 82 122 66 108 56 100 74 106 96 130 104Z"
        fill="color-mix(in srgb, var(--color-base) 35%, var(--color-paper))"
        stroke="var(--color-base-ink)"
        strokeWidth="4"
      />
      <path
        d="M82 128c14 4 28 10 40 22M168 100c-12 6-24 14-34 26"
        stroke="var(--color-base-ink)"
        strokeWidth="2.5"
        opacity=".5"
      />
    </svg>
  )
}
