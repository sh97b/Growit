/** 돌아가는 동그라미. prefers-reduced-motion이면 돌지 않는다(모양만 남는다). */
export function Spinner({ className = '' }: { className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={`inline-block h-5 w-5 flex-none rounded-full border-2 border-current border-t-transparent animate-spin motion-reduce:animate-none ${className}`}
    />
  )
}

/**
 * AI가 일하는 중임을 눈에 띄게 알리는 카드(스켈레톤 줄 + 큰 문구). 본문 크기(16px 이상)로 쓰고,
 * prefers-reduced-motion이면 깜빡임을 끈다. 화면을 읽는 도구에도 상태로 알려진다.
 */
export function AiProgressCard({ message, lines = 2 }: { message: string; lines?: number }) {
  return (
    <div role="status" aria-live="polite" className="flex flex-col gap-3.5 rounded-2xl bg-surface p-5">
      <p className="flex items-center gap-2.5 t-body font-medium leading-[1.5] text-ink">
        <Spinner className="text-base-ink" />
        {message}
      </p>
      {Array.from({ length: lines }).map((_, index) => (
        <div
          key={index}
          className={`h-3.5 animate-pulse rounded-full bg-rest motion-reduce:animate-none ${index === 0 ? 'w-[80%]' : 'w-full'}`}
        />
      ))}
    </div>
  )
}
