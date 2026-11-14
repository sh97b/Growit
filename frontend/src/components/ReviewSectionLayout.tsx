import { Outlet } from 'react-router-dom'
import { ReviewTabs } from './ReviewTabs'

export function ReviewSectionLayout() {
  return (
    <div className="flex flex-col gap-5">
      <ReviewTabs />
      <Outlet />
    </div>
  )
}
