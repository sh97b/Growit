import { useEffect, useState } from 'react'

function Bar({ className = '' }: { className?: string }) {
  return <div className={['h-3.5 rounded-full bg-rest motion-safe:animate-pulse', className].join(' ')} />
}

/**
 * S1 A-2단계: 자리표시(스켈레톤)는 250ms 안에 데이터가 오면 아예 보이지 않아야 깜박임이 없다.
 * 이 컴포넌트 하나가 앱 전체에서 공용 로딩 표시로 쓰이므로, 여기서 한 번만 지연시키면 호출부를
 * 전부 고칠 필요가 없다. 250ms 전에는 빈 공간(null)을 유지한다.
 */
export function Loading({ label = '불러오는 중이에요' }: { label?: string }) {
  const [show, setShow] = useState(false)

  useEffect(() => {
    const timer = setTimeout(() => setShow(true), 250)
    return () => clearTimeout(timer)
  }, [])

  if (!show) return null

  return (
    <div className="flex flex-col gap-3" role="status" aria-live="polite">
      <Bar className="w-2/3" />
      <Bar className="w-full" />
      <Bar className="w-5/6" />
      <span className="sr-only">{label}</span>
    </div>
  )
}
