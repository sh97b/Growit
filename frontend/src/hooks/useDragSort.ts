import { useState, type DragEvent } from 'react'

/**
 * 목록 한 개 안에서 끌어 놓아 순서를 바꾸는 훅(브라우저 기본 드래그 앤 드롭, 마우스 기준).
 * 끌기 시작한 항목이 이 목록 것일 때만 반응하므로 다른 목록 사이로는 옮겨지지 않는다.
 * onDrop은 끌던 항목 id와 놓은 자리(0부터)를 받는다.
 */
export function useDragSort(ids: number[], onDrop: (id: number, position: number) => void, disabled = false) {
  const [dragId, setDragId] = useState<number | null>(null)
  const [overId, setOverId] = useState<number | null>(null)

  function reset() {
    setDragId(null)
    setOverId(null)
  }

  /** 항목(li)에 펼쳐 쓰는 속성. 끄는 중이면 data-drop이 "before"/"after"로 놓일 쪽을 알려 준다. */
  function itemProps(id: number) {
    if (disabled) return {}
    const dragIndex = dragId == null ? -1 : ids.indexOf(dragId)
    const overIndex = ids.indexOf(id)
    const dropSide: 'before' | 'after' | undefined =
      dragId != null && overId === id && dragId !== id ? (dragIndex < overIndex ? 'after' : 'before') : undefined
    return {
      draggable: true,
      'data-dragging': dragId === id ? '' : undefined,
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

  return { itemProps }
}
