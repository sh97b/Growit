import type { ReactNode } from 'react'

type Props = { left?: ReactNode; right?: ReactNode }

/**
 * 돌아보기 탭의 2행 컨트롤 줄 공용 구조(P1 3단계): 왼쪽 flex-1 영역(검색창 또는 안내 글자) +
 * 오른쪽 고정 영역(분야·기간 드롭다운 등). 모든 탭이 같은 높이(2.5rem)·같은 간격(0.5rem)을 쓴다.
 */
export function ReviewToolbar({ left, right }: Props) {
  return (
    <div className="flex min-h-10 flex-wrap items-center gap-2 lg:flex-nowrap">
      <div className="min-w-0 flex-1 text-caption-min text-ink-2">{left}</div>
      {right ? <div className="flex flex-none flex-wrap items-center gap-2">{right}</div> : null}
    </div>
  )
}
