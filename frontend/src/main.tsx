import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import './index.css'
import App from './App.tsx'
import { ApiError } from './lib/api.ts'
import { AuthProvider } from './lib/auth.tsx'
import { registerQueryClient } from './lib/sessionReset.ts'

function shouldRetry(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiError && error.status >= 400 && error.status < 500) {
    return false
  }
  return failureCount < 1
}

const queryClient = new QueryClient({
  defaultOptions: {
    // S1 A단계: staleTime이 0이면 화면을 바꿀 때마다 캐시가 있어도 조용히 다시 불러오다가
    // 응답이 돌아오면 데이터가 바뀌어 깜박이는 경우가 있었다. 30초 동안은 같은 키를 다시
    // 그릴 때 캐시를 그대로 쓰고 백그라운드 재요청도 하지 않는다.
    queries: { retry: shouldRetry, staleTime: 30_000 },
    mutations: { retry: shouldRetry },
  },
})
registerQueryClient(queryClient)

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {
      // 등록 실패는 조용히 넘어간다 — 알림을 못 받을 뿐 앱 사용에는 지장이 없다.
    })
  })
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <App />
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
