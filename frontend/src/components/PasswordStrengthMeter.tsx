import { PASSWORD_STRENGTH_LABEL, estimatePasswordStrength } from '../lib/passwordStrength'

const DOT_COUNT: Record<ReturnType<typeof estimatePasswordStrength>, number> = {
  weak: 1,
  medium: 2,
  strong: 3,
}

/** 입력을 막지 않는 가벼운 참고용 표시다(실제 판단은 서버 PasswordPolicy가 한다). */
export function PasswordStrengthMeter({ password, email }: { password: string; email: string }) {
  if (!password) return null
  const strength = estimatePasswordStrength(password, email)
  const activeDots = DOT_COUNT[strength]

  return (
    <div className="flex items-center gap-2 text-[13px] text-ink-2">
      <div className="flex gap-1" aria-hidden="true">
        {[0, 1, 2].map((i) => (
          <span
            key={i}
            className={['h-1.5 w-6 rounded-full', i < activeDots ? 'bg-base' : 'bg-line-2'].join(' ')}
          />
        ))}
      </div>
      <span>비밀번호 강도: {PASSWORD_STRENGTH_LABEL[strength]}</span>
    </div>
  )
}
