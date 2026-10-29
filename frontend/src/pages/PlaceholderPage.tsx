export function PlaceholderPage({ title }: { title: string }) {
  return (
    <div className="flex flex-col items-center gap-3 py-16 text-center">
      <p className="font-serif text-xl font-semibold">{title}</p>
      <p className="text-sm text-ink-2">이 화면은 준비 중이에요. 곧 만나볼 수 있어요.</p>
    </div>
  )
}
