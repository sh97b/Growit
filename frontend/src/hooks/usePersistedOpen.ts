import { useCallback, useState } from 'react'

/** 열림 상태를 기억해 두는 훅(웹 전용). localStorage를 못 쓰면 닫힌 채로 시작한다. */
export function usePersistedOpen(storageKey: string): [boolean, (open: boolean) => void] {
  const [open, setOpen] = useState(() => {
    try {
      return window.localStorage.getItem(`${storageKey}.open`) === '1'
    } catch {
      return false
    }
  })
  const update = useCallback(
    (next: boolean) => {
      setOpen(next)
      try {
        window.localStorage.setItem(`${storageKey}.open`, next ? '1' : '0')
      } catch {
        /* 무시 */
      }
    },
    [storageKey],
  )
  return [open, update]
}
