import type { ReactNode, RefObject } from 'react'

type ListPaneProps = {
  listRef: RefObject<HTMLDivElement | null>
  children: ReactNode
  footer: ReactNode
  className?: string
}

/**
 * 목록+페이지네이션 공용 구조(P1 2단계). 목록 영역(flex:1, overflow:hidden)과 푸터(flex:none,
 * 높이 3rem 고정)를 형제로 둬서, 페이지 번호가 나타나거나 사라져도 목록 영역의 높이가 흔들리지
 * 않게 한다 — 페이지네이션이 absolute/sticky로 목록 위에 떠 있지 않고 일반 흐름에 있으면서도,
 * 1페이지뿐이라 비어 있을 때조차 자리를 그대로 차지해 마지막 카드를 덮는 일이 없다.
 * useFitPageSize의 containerRef는 이 컴포넌트의 listRef(푸터를 제외한 목록 영역)에 꽂는다.
 */
export function ListPane({ listRef, children, footer, className = '' }: ListPaneProps) {
  return (
    <div className={['flex flex-col lg:min-h-0', className].filter(Boolean).join(' ')}>
      <div ref={listRef} className="min-h-0 flex-1 overflow-hidden">
        {children}
      </div>
      <div className="flex h-12 flex-none items-center justify-center">{footer}</div>
    </div>
  )
}
