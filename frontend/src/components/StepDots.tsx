export function StepDots({ step, total }: { step: number; total: number }) {
  return (
    <div className="flex items-center gap-1.5" aria-label={`${step}/${total} 단계`}>
      {Array.from({ length: total }, (_, index) => index + 1).map((n) => (
        <span
          key={n}
          className={n === step ? 'h-2 w-7 rounded-full bg-base' : 'h-2 w-2 rounded-full bg-line-2'}
        />
      ))}
      <span className="ml-1.5 text-[13px] text-ink-2">
        {step}/{total}
      </span>
    </div>
  )
}
