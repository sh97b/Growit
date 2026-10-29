import { Outlet } from 'react-router-dom'
import { Footer } from './Footer'
import { Header } from './Header'
import { TabBar } from './TabBar'

export function AppLayout() {
  return (
    <div className="flex min-h-dvh flex-col bg-paper text-ink">
      <Header />
      <main className="min-h-0 flex-1 overflow-y-auto">
        <div className="mx-auto w-full max-w-[1200px] px-5 py-6 lg:px-10">
          <Outlet />
        </div>
      </main>
      <Footer />
      <TabBar />
    </div>
  )
}
