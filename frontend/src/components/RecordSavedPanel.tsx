import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { Button } from './Button'
import { AiLabel } from './AiLabel'
import { EvidenceChip } from './EvidenceChip'
import { Note } from './Note'
import { CheckIcon, RefreshIcon, ThumbsDownIcon, ThumbsUpIcon } from './icons'
import { ApiError } from '../lib/api'
import { formatMonthDay } from '../lib/date'
import { useReanalyzeRecord, useSubmitAnalysisFeedback } from '../hooks/useRecords'
import type { RecordDetail } from '../lib/records'

const POLL_TIMEOUT_MS = 30_000

export function RecordSavedPanel({
  record,
  hobbyName,
  isFirstRecord,
  onHome,
  onViewDetail,
  onEdit,
}: {
  record: RecordDetail
  hobbyName: string
  isFirstRecord: boolean
  onHome: () => void
  onViewDetail: () => void
  onEdit: () => void
}) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const deadlineRef = useRef<number | null>(null)
  if (deadlineRef.current === null) {
    deadlineRef.current = Date.now() + POLL_TIMEOUT_MS
  }
  const [timedOut, setTimedOut] = useState(false)
  const feedbackMutation = useSubmitAnalysisFeedback()
  const reanalyzeMutation = useReanalyzeRecord()

  // record는 부모(RecordSavedPage)의 useRecord(id)가 폴링하며 계속 갱신해 주는 값을 그대로 받는다.
  // 여기서 따로 같은 키로 또 폴링을 돌리면(과거에 그랬다) 두 폴러가 서로 다른 타임아웃을 들고
  // 따로 움직여 분석이 실제로 끝났는데도 이 화면만 멈춰 보이는 문제가 생길 수 있어 없앴다.
  useEffect(() => {
    if (record.aiStatus !== 'PENDING') return
    const remaining = deadlineRef.current! - Date.now()
    if (remaining <= 0) {
      setTimedOut(true)
      return
    }
    const timer = setTimeout(() => setTimedOut(true), remaining)
    return () => clearTimeout(timer)
  }, [record.aiStatus])

  async function handleRetry() {
    try {
      const updated = await reanalyzeMutation.mutateAsync(record.id)
      queryClient.setQueryData(['records', record.id], updated)
      deadlineRef.current = Date.now() + POLL_TIMEOUT_MS
      setTimedOut(false)
    } catch {
      // 오류 메시지는 reanalyzeMutation.error로 아래에 그대로 보여준다
    }
  }

  function handleChangeFeedback(value: 'HELPFUL' | 'NOT_HELPFUL') {
    if (feedbackMutation.isPending) return
    feedbackMutation.mutate({ id: record.id, payload: { change: value } })
  }

  const showFailed = record.aiStatus === 'FAILED' || (record.aiStatus === 'PENDING' && timedOut)

  return (
    <div className="flex flex-col gap-6 lg:max-w-[760px]">
      <div className="flex flex-wrap items-center gap-2.5">
        <span className="flex h-8 w-8 flex-none items-center justify-center rounded-full bg-[color-mix(in_srgb,var(--color-base)_12%,white)] text-base-ink">
          <CheckIcon width={18} height={18} />
        </span>
        <h1 className="font-serif text-xl font-semibold lg:text-[30px]">기록을 저장했어요</h1>
        <span className="text-[13px] text-ink-2">
          {formatMonthDay(record.recordDate)} · {hobbyName}
        </span>
      </div>

      {showFailed ? (
        <div className="flex flex-col gap-3">
          <Note variant="sand">
            <span>
              <b>기록은 안전하게 저장됐어요.</b>
              <br />
              지금은 AI 한 줄을 만들 수 없었어요.
            </span>
          </Note>
          <button
            type="button"
            disabled={reanalyzeMutation.isPending}
            onClick={handleRetry}
            className="inline-flex min-h-11 w-fit items-center gap-1.5 text-sm font-medium text-base-ink underline underline-offset-4"
          >
            <RefreshIcon width={16} height={16} />
            다시 시도하기
          </button>
          {reanalyzeMutation.isError ? (
            <Note variant="sand">
              {reanalyzeMutation.error instanceof ApiError ? reanalyzeMutation.error.message : '잠시 후 다시 시도해 주세요'}
            </Note>
          ) : null}
        </div>
      ) : record.aiStatus === 'PENDING' ? (
        <div className="flex flex-col gap-3.5 rounded-2xl border border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_9%,white)] p-7">
          <AiLabel>이전 기록과 이어 보고 있어요</AiLabel>
          <div className="h-3.5 w-[88%] animate-pulse rounded-full bg-rest" />
          <div className="h-3.5 w-full animate-pulse rounded-full bg-rest" />
          <p className="text-sm text-ink-2">기록은 이미 저장됐어요.</p>
        </div>
      ) : record.aiStatus === 'DONE' && record.analysis?.change ? (
        <div className="flex flex-col gap-3.5 rounded-2xl border border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_9%,white)] p-7">
          <AiLabel>달라진 점</AiLabel>
          <p className="font-serif text-lg leading-[1.55] lg:text-[24px]">{record.analysis.change.text}</p>
          <div>
            <EvidenceChip record={record.analysis.change.evidence} />
          </div>
          <div className="flex items-center justify-between gap-3 border-t border-[color-mix(in_srgb,var(--color-base)_28%,white)] pt-2">
            <span className="text-sm text-ink">이 한 줄이 도움됐나요?</span>
            <div className="flex gap-2">
              <button
                type="button"
                aria-label="도움이 됐어요"
                aria-pressed={record.analysis.changeFeedback === 'HELPFUL'}
                onClick={() => handleChangeFeedback('HELPFUL')}
                className={[
                  'inline-flex h-11 w-11 items-center justify-center rounded-lg border text-ink hover:bg-[color-mix(in_srgb,var(--color-ink)_6%,white)]',
                  record.analysis.changeFeedback === 'HELPFUL'
                    ? 'border-base bg-[color-mix(in_srgb,var(--color-base)_12%,white)]'
                    : 'border-line-2 bg-white',
                ].join(' ')}
              >
                <ThumbsUpIcon width={20} height={20} />
              </button>
              <button
                type="button"
                aria-label="아쉬워요"
                aria-pressed={record.analysis.changeFeedback === 'NOT_HELPFUL'}
                onClick={() => handleChangeFeedback('NOT_HELPFUL')}
                className={[
                  'inline-flex h-11 w-11 items-center justify-center rounded-lg border text-ink hover:bg-[color-mix(in_srgb,var(--color-ink)_6%,white)]',
                  record.analysis.changeFeedback === 'NOT_HELPFUL'
                    ? 'border-base bg-[color-mix(in_srgb,var(--color-base)_12%,white)]'
                    : 'border-line-2 bg-white',
                ].join(' ')}
              >
                <ThumbsDownIcon width={20} height={20} />
              </button>
            </div>
          </div>
          {record.analysis.changeFeedback === 'NOT_HELPFUL' ? (
            <button
              type="button"
              onClick={onViewDetail}
              className="inline-flex min-h-11 w-fit items-center gap-1.5 self-start text-sm font-medium text-base-ink underline underline-offset-4"
            >
              기록 상세에서 AI 결과 고치기
            </button>
          ) : null}
          {feedbackMutation.isError ? (
            <Note variant="sand">
              {feedbackMutation.error instanceof ApiError ? feedbackMutation.error.message : '잠시 후 다시 시도해 주세요'}
            </Note>
          ) : null}
        </div>
      ) : record.aiStatus === 'DONE' ? (
        isFirstRecord ? (
          <div className="flex flex-col gap-3.5 rounded-2xl border border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_9%,white)] p-7">
            <AiLabel>첫 기록이에요</AiLabel>
            <p className="font-serif text-lg leading-[1.55] lg:text-[24px]">
              오늘 쓴 기록이 첫 기록이에요. 다음 기록에서 이어서 볼게요.
            </p>
          </div>
        ) : (
          <div className="flex flex-col gap-3.5 rounded-2xl border border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_9%,white)] p-7">
            <AiLabel>AI 정리</AiLabel>
            <p className="font-serif text-lg leading-[1.55] lg:text-[24px]">
              기록상 아직 뚜렷하게 달라진 점은 없어요. 다음 기록에서 다시 볼게요.
            </p>
          </div>
        )
      ) : record.aiStatus === 'SKIPPED' ? (
        <div className="flex flex-col gap-1 rounded-2xl bg-surface p-6">
          <p className="text-ink">AI 정리가 꺼져 있어요. 켜면 이전 기록과 이어서 한 줄을 보여드려요.</p>
          <button
            type="button"
            onClick={() => navigate('/settings/ai')}
            className="inline-flex min-h-11 w-fit items-center self-start text-sm font-medium text-base-ink underline underline-offset-4"
          >
            켜기
          </button>
        </div>
      ) : null}

      <div className="flex flex-col gap-2 lg:flex-row lg:self-start">
        <Button type="button" block onClick={onHome} className="lg:w-auto">
          홈으로
        </Button>
        <Button type="button" variant="secondary" block onClick={onViewDetail} className="lg:w-auto">
          기록 상세 보기
        </Button>
        <Button type="button" variant="secondary" block onClick={onEdit} className="lg:w-auto">
          고치기
        </Button>
      </div>
    </div>
  )
}
