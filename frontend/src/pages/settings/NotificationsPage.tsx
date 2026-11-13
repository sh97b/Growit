import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Note } from '../../components/Note'
import { Switch } from '../../components/Switch'
import { InfoIcon } from '../../components/icons'
import { ApiError } from '../../lib/api'
import { pickClass } from '../../lib/recordForm'
import {
  disablePush,
  enablePush,
  fetchNotificationSettings,
  sendTestPush,
  updateNotificationSettings,
  type NotificationSettings,
} from '../../lib/push'

const PRESETS: { label: string; time: string }[] = [
  { label: '아침', time: '08:00' },
  { label: '점심', time: '12:30' },
  { label: '저녁', time: '19:00' },
  { label: '자기 전', time: '22:00' },
]

const WEEKDAY_LABELS = ['월', '화', '수', '목', '금', '토', '일']

function isIosSafari(): boolean {
  const ua = navigator.userAgent
  return /iP(hone|ad|od)/.test(ua) && /Safari/.test(ua) && !/CriOS|FxiOS/.test(ua)
}

function isStandalone(): boolean {
  return window.matchMedia?.('(display-mode: standalone)').matches || (navigator as unknown as { standalone?: boolean }).standalone === true
}

/** 구독이 만료·해지됐거나(GONE) 형식이 깨졌을 때(INVALID) — 둘 다 기기 쪽에서 다시 켜야 한다. */
function pushErrorMessage(error: unknown): { message: string; needsReconnect: boolean } {
  if (error instanceof ApiError) {
    if (error.code === 'PUSH_SUBSCRIPTION_GONE' || error.code === 'PUSH_SUBSCRIPTION_INVALID') {
      return { message: '알림 연결이 끊어졌어요. 알림을 껐다 다시 켜 주세요.', needsReconnect: true }
    }
    // PUSH_SEND_FAILED, PUSH_DISABLED, INTERNAL_ERROR 등은 서버 message를 그대로 보여준다.
    return { message: error.message, needsReconnect: false }
  }
  return { message: '지금은 보낼 수 없어요.', needsReconnect: false }
}

