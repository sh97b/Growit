import { apiRequest } from './api'

export type NotificationSettings = {
  enabled: boolean
  timeOfDay: string
  daysOfWeek: string
  notifyOnCoachArrival: boolean
}

export function fetchNotificationSettings() {
  return apiRequest<NotificationSettings>('/api/notifications/settings')
}

export function updateNotificationSettings(settings: NotificationSettings) {
  return apiRequest<NotificationSettings>('/api/notifications/settings', { method: 'PUT', body: settings })
}

export function fetchPushPublicKey() {
  return apiRequest<{ enabled: boolean; publicKey: string }>('/api/push/public-key')
}

export function sendTestPush() {
  return apiRequest<void>('/api/push/test', { method: 'POST' })
}

export function isPushSupported(): boolean {
  return 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window
}

function urlBase64ToUint8Array(base64Url: string): Uint8Array {
  const padding = '='.repeat((4 - (base64Url.length % 4)) % 4)
  const base64 = (base64Url + padding).replace(/-/g, '+').replace(/_/g, '/')
  const raw = atob(base64)
  const bytes = new Uint8Array(raw.length)
  for (let i = 0; i < raw.length; i++) {
    bytes[i] = raw.charCodeAt(i)
  }
  return bytes
}

/** 반드시 사용자 클릭 핸들러 안에서 호출한다(권한 요청이 사용자 동작 없이 뜨면 대부분 브라우저가 막는다). */
export async function enablePush(): Promise<'granted' | 'denied' | 'unsupported'> {
  if (!isPushSupported()) {
    return 'unsupported'
  }
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') {
    return 'denied'
  }

  const { enabled, publicKey } = await fetchPushPublicKey()
  if (!enabled) {
    return 'unsupported'
  }

  const registration = await navigator.serviceWorker.ready
  const subscription =
    (await registration.pushManager.getSubscription()) ??
    (await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(publicKey) as BufferSource,
    }))

  const json = subscription.toJSON()
  await apiRequest('/api/push/subscriptions', {
    method: 'POST',
    body: { endpoint: json.endpoint, keys: json.keys },
  })
  return 'granted'
}

export async function disablePush(): Promise<void> {
  if (!isPushSupported()) return
  const registration = await navigator.serviceWorker.ready
  const subscription = await registration.pushManager.getSubscription()
  if (!subscription) return
  const endpoint = subscription.endpoint
  await subscription.unsubscribe()
  await apiRequest('/api/push/subscriptions', { method: 'DELETE', body: { endpoint } })
}
