import { PASSWORD_RULES, hasDisallowedCharacters } from '../lib/passwordRules'
import { CheckIcon, XIcon } from './icons'

/** 서버 PasswordPolicy와 같은 규칙을 실시간으로 보여 준다(입력을 막지는 않음, 최종 판단은 서버). */
export function PasswordChecklist({ password }: { password: string }) {
  if (!password) return null
  const disallowed = hasDisallowedCharacters(password)

  return (
    <ul className="flex flex-col gap-1 text-caption-min">
      {PASSWORD_RULES.map((rule) => {
        const met = rule.test(password)
        return (
          <li key={rule.key} className={['flex items-center gap-1.5', met ? 'text-ink-2' : 'text-ink-3'].join(' ')}>
            {met ? (
              <CheckIcon aria-hidden="true" className="h-3.5 w-3.5 shrink-0 text-base" />
            ) : (
              <XIcon aria-hidden="true" className="h-3.5 w-3.5 shrink-0 text-ink-3" />
            )}
            <span>
              {rule.label}
              <span className="sr-only">{met ? ' - 충족' : ' - 아직 충족하지 않음'}</span>
            </span>
          </li>
        )
      })}
      {disallowed ? (
        <li className="flex items-center gap-1.5 text-note-sand-fg">
          <XIcon aria-hidden="true" className="h-3.5 w-3.5 shrink-0" />
          <span>영문, 숫자, 특수문자만 쓸 수 있어요</span>
        </li>
      ) : null}
    </ul>
  )
}
