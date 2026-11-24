import { useEffect, useState } from 'react'

const CHECK_INTERVAL_MS = 5 * 60 * 1000

function currentBundle(): string | null {
  return document.querySelector<HTMLScriptElement>('script[type="module"][src*="/assets/"]')?.getAttribute('src') ?? null
}

/** 서버의 index.html이 가리키는 번들 이름을 읽는다. 배포마다 해시가 바뀌므로 다르면 새 버전이다. */
async function latestBundle(): Promise<string | null> {
  const response = await fetch('/index.html', { cache: 'no-store' })
  if (!response.ok) return null
  const html = await response.text()
  return /<script[^>]+type="module"[^>]+src="([^"]*\/assets\/[^"]+\.js)"/.exec(html)?.[1] ?? null
}

function isTyping(pathname: string): boolean {
  const active = document.activeElement
  const editable =
    active instanceof HTMLInputElement || active instanceof HTMLTextAreaElement || (active as HTMLElement | null)?.isContentEditable === true
  return editable || /^\/records\/(new|\d+\/edit)/.test(pathname)
}

/**
 * 새 버전이 배포되면 알린다. 입력 중이 아닐 때 탭으로 돌아오면 조용히 새로고침하고, 입력 중이거나 기록
 * 작성·수정 화면이면 아래 배너로만 알려서 쓰던 글이 사라지지 않게 한다.
 */
export function UpdateBanner() {
  const [updateReady, setUpdateReady] = useState(false)

  useEffect(() => {
    if (import.meta.env.DEV) return
    const loaded = currentBundle()
    if (!loaded) return
    let cancelled = false

    async function check(fromReturn: boolean) {
      try {
        const latest = await latestBundle()
        if (cancelled || !latest || latest === loaded) return
        if (fromReturn && !isTyping(window.location.pathname)) {
          window.location.reload()
          return
        }
        setUpdateReady(true)
      } catch {
        // 네트워크 오류는 다음 확인 때 다시 본다.
      }
    }

    const timer = window.setInterval(() => void check(false), CHECK_INTERVAL_MS)
    const onVisible = () => {
      if (document.visibilityState === 'visible') void check(true)
    }
    document.addEventListener('visibilitychange', onVisible)
    return () => {
      cancelled = true
      window.clearInterval(timer)
      document.removeEventListener('visibilitychange', onVisible)
    }
  }, [])

  if (!updateReady) return null

  return (
    <div
      role="status"
      className="fixed inset-x-4 bottom-20 z-50 mx-auto flex max-w-md items-center justify-between gap-3 rounded-xl border border-line-2 bg-white px-4 py-2 t-sub text-ink shadow-md lg:bottom-4"
    >
      <span>새 버전이 있어요.</span>
      <button
        type="button"
        onClick={() => window.location.reload()}
        className="inline-flex min-h-11 items-center rounded-lg px-3 font-medium text-base-ink hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]"
      >
        새로고침
      </button>
    </div>
  )
}
