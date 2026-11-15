import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { PlusIcon } from './icons'

const HIDDEN_PATTERNS = [/^\/records\/new$/, /^\/records\/\d+\/edit$/, /^\/records\/\d+\/saved$/]

/**
 * S1 C-3단계: 하단 탭바에서 "기록하기" 알약이 빠진 자리를 대신하는, 모든 모바일 화면(기록 작성·수정·
 * 저장 직후·온보딩 제외)에 떠 있는 둥근 버튼. 키보드가 올라오면(visualViewport 높이가 줄면) 숨긴다
 * — 입력 중에 버튼이 키보드 위에 떠 화면을 가리는 것을 막는다. 온보딩은 애초에 AppLayout 밖의
 * 라우트라 이 컴포넌트 자체가 렌더되지 않는다.
 */
export function RecordFab() {
  const location = useLocation()
  const [keyboardOpen, setKeyboardOpen] = useState(false)

  useEffect(() => {
    const viewport = window.visualViewport
    if (!viewport) return
    const initialHeight = viewport.height
    function handleResize() {
      if (!viewport) return
      setKeyboardOpen(viewport.height < initialHeight * 0.75)
    }
    viewport.addEventListener('resize', handleResize)
    return () => viewport.removeEventListener('resize', handleResize)
  }, [])

  const hidden = keyboardOpen || HIDDEN_PATTERNS.some((pattern) => pattern.test(location.pathname))
  if (hidden) return null

  return (
    <Link
      to="/records/new"
      aria-label="기록하기"
      className="fixed z-20 flex h-14 w-14 items-center justify-center rounded-full bg-base text-white no-underline shadow-lg lg:hidden"
      style={{
        right: '1rem',
        bottom: 'calc(3.5rem + env(safe-area-inset-bottom) + 1rem)',
      }}
    >
      <PlusIcon width={26} height={26} />
    </Link>
  )
}