export function NotificationsPage() {
  const queryClient = useQueryClient()
  const [advancedOpen, setAdvancedOpen] = useState(false)
  const [permissionState, setPermissionState] = useState<'idle' | 'denied' | 'unsupported'>('idle')
  const [testMessage, setTestMessage] = useState<string | null>(null)
  const [needsReconnect, setNeedsReconnect] = useState(false)

  const settingsQuery = useQuery({ queryKey: ['notifications', 'settings'], queryFn: fetchNotificationSettings })

  const mutation = useMutation({
    mutationFn: (next: NotificationSettings) => updateNotificationSettings(next),
    onSuccess: (data) => queryClient.setQueryData(['notifications', 'settings'], data),
  })

  if (settingsQuery.isLoading || !settingsQuery.data) {
    return null
  }

  const settings = settingsQuery.data

  function save(partial: Partial<NotificationSettings>) {
    mutation.mutate({ ...settings, ...partial })
  }

  async function handleEnable() {
    setPermissionState('idle')
    const result = await enablePush()
    if (result === 'granted') {
      save({ enabled: true })
    } else if (result === 'denied') {
      setPermissionState('denied')
    } else {
      setPermissionState('unsupported')
    }
  }

  async function handleDisableAll() {
    await disablePush()
    save({ enabled: false })
  }

  async function handleTest() {
    setTestMessage(null)
    try {
      await sendTestPush()
      setTestMessage('테스트 알림을 보냈어요.')
    } catch (error) {
      const { message, needsReconnect: reconnect } = pushErrorMessage(error)
      setTestMessage(message)
      if (reconnect) {
        setNeedsReconnect(true)
        await disablePush().catch(() => {
          // 브라우저 쪽 구독 해제가 실패해도(이미 끊어졌을 수 있음) 재연결 흐름은 그대로 보여준다
        })
      }
    }
  }

  async function handleReconnect() {
    setTestMessage(null)
    const result = await enablePush()
    if (result === 'granted') {
      setNeedsReconnect(false)
    } else if (result === 'denied') {
      setPermissionState('denied')
    } else {
      setPermissionState('unsupported')
    }
  }

  function toggleDay(index: number) {
    const chars = settings.daysOfWeek.split('')
    chars[index] = chars[index] === '1' ? '0' : '1'
    save({ daysOfWeek: chars.join('') })
  }

  if (!settings.enabled) {
    return (
      <div className="flex flex-col gap-5 lg:mx-auto lg:max-w-[45rem]">
        <nav aria-label="현재 위치" className="hidden items-center gap-1.5 text-caption-min text-ink-3 lg:flex">
          <span>설정</span>
          <span aria-hidden="true">›</span>
          <span>알림 설정</span>
        </nav>
        <div className="flex flex-col items-start gap-4 pt-6">
          <span className="flex h-14 w-14 items-center justify-center rounded-2xl bg-[color-mix(in_srgb,var(--color-base)_12%,white)] text-base-ink">
            <InfoIcon width={28} height={28} />
          </span>
          <h1 className="text-page-title">알림을 받아 볼까요?</h1>
          <p className="text-body-min text-ink-2">하루 한 번, 정한 시간에만 알려요. 안 쓴 날을 알려주는 알림은 보내지 않아요.</p>

          {permissionState === 'denied' ? (
            <Note variant="sand">브라우저 알림 권한이 꺼져 있어요. 브라우저 설정에서 이 사이트의 알림을 허용해 주세요.</Note>
          ) : null}
          {permissionState === 'unsupported' && isIosSafari() && !isStandalone() ? (
            <Note variant="calm">아이폰에서는 홈 화면에 추가한 뒤에만 알림을 받을 수 있어요(공유 → 홈 화면에 추가).</Note>
          ) : null}
          {permissionState === 'unsupported' && !(isIosSafari() && !isStandalone()) ? (
            <Note variant="calm">이 브라우저에서는 알림을 지원하지 않아요.</Note>
          ) : null}

          <button
            type="button"
            onClick={handleEnable}
            className="inline-flex min-h-11 w-full items-center justify-center rounded-lg bg-base px-5 text-body-min font-medium text-white lg:w-fit"
          >
            알림 받기
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-6 lg:mx-auto lg:max-w-[45rem]">
      <nav aria-label="현재 위치" className="hidden items-center gap-1.5 text-caption-min text-ink-3 lg:flex">
        <span>설정</span>
        <span aria-hidden="true">›</span>
        <span>알림 설정</span>
      </nav>
      <h1 className="text-page-title">알림 설정</h1>

      <div className="flex flex-col gap-3.5">
        <h2 className="text-card-title">몇 시에 알려드릴까요?</h2>
        <div className="flex flex-wrap gap-2">
          {PRESETS.map((preset) => (
            <button
              key={preset.time}
              type="button"
              aria-pressed={settings.timeOfDay === preset.time}
              onClick={() => save({ timeOfDay: preset.time })}
              className={pickClass(settings.timeOfDay === preset.time)}
            >
              {preset.label}
            </button>
          ))}
        </div>
        <div className="flex items-center gap-2.5">
          <label htmlFor="exact-time" className="text-sm text-ink-2">
            정확한 시간
          </label>
          <input
            id="exact-time"
            type="time"
            value={settings.timeOfDay}
            onChange={(event) => save({ timeOfDay: event.target.value })}
            className="h-11 w-[8.75rem] rounded-lg border border-input bg-white px-3 text-body-min text-ink outline-none focus:border-base"
          />
        </div>
      </div>

      <button
        type="button"
        onClick={() => setAdvancedOpen((open) => !open)}
        aria-expanded={advancedOpen}
        className="inline-flex min-h-11 w-fit items-center text-sm font-medium text-base-ink underline underline-offset-4"
      >
        고급 설정 {advancedOpen ? '접기' : ''}
      </button>

      {advancedOpen ? (
        <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
          <div className="flex flex-col gap-2">
            <span className="text-sm font-medium text-ink">요일</span>
            <div className="flex flex-wrap gap-1.5">
              {WEEKDAY_LABELS.map((label, index) => (
                <button
                  key={label}
                  type="button"
                  aria-pressed={settings.daysOfWeek[index] === '1'}
                  onClick={() => toggleDay(index)}
                  className={pickClass(settings.daysOfWeek[index] === '1')}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>

          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-1 rounded-xl bg-surface p-4">
              <span className="text-sm text-ink-2">알림 문구</span>
              <p className="text-ink">오늘 한 줄 남기고 싶을 때 열어 보세요.</p>
            </div>
            <div className="flex min-h-11 items-center justify-between gap-3">
              <span>새 회고가 도착하면 알려 주기</span>
              <Switch
                checked={settings.notifyOnCoachArrival}
                onChange={() => save({ notifyOnCoachArrival: !settings.notifyOnCoachArrival })}
                label="새 회고가 도착하면 알려 주기"
              />
            </div>
          </div>
        </div>
      ) : null}

      {isIosSafari() && !isStandalone() ? (
        <Note variant="calm">아이폰은 홈 화면에 추가한 앱에서만 알림이 와요(공유 → 홈 화면에 추가).</Note>
      ) : null}

      <div className="flex flex-wrap items-center gap-3 border-t border-line pt-4">
        {needsReconnect ? (
          <button
            type="button"
            onClick={handleReconnect}
            className="inline-flex min-h-11 items-center rounded-lg bg-base px-4 text-sm font-medium text-white"
          >
            알림 다시 켜기
          </button>
        ) : (
          <button
            type="button"
            onClick={handleTest}
            className="inline-flex min-h-11 items-center rounded-lg border border-line-2 bg-white px-4 text-sm font-medium text-ink"
          >
            테스트 알림 보내기
          </button>
        )}
        <button type="button" onClick={handleDisableAll} className="inline-flex min-h-11 items-center text-sm font-medium text-ink-2 underline underline-offset-4">
          알림 모두 끄기
        </button>
      </div>
      {permissionState === 'denied' ? (
        <Note variant="sand">브라우저 알림 권한이 꺼져 있어요. 브라우저 설정에서 이 사이트의 알림을 허용해 주세요.</Note>
      ) : null}
      {testMessage ? <Note variant="calm">{testMessage}</Note> : null}
    </div>
  )
}
