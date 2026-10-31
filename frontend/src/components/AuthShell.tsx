import type { ReactNode } from 'react'
import { Footer } from './Footer'

export function AuthShell({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col bg-paper">
      <header className="hidden h-[68px] flex-none items-center border-b border-line bg-paper lg:flex">
        <div className="mx-auto w-full max-w-[1200px] px-10">
          <span className="font-serif text-xl font-semibold text-ink">Growit</span>
        </div>
      </header>

      <main className="flex flex-1 flex-col lg:items-center lg:justify-center">
        <div className="mx-auto flex w-full max-w-[420px] flex-1 flex-col gap-6 px-5 pb-8 pt-6 lg:hidden">
          <span className="font-serif text-xl font-semibold text-ink">Growit</span>
          {children}
        </div>

        <div className="mx-auto hidden w-full max-w-[1200px] grid-cols-[1fr_380px] items-center gap-[140px] px-10 py-16 lg:grid">
          <div className="flex max-w-[440px] flex-col gap-4">
            <p className="font-serif text-[36px] font-semibold leading-[1.3]">
              꾸준함이 눈에 보이고, 내가 쓴 말이 먼저 읽히는 곳
            </p>
            <p className="text-[17px] leading-[1.75] text-ink-2">
              쉬어 간 날도 흐름의 일부예요. 오늘 한 줄이면 충분해요.
            </p>
          </div>
          <div className="flex flex-col gap-4">{children}</div>
        </div>
      </main>

      <Footer />
    </div>
  )
}
