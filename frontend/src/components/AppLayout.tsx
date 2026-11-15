import { Outlet } from 'react-router-dom'
import { AccountMenu } from './AccountMenu'
import { Header } from './Header'
import { TabBar } from './TabBar'
import { useOfflineSync } from '../hooks/useOfflineSync'

export function AppLayout() {
  useOfflineSync()

  return (
    // Q1 5번: 모바일은 min-h-dvh(최소 높이)가 아니라 h-dvh(고정 높이)여야 한다 — flex 컨테이너의
    // 높이가 "auto"면(최소값만 있고 정해진 높이가 없으면) flex-1인 main이 실제로 남는 공간만큼만
    // 차지하도록 계산되지 않고 내용 높이만큼 늘어나 버려서(overflow-y-auto가 안 걸리고) 문서(body)
    // 자체가 스크롤되고 탭바는 그 긴 문서의 맨 끝에 있었다. h-dvh로 고정해야 main이 "남은 공간"
    // 이라는 정해진 높이를 받아 그 안에서만 스크롤하고, 탭바가 항상 화면 아래에 머문다.
    <div className="flex h-dvh flex-col overflow-hidden bg-paper text-ink lg:grid lg:grid-rows-[auto_minmax(0,1fr)]">
      <Header />
      <header className="flex h-14 flex-none items-center justify-end border-b border-line bg-paper px-5 lg:hidden">
        <AccountMenu size={36} />
      </header>
      <main className="min-h-0 flex-1 overflow-y-auto overscroll-contain lg:flex lg:flex-col lg:overflow-hidden">
        {/*
          O1(C.1): 데스크톱에서는 main도 app-shell-main도 스크롤하지 않는다. 전에는 app-shell-main이
          페이지 전체를 스크롤했는데, 그 안의 개별 열(예: 돌아보기 상세 열)도 자기 스크롤을 갖고 있어서
          스크롤바가 두 개(바깥 페이지 + 안쪽 열) 겹쳐 보였다. 이제 스크롤 책임은 각 페이지의 열에게만
          있고, app-shell-main은 패딩만 제공하는 고정 높이 컨테이너다.
        */}
        <div className="app-shell-main app-shell-container py-6 lg:flex lg:min-h-0 lg:flex-1 lg:flex-col lg:overflow-hidden">
          <Outlet />
        </div>
      </main>
      <TabBar />
    </div>
  )
}
