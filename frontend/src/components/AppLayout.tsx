import { Outlet } from 'react-router-dom'
import { AccountMenu } from './AccountMenu'
import { Header } from './Header'
import { TabBar } from './TabBar'
import { useOfflineSync } from '../hooks/useOfflineSync'

export function AppLayout() {
  useOfflineSync()

  return (
    <div className="flex min-h-dvh flex-col bg-paper text-ink lg:grid lg:h-dvh lg:grid-rows-[auto_minmax(0,1fr)] lg:overflow-hidden">
      <Header />
      <header className="flex h-14 flex-none items-center justify-end border-b border-line bg-paper px-5 lg:hidden">
        <AccountMenu size={36} />
      </header>
      <main className="min-h-0 flex-1 overflow-y-auto lg:flex lg:flex-col lg:overflow-hidden">
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
