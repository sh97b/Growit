function Bar({ className = '' }: { className?: string }) {
  return <div className={['h-3.5 rounded-full bg-rest motion-safe:animate-pulse', className].join(' ')} />
}

export function Loading({ label = '불러오는 중이에요' }: { label?: string }) {
  return (
    <div className="flex flex-col gap-3" role="status" aria-live="polite">
      <Bar className="w-2/3" />
      <Bar className="w-full" />
      <Bar className="w-5/6" />
      <span className="sr-only">{label}</span>
    </div>
  )
}
