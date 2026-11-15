import { Outlet } from 'react-router-dom'

/**
 * 돌아보기 세 탭(기록·변화·회고)의 공통 바깥 틀. 탭 박스는 더 이상 여기서 그리지 않는다(N1) —
 * 탭별 주 액션(예: 변화의 [변화 보기])을 탭과 같은 줄 오른쪽에 둬야 해서, 각 페이지가 직접
 * ReviewHeaderRow를 그린다. 여기서는 min-h-0/flex-1 체인만 이어서 안쪽 열이 필요할 때만
 * 스크롤되게 한다(문서 스크롤 방지).
 */
export function ReviewSectionLayout() {
  return (
    <div className="flex flex-col gap-4 lg:min-h-0 lg:flex-1">
      <Outlet />
    </div>
  )
}
