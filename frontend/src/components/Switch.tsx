export function Switch({
  checked,
  onChange,
  label,
  disabled = false,
}: {
  checked: boolean
  onChange: () => void
  label: string
  disabled?: boolean
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={onChange}
      className={[
        'relative h-7 w-12 flex-none rounded-full border-0 disabled:opacity-60',
        checked ? 'bg-base' : 'bg-input',
      ].join(' ')}
    >
      <span
        className={[
          'absolute top-[3px] h-[22px] w-[22px] rounded-full bg-white transition-[left,right]',
          checked ? 'right-[3px]' : 'left-[3px]',
        ].join(' ')}
      />
    </button>
  )
}
