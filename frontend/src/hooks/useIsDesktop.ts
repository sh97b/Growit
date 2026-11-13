import { useEffect, useState } from 'react'

const QUERY = '(min-width: 1024px)'

/** 임의의 미디어 쿼리 매치 여부를 구독한다(LAYOUT-V2: 활동 지도의 1280px 폭 분기 등에 재사용). */
export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(() => window.matchMedia(query).matches)

  useEffect(() => {
    const mediaQuery = window.matchMedia(query)
    const handleChange = () => setMatches(mediaQuery.matches)
    mediaQuery.addEventListener('change', handleChange)
    return () => mediaQuery.removeEventListener('change', handleChange)
  }, [query])

  return matches
}

/** 데스크톱(폭 1024 이상) 여부. U2의 화면별 페이지 크기(데스크톱/모바일)를 정할 때 쓴다. */
export function useIsDesktop(): boolean {
  return useMediaQuery(QUERY)
}
