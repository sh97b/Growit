import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { Loading } from '../components/Loading'
import { useAuth } from '../lib/auth'
import { exchangeOauthCode } from '../lib/oauth'

/**
 * 콜백이 넘겨준 fragment의 1회용 코드를 읽어 즉시 주소에서 지우고(새로고침·공유 시 남지 않게),
 * exchange로 우리 JWT를 받아 세션에 반영한다. 신규 가입이면 온보딩으로, 아니면 홈으로 보낸다.
 */
export function AuthCallbackPage() {
  const navigate = useNavigate()
  const { completeSocialLogin } = useAuth()
  const startedRef = useRef(false)

  useEffect(() => {
    if (startedRef.current) return
    startedRef.current = true

    const hash = window.location.hash
    const code = new URLSearchParams(hash.startsWith('#') ? hash.slice(1) : hash).get('code')
    window.history.replaceState(null, '', window.location.pathname)

    if (!code) {
      navigate('/login?error=social_failed', { replace: true })
      return
    }

    exchangeOauthCode(code)
      .then(({ accessToken, isNewUser }) => {
        completeSocialLogin(accessToken, isNewUser)
        navigate(isNewUser ? '/onboarding' : '/', { replace: true })
      })
      .catch(() => {
        navigate('/login?error=social_failed', { replace: true })
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return <Loading />
}
