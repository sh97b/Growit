import { apiRequest } from './api'

export type OauthProvider = 'KAKAO' | 'GOOGLE'

export type OauthProviderStatus = { provider: OauthProvider; enabled: boolean }

export function fetchOauthProviders() {
  return apiRequest<OauthProviderStatus[]>('/api/auth/oauth/providers', { auth: false })
}

export function oauthStartUrl(provider: OauthProvider): string {
  return `${import.meta.env.VITE_API_BASE_URL}/api/auth/oauth/${provider.toLowerCase()}/start`
}

export type OauthExchangeResponse = { accessToken: string; isNewUser: boolean }

export function exchangeOauthCode(code: string) {
  return apiRequest<OauthExchangeResponse>('/api/auth/oauth/exchange', {
    method: 'POST',
    body: { code },
    auth: false,
  })
}
