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
          'absolute top-[0.1875rem] h-[1.375rem] w-[1.375rem] rounded-full bg-white transition-[left,right]',
          checked ? 'right-[0.1875rem]' : 'left-[0.1875rem]',
        ].join(' ')}
      />
    </button>
  )
}
