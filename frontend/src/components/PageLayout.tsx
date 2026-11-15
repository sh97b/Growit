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

// O1(C.1): app-shell-main은 더 이상 스크롤하지 않는다(바깥 페이지 스크롤 + 안쪽 열 스크롤이 겹쳐
// 스크롤바가 두 개로 보이던 문제의 원인). 대신 각 열(column)이 자기 몫의 높이(height:100%,
// min-height:0)를 갖고 필요할 때만 스스로 overflow-y:auto 한다 — 두 열이 같은 그리드 행에서
// items-stretch로 똑같은 높이를 받으므로, 바깥 페이지가 길어질 일 자체가 없다(sticky 불필요).
const GRID_BASE = 'lg:grid lg:grid-cols-12 lg:items-stretch lg:gap-8'
const COLUMN_SCROLL = 'lg:h-full lg:min-h-0 lg:overflow-y-auto lg:overscroll-contain scrollbar-thin-fade'

type SplitProps = { main: ReactNode; side: ReactNode; className?: string }

/** 본문 7열 : 사이드 5열. 모바일은 본문 → 사이드 순서로 세로로 쌓는다. */
export function Split({ main, side, className = '' }: SplitProps) {
  return (
    <div className={[GRID_BASE, 'flex flex-col gap-5 lg:h-full', className].filter(Boolean).join(' ')}>
      <div className={['flex min-w-0 flex-col gap-5 lg:col-span-7', COLUMN_SCROLL].join(' ')}>{main}</div>
      <div className={['flex min-w-0 flex-col gap-5 lg:col-span-5', COLUMN_SCROLL].join(' ')}>{side}</div>
    </div>
  )
}

/** 본문 5열 : 사이드 7열. Split과 좌우 비율만 반대다. */
export function SplitWide({ main, side, className = '' }: SplitProps) {
  return (
    <div className={[GRID_BASE, 'flex flex-col gap-5 lg:h-full', className].filter(Boolean).join(' ')}>
      <div className={['flex min-w-0 flex-col gap-5 lg:col-span-5', COLUMN_SCROLL].join(' ')}>{main}</div>
      <div className={['flex min-w-0 flex-col gap-5 lg:col-span-7', COLUMN_SCROLL].join(' ')}>{side}</div>
    </div>
  )
}

/** 사이드 없이 12열을 전부 쓴다. 왼쪽으로 쏠리지 않게 본문 폭을 데스크톱 전체로 둔다. */
export function Full({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <div className={['flex flex-col gap-5', className].filter(Boolean).join(' ')}>{children}</div>
}

type SettingsShellProps = { menu: ReactNode; content: ReactNode }

/** 설정 계열(왼쪽 메뉴 3열 : 오른쪽 내용 9열, 내용은 최대 56rem 왼쪽 정렬). 모바일은 menu만 보인다. */
export function SettingsShell({ menu, content }: SettingsShellProps) {
  return (
    <div className={[GRID_BASE, 'flex flex-col gap-5 lg:h-full'].join(' ')}>
      <div className={['flex min-w-0 flex-col gap-1 lg:col-span-3', COLUMN_SCROLL].join(' ')}>{menu}</div>
      <div className={['flex min-w-0 flex-col gap-5 lg:col-span-9', COLUMN_SCROLL].join(' ')}>
        <div className="flex w-full max-w-[56rem] flex-col gap-5">{content}</div>
      </div>
    </div>
  )
}

type SettingsPageProps = { title: string; description?: ReactNode; children: ReactNode }

/**
 * 모든 설정 하위 화면(활동 분야 관리·AI 정리·알림 설정·비밀번호 바꾸기·계정 삭제)이 공유하는
 * 브레드크럼·제목 구조(L3). 화면마다 제각각 작성돼 있던 것을 하나로 모아, 어떤 설정 화면에서도
 * 왼쪽 시작 x와 위쪽 y가 활동 분야 관리와 똑같이 맞게 한다.
 */
export function SettingsPage({ title, description, children }: SettingsPageProps) {
  return (
    <div className="flex flex-col gap-5">
      <nav aria-label="현재 위치" className="hidden items-center gap-1.5 text-caption-min text-ink-3 lg:flex">
        <span>설정</span>
        <span aria-hidden="true">›</span>
        <span>{title}</span>
      </nav>
      <h1 className="text-page-title">{title}</h1>
      {description ? <p className="text-body-min text-ink-2">{description}</p> : null}
      {children}
    </div>
  )
}

type SettingsRowProps = { label: string; description?: ReactNode; children: ReactNode }

/** 설정 내용의 "행" 구조(L3) — 왼쪽 라벨·보조 설명, 오른쪽 조작 부품, 행 사이는 연한 구분선. */
export function SettingsRow({ label, description, children }: SettingsRowProps) {
  return (
    <div className="flex flex-col gap-3 border-t border-line py-5 first:border-t-0 lg:flex-row lg:items-center lg:justify-between lg:gap-6">
      <div className="flex flex-col gap-1">
        <span className="text-card-title">{label}</span>
        {description ? <span className="text-caption-min text-ink-2">{description}</span> : null}
      </div>
      <div className="flex flex-none flex-col gap-2 lg:items-end">{children}</div>
    </div>
  )
}
