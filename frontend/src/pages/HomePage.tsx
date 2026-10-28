import { useQuery } from '@tanstack/react-query'

type HealthResponse = {
  status: string
}

async function fetchHealth(): Promise<HealthResponse> {
  const baseUrl = import.meta.env.VITE_API_BASE_URL ?? ''
  const response = await fetch(`${baseUrl}/api/health`)
  if (!response.ok) {
    throw new Error(`서버 응답 오류: ${response.status}`)
  }
  return response.json()
}

export function HomePage() {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['health'],
    queryFn: fetchHealth,
  })

  return (
    <main className="flex min-h-dvh flex-col items-center justify-center gap-4 px-4 text-center">
      <h1 className="text-3xl font-semibold">Growit</h1>
      {isLoading && <p className="text-gray-500">서버 상태를 확인하는 중이에요</p>}
      {isError && (
        <p className="text-gray-500">
          서버에 연결할 수 없어요 ({(error as Error).message})
        </p>
      )}
      {data && <p className="text-gray-500">서버 상태: {data.status}</p>}
    </main>
  )
}
