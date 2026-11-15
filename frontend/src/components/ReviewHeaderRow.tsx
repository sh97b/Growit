import type { ReactNode } from 'react'
import { ReviewTabs } from './ReviewTabs'

type Props = {
  /** 스크린리더 전용 큰 제목(지난 기록·나의 변화·AI 회고). 시각적으로는 숨긴다(N1). */
  title: string
  /** 탭과 같은 줄 오른쪽 끝에 두는 탭별 주 액션(예: 변화의 [변화 보기]). 없으면 탭만 보인다. */
  action?: ReactNode
}

/**
 * 돌아보기 머리줄(N1): 탭 박스는 내용 폭만 차지하고, 같은 줄 오른쪽 끝에 탭별 주 액션을 둔다.
 * 큰 페이지 제목은 sr-only로만 남겨 전체 머리줄 높이를 약 7rem 이내로 줄인다.
 */
export function ReviewHeaderRow({ title, action }: Props) {
  return (
    <>
      <h1 className="sr-only">{title}</h1>
      <div className="flex items-center justify-between gap-3">
        <ReviewTabs />
        {action ? <div className="flex flex-none items-center gap-2">{action}</div> : null}
      </div>
    </>
  )
}
