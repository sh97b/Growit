import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Button } from '../../components/Button'
import { Loading } from '../../components/Loading'
import { Note } from '../../components/Note'
import { SettingsPage, SettingsRow } from '../../components/PageLayout'
import { Switch } from '../../components/Switch'
import { InfoIcon } from '../../components/icons'
import { useCurrentUser, useUpdateAiEnabled } from '../../hooks/useCurrentUser'
import { ApiError } from '../../lib/api'
import { deleteAiData } from '../../lib/ai'

export function AiPage() {
  const { data: currentUser, isLoading } = useCurrentUser()
  const updateAiEnabled = useUpdateAiEnabled()
  const queryClient = useQueryClient()
  const [confirmingClear, setConfirmingClear] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const clearData = useMutation({
    mutationFn: deleteAiData,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['records'] })
      setConfirmingClear(false)
    },
    onError: (error) => setErrorMessage(error instanceof ApiError ? error.message : '지우지 못했어요'),
  })

  if (isLoading || !currentUser) {
    return <Loading />
  }

  function toggleAiEnabled() {
    if (!currentUser) return
    updateAiEnabled.mutate({ displayName: currentUser.displayName ?? '', aiEnabled: !currentUser.aiEnabled })
  }

  return (
    <SettingsPage title="AI 정리">
      {errorMessage ? <Note variant="sand">{errorMessage}</Note> : null}

      <div className="flex flex-col">
        <SettingsRow label="AI 정리 켜기">
          <Switch checked={currentUser.aiEnabled} onChange={toggleAiEnabled} label="AI 정리 켜기" disabled={updateAiEnabled.isPending} />
        </SettingsRow>

        <SettingsRow
          label="AI 정리 결과 지우기"
          description="AI 정리가 만든 태그·한 줄·회고만 지울 수 있어요. 쓴 기록은 그대로 남아요."
        >
          <Button type="button" variant="secondary" size="sm" className="flex-none" onClick={() => setConfirmingClear(true)}>
            지우기
          </Button>
        </SettingsRow>
      </div>

      {currentUser.aiEnabled ? (
        <div className="flex items-start gap-2.5 rounded-2xl bg-surface p-4 text-ink">
          <InfoIcon width={20} height={20} className="flex-none" />
          <span>내 기록은 AI 정리와 회고를 만드는 데 쓰여요. 설정에서 언제든 끌 수 있어요.</span>
        </div>
      ) : (
        <Note variant="sand">
          AI 정리가 꺼져 있어요. 기록만 저장돼요. 켜면 내 기록이 AI 정리와 회고를 만드는 데 쓰여요.
        </Note>
      )}

      {confirmingClear ? (
        <Note variant="sand">
          <div className="flex flex-col gap-3">
            <p>AI 정리 결과를 지울까요? 쓴 기록은 그대로 남아요.</p>
            <div className="flex gap-2">
              <Button type="button" variant="secondary" size="sm" onClick={() => setConfirmingClear(false)}>
                취소
              </Button>
              <Button type="button" size="sm" disabled={clearData.isPending} onClick={() => clearData.mutate()}>
                지우기
              </Button>
            </div>
          </div>
        </Note>
      ) : null}
    </SettingsPage>
  )
}
