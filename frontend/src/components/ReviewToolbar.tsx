import type { ReactNode } from 'react'

type Props = { left?: ReactNode; right?: ReactNode }

/**
 * 돌아보기 탭의 2행 컨트롤 줄 공용 구조(P1 3단계, P2 5번): 왼쪽 고정 영역(드롭다운 등, 자연 폭) +
 * 오른쪽 flex-1 영역(안내 글자, 왼쪽 정렬). 모든 탭이 같은 높이(2.5rem)·같은 간격(0.5rem)을 쓴다.
 */
export function ReviewToolbar({ left, right }: Props) {
  return (
    // Q1 2번: 모바일은 드롭다운 두 개를 같은 폭으로 쌓고(grid 2열) 안내 글자는 그 아래 줄로 둔다
    // (고정 높이·flex-wrap으로 인한 겹침 없음, 내용만큼 자연스럽게 늘어난다).
    <div className="flex flex-col gap-2 lg:min-h-10 lg:flex-row lg:flex-nowrap lg:items-center">
      {left ? <div className="grid grid-cols-2 gap-2 lg:flex lg:flex-none lg:items-center">{left}</div> : null}
      <div className="min-w-0 flex-1 text-left text-caption-min text-ink-2">{right}</div>
    </div>
  )
}
