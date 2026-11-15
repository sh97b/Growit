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
          N1: main 자체는 더 이상 스크롤하지 않는다(데스크톱). main은 뷰포트 끝까지 패딩 없이 닿아 있어서,
          여기서 스크롤바가 생기면 화면 오른쪽 끝에 "문서 스크롤바"처럼 보였다(실제로는 main의 스크롤바였다).
          스크롤은 안쪽의 패딩 있는 app-shell-main(또는 그 안의 개별 열)이 맡아 테두리 안쪽에서만 보이게 한다.
        */}
        <div className="scrollbar-thin-fade app-shell-main app-shell-container py-6 lg:flex lg:min-h-0 lg:flex-1 lg:flex-col lg:overflow-y-auto">
          <Outlet />
        </div>
      </main>
      <TabBar />
    </div>
  )
}
