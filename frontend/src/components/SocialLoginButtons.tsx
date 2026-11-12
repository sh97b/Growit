import { useQuery } from '@tanstack/react-query'
import { fetchOauthProviders, oauthStartUrl, type OauthProvider } from '../lib/oauth'

function startSocialLogin(provider: OauthProvider) {
  window.location.href = oauthStartUrl(provider)
}

/** 106~109 보드의 소셜 버튼. 애플은 지시에 따라 만들지 않는다. 꺼져 있는 제공자는 버튼 자체가 안 보인다. */
export function SocialLoginButtons() {
  const { data } = useQuery({ queryKey: ['oauth', 'providers'], queryFn: fetchOauthProviders })

  const kakaoEnabled = data?.some((p) => p.provider === 'KAKAO' && p.enabled) ?? false
  const googleEnabled = data?.some((p) => p.provider === 'GOOGLE' && p.enabled) ?? false

  if (!kakaoEnabled && !googleEnabled) return null

  return (
    <div className="flex flex-col gap-3.5">
      <div className="flex items-center gap-3">
        <span className="h-px flex-1 bg-line" />
        <span className="text-sm text-ink-3">또는</span>
        <span className="h-px flex-1 bg-line" />
      </div>
      <div className="flex gap-2">
        {kakaoEnabled ? (
          <button
            type="button"
            aria-label="카카오로 계속하기"
            onClick={() => startSocialLogin('KAKAO')}
            className="flex min-h-11 flex-1 items-center justify-center gap-1.5 rounded-lg px-2 text-body-min font-semibold"
            style={{ background: '#FEE500', color: '#191919' }}
          >
            <svg width={18} height={18} viewBox="0 0 24 24" aria-hidden="true">
              <path
                fill="#191919"
                d="M12 3C6.48 3 2 6.58 2 11c0 2.84 1.87 5.33 4.69 6.75l-.95 3.5c-.08.3.26.54.52.37l4.13-2.74c.52.07 1.05.12 1.61.12 5.52 0 10-3.58 10-8S17.52 3 12 3z"
              />
            </svg>
            카카오
          </button>
        ) : null}
        {googleEnabled ? (
          <button
            type="button"
            aria-label="Google로 계속하기"
            onClick={() => startSocialLogin('GOOGLE')}
            className="flex min-h-11 flex-1 items-center justify-center gap-1.5 rounded-lg border border-line-2 px-2 text-body-min font-semibold"
            style={{ background: '#fff', color: '#1F1F1F' }}
          >
            <svg width={18} height={18} viewBox="0 0 24 24" aria-hidden="true">
              <path
                fill="#4285F4"
                d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
              />
              <path
                fill="#34A853"
                d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
              />
              <path
                fill="#FBBC05"
                d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
              />
              <path
                fill="#EA4335"
                d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
              />
            </svg>
            Google
          </button>
        ) : null}
      </div>
    </div>
  )
}
