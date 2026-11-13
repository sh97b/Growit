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
      <main className="scrollbar-thin-fade min-h-0 flex-1 overflow-y-auto lg:flex lg:flex-col">
        <div className="app-shell-main app-shell-container py-6 lg:flex lg:min-h-0 lg:flex-1 lg:flex-col">
          <Outlet />
        </div>
      </main>
      <TabBar />
    </div>
  )
}
