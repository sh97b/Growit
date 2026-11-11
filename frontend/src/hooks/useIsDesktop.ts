import { useEffect, useState } from 'react'

const QUERY = '(min-width: 1024px)'

/** 데스크톱(폭 1024 이상) 여부. U2의 화면별 페이지 크기(데스크톱/모바일)를 정할 때 쓴다. */
export function useIsDesktop(): boolean {
  const [isDesktop, setIsDesktop] = useState(() => window.matchMedia(QUERY).matches)

  useEffect(() => {
    const mediaQuery = window.matchMedia(QUERY)
    const handleChange = () => setIsDesktop(mediaQuery.matches)
    mediaQuery.addEventListener('change', handleChange)
    return () => mediaQuery.removeEventListener('change', handleChange)
  }, [])

  return isDesktop
}
