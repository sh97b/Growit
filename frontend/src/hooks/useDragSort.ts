import { useState, type DragEvent, type KeyboardEvent } from 'react'

export type KeyboardSortProps = {
  'aria-pressed'?: boolean
  onKeyDown?: (event: KeyboardEvent) => void
  onBlur?: () => void
}

/**
 * 목록 한 개 안에서 순서를 바꾸는 훅. 마우스는 브라우저 기본 드래그 앤 드롭, 키보드는 "스페이스로 집기 → 방향키로 옮기기 →
 * 스페이스로 놓기(Esc 취소)"이고 진행은 message(스크린리더용 aria-live 문구)로 알려 준다.
 * 끌기 시작한 항목이 이 목록 것일 때만 반응하므로 다른 목록 사이로는 옮겨지지 않는다.
 * onDrop은 옮긴 항목 id와 놓은 자리(0부터)를 받는다. 서버 저장이 실패하면 목록이 그대로 남는다(저장 전에는 화면 순서를 바꾸지 않는다).
 */
export function useDragSort(ids: number[], onDrop: (id: number, position: number) => void, disabled = false) {
  const [dragId, setDragId] = useState<number | null>(null)
  const [overId, setOverId] = useState<number | null>(null)
  /** 키보드로 집은 항목과 지금 가리키는 자리. */
  const [held, setHeld] = useState<{ id: number; position: number } | null>(null)
  const [message, setMessage] = useState('')

  function reset() {
    setDragId(null)
    setOverId(null)
  }

  const activeDragId = held ? held.id : dragId
  const activeOverId = held ? (ids[held.position] ?? null) : overId

  /** 항목(li)에 펼쳐 쓰는 속성. 끄는 중이면 data-drop이 "before"/"after"로 놓일 쪽을 알려 준다. */
  function itemProps(id: number) {
    if (disabled) return {}
    const dragIndex = activeDragId == null ? -1 : ids.indexOf(activeDragId)
    const overIndex = ids.indexOf(id)
    const dropSide: 'before' | 'after' | undefined =
      activeDragId != null && activeOverId === id && activeDragId !== id ? (dragIndex < overIndex ? 'after' : 'before') : undefined
    return {
      draggable: true,
      'data-dragging': activeDragId === id ? '' : undefined,
      'data-drop': dropSide,
      onDragStart: (event: DragEvent) => {
        event.dataTransfer.effectAllowed = 'move'
        event.dataTransfer.setData('text/plain', String(id))
        setDragId(id)
      },
      onDragOver: (event: DragEvent) => {
        if (dragId == null) return
        event.preventDefault()
        event.dataTransfer.dropEffect = 'move'
        if (overId !== id) setOverId(id)
      },
      onDrop: (event: DragEvent) => {
        if (dragId == null) return
        event.preventDefault()
        if (dragId !== id) onDrop(dragId, overIndex)
        reset()
      },
      onDragEnd: reset,
    }
  }

  /**
   * 키보드로 옮기는 속성(집기용 손잡이 버튼이나 단계 항목에 펼친다). label은 안내 문구에 쓰는 이름이다.
   * 집은 동안 방향키·스페이스·Esc는 이 요소가 가져가고(다른 키 동작은 건드리지 않는다), 포커스가 떠나면 취소한다.
   */
  function keyboardProps(id: number, label: string): KeyboardSortProps {
    if (disabled) return {}
    const total = ids.length
    return {
      'aria-pressed': held?.id === id,
      onKeyDown: (event: KeyboardEvent) => {
        const index = ids.indexOf(id)
        if (!held || held.id !== id) {
          if (event.key === ' ' || event.key === 'Enter') {
            event.preventDefault()
            event.stopPropagation()
            setHeld({ id, position: index })
            setMessage(`${label}을(를) 집었어요. 지금 ${total}개 중 ${index + 1}번째예요. 방향키로 옮기고 스페이스로 놓아요. Esc로 취소해요.`)
          }
          return
        }
        const step = event.key === 'ArrowUp' || event.key === 'ArrowLeft' ? -1 : event.key === 'ArrowDown' || event.key === 'ArrowRight' ? 1 : 0
        if (step !== 0) {
          event.preventDefault()
          event.stopPropagation()
          const next = Math.min(total - 1, Math.max(0, held.position + step))
          setHeld({ id, position: next })
          setMessage(`${next + 1}번째로 옮기는 중이에요`)
          return
        }
        if (event.key === ' ' || event.key === 'Enter') {
          event.preventDefault()
          event.stopPropagation()
          setHeld(null)
          if (held.position !== index) {
            onDrop(id, held.position)
            setMessage(`${label}을(를) ${held.position + 1}번째에 놓았어요.`)
          } else {
            setMessage(`${label}을(를) 그 자리에 놓았어요.`)
          }
          return
        }
        if (event.key === 'Escape') {
          event.preventDefault()
          event.stopPropagation()
          setHeld(null)
          setMessage('옮기기를 취소했어요.')
        }
      },
      onBlur: () => {
        if (held?.id === id) {
          setHeld(null)
          setMessage('옮기기를 취소했어요.')
        }
      },
    }
  }

  return { itemProps, keyboardProps, message, heldId: held?.id ?? null }
}
