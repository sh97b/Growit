import type { ReactNode } from 'react'

type PageHeaderProps = {
  title: string
  subtitle?: ReactNode
  action?: ReactNode
}

/**
 * 모든 페이지의 제목 위치·크기를 하나로 통일한다(LAYOUT-V2). 돌아보기 탭 박스·보조 링크 줄은
 * 이 컴포넌트가 아니라 페이지 바로 위(ReviewSectionLayout)에서 그리므로, 여기서는 제목 한 줄만
 * 책임진다 — 탭 줄은 자연히 이 컴포넌트 바로 위에 놓인다.
 */
export function PageHeader({ title, subtitle, action }: PageHeaderProps) {
  return (
    <div className="flex flex-none items-start justify-between gap-4">
      <div className="flex flex-col gap-1">
        <h1 className="text-page-title">{title}</h1>
        {subtitle ? <p className="text-body-min text-ink-2">{subtitle}</p> : null}
      </div>
      {action ? <div className="flex flex-none items-center gap-2">{action}</div> : null}
    </div>
  )
}

const GRID_BASE = 'lg:grid lg:grid-cols-12 lg:items-start lg:gap-8'
/** 사이드 열은 헤더 높이(4.25rem) + 1.5rem 아래에서 고정된다. */
const SIDE_STICKY = 'lg:sticky lg:top-[5.75rem]'

type SplitProps = { main: ReactNode; side: ReactNode; className?: string }

/** 본문 7열 : 사이드 5열. 모바일은 본문 → 사이드 순서로 세로로 쌓는다. */
export function Split({ main, side, className = '' }: SplitProps) {
  return (
    <div className={[GRID_BASE, 'flex flex-col gap-5', className].filter(Boolean).join(' ')}>
      <div className="flex min-w-0 flex-col gap-5 lg:col-span-7">{main}</div>
      <div className={['flex min-w-0 flex-col gap-5 lg:col-span-5', SIDE_STICKY].join(' ')}>{side}</div>
    </div>
  )
}

/** 본문 5열 : 사이드 7열. Split과 좌우 비율만 반대다. */
export function SplitWide({ main, side, className = '' }: SplitProps) {
  return (
    <div className={[GRID_BASE, 'flex flex-col gap-5', className].filter(Boolean).join(' ')}>
      <div className="flex min-w-0 flex-col gap-5 lg:col-span-5">{main}</div>
      <div className={['flex min-w-0 flex-col gap-5 lg:col-span-7', SIDE_STICKY].join(' ')}>{side}</div>
    </div>
  )
}

/** 사이드 없이 12열을 전부 쓴다. 왼쪽으로 쏠리지 않게 본문 폭을 데스크톱 전체로 둔다. */
export function Full({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <div className={['flex flex-col gap-5', className].filter(Boolean).join(' ')}>{children}</div>
}

type SettingsShellProps = { menu: ReactNode; content: ReactNode }

/** 설정 계열(왼쪽 메뉴 3열 : 오른쪽 내용 9열, 내용은 최대 46rem 왼쪽 정렬). 모바일은 menu만 보인다. */
export function SettingsShell({ menu, content }: SettingsShellProps) {
  return (
    <div className={[GRID_BASE, 'flex flex-col gap-5 lg:h-full'].join(' ')}>
      <div className={['flex min-w-0 flex-col gap-1 lg:col-span-3', SIDE_STICKY].join(' ')}>{menu}</div>
      <div className="flex min-w-0 flex-col gap-5 lg:col-span-9">
        <div className="flex w-full max-w-[46rem] flex-col gap-5">{content}</div>
      </div>
    </div>
  )
}
