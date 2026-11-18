import { useEffect, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

/**
 * 브라우저 기본 모달(dialog)을 쓴다 — 포커스 가두기, Esc로 닫기, 뒤쪽 화면 비활성화를 브라우저가 해 준다.
 * 열려 있을 때만 그리는 쪽에서 마운트하고, 닫힐 때(Esc 포함) onClose가 불린다.
 * body 아래에 그려서, 불러낸 카드가 화면 폭에 따라 숨겨지거나(CSS display:none) 넘침 처리로 가려져도 모달이 같이 사라지지 않게 한다.
 */
export function Dialog({
  onClose,
  labelledBy,
  children,
}: {
  onClose: () => void
  labelledBy: string
  children: ReactNode
}) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const element = ref.current
    if (element && !element.open) element.showModal()
  }, [])

  return createPortal(
    <dialog
      ref={ref}
      aria-labelledby={labelledBy}
      onClose={onClose}
      className="m-auto max-h-[90dvh] w-[min(92vw,34rem)] overflow-hidden rounded-2xl border-0 bg-paper p-0 text-ink shadow-xl backdrop:bg-black/40"
    >
      <div className="flex max-h-[90dvh] flex-col overflow-y-auto p-5 lg:p-6">{children}</div>
    </dialog>,
    document.body,
  )
}
