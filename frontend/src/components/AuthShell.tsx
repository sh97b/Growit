import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import growitLogo from '../assets/brand/growit-logo.svg'

export function AuthShell({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col bg-paper">
      <header className="hidden h-[4.25rem] flex-none items-center border-b border-line bg-paper lg:flex">
        <div className="mx-auto w-full max-w-[90rem] px-10">
          <Link to="/" className="inline-flex items-center no-underline">
            <img src={growitLogo} alt="Growit" className="h-7 w-auto max-w-[9rem]" />
          </Link>
        </div>
      </header>

      <main className="flex flex-1 flex-col lg:items-center lg:justify-center">
        <div className="mx-auto flex w-full max-w-[26.25rem] flex-1 flex-col gap-6 px-5 pb-8 pt-6 lg:hidden">
          <Link to="/" className="inline-flex items-center self-start no-underline">
            <img src={growitLogo} alt="Growit" className="h-6 w-auto max-w-[8rem]" />
          </Link>
          {children}
        </div>

        <div className="mx-auto hidden w-full max-w-[90rem] grid-cols-[1fr_380px] items-center gap-[8.75rem] px-10 py-16 lg:grid">
          <div className="flex max-w-[27.5rem] flex-col gap-4">
            <p className="t-page">
              꾸준함이 눈에 보이고,
              <br />
              내가 쓴 말이 먼저 읽히는 곳
            </p>
            <p className="t-body-lg text-ink-2">
              쉬어 간 날도 흐름의 일부예요. 오늘 한 줄이면 충분해요.
            </p>
          </div>
          <div className="flex flex-col gap-4">{children}</div>
        </div>
      </main>
    </div>
  )
}
