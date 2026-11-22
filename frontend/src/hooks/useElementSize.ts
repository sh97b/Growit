import { useLayoutEffect, useRef, useState } from 'react'

/** 요소의 실제 폭·높이(px)를 ResizeObserver로 잰다. 차트가 컨테이너를 꽉 채워 그릴 때 쓴다. */
export function useElementSize<T extends HTMLElement>() {
  const ref = useRef<T | null>(null)
  const [size, setSize] = useState({ width: 0, height: 0 })

  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return
    const observer = new ResizeObserver((entries) => {
      const rect = entries[0].contentRect
      const width = Math.round(rect.width)
      const height = Math.round(rect.height)
      setSize((prev) => (prev.width === width && prev.height === height ? prev : { width, height }))
    })
    observer.observe(el)
    return () => observer.disconnect()
  }, [])

  return { ref, width: size.width, height: size.height }
}
