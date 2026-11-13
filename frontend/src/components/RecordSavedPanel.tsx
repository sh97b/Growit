import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Button } from './Button'
import { AiLabel } from './AiLabel'
import { ChangeFeedbackRow } from './ChangeFeedbackRow'
import { EvidenceChip } from './EvidenceChip'
import { Note } from './Note'
import { Split } from './PageLayout'
import { RecentWeekStrip } from './RecentWeekStrip'
import { RecordPairCards } from './RecordPairCards'
import { CheckIcon, RefreshIcon } from './icons'
import { ApiError } from '../lib/api'
import { formatMonthDay } from '../lib/date'
import { useReanalyzeRecord } from '../hooks/useRecords'
import { fetchRecord, type RecordAnalysis, type RecordDetail } from '../lib/records'

const COMPARISON_CONTENT_MAX = 120

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
  const reanalyzeMutation = useReanalyzeRecord()

  // 같은 분야의 직전 기록이 있으면 AI를 기다리지 않고 바로 비교 카드를 보여준다(디자인에 없는 추가).
  // ['records', id]는 useRecord가 쓰는 키와 같아서, 나중에 그 기록 상세로 들어가면 캐시를 그대로 쓴다.
  const previousId = record.previousRecord?.id ?? null
  const previousQuery = useQuery({
    queryKey: ['records', previousId],
    queryFn: () => fetchRecord(previousId!),
    enabled: previousId != null,
  })

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

  const showFailed = record.aiStatus === 'FAILED' || (record.aiStatus === 'PENDING' && timedOut)

  const header = (
    <div className="flex flex-wrap items-center gap-2.5">
      <span className="flex h-8 w-8 flex-none items-center justify-center rounded-full bg-[color-mix(in_srgb,var(--color-base)_12%,white)] text-base-ink">
        <CheckIcon width={18} height={18} />
      </span>
      <h1 className="font-serif text-xl font-semibold lg:text-[1.875rem]">기록을 저장했어요</h1>
      <span className="text-caption-min text-ink-2">
        {formatMonthDay(record.recordDate)} · {hobbyName}
      </span>
    </div>
  )

  const actionButtons = (
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
  )

  const change = record.aiStatus === 'DONE' ? (record.analysis?.change ?? null) : null

  // 비교 카드가 없을 때(직전 기록이 없음)만 쓰는, 근거 칩이 딸린 온전한 "달라진 점" 카드.
  const changeBlock = change ? (
    <div className="flex flex-col gap-3.5 rounded-2xl border border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_9%,white)] p-7">
      <AiLabel>달라진 점</AiLabel>
      <p className="font-serif text-lg leading-[1.55] lg:text-[1.5rem]">{change.text}</p>
      <div>
        <EvidenceChip record={change.evidence} />
      </div>
      <ChangeFeedbackRow record={record} analysis={record.analysis as RecordAnalysis} onNotHelpfulAction={onViewDetail} />
    </div>
  ) : null

  // 비교 카드가 이미 같은 근거 기록을 보여주므로, 여기서는 AI 문장과 피드백만 두고 근거 칩은 만들지 않는다.
  const changeSentenceBlock = (
    <div className={['grid-collapse', change ? '' : 'is-collapsed'].join(' ')}>
      <div>
        <div className="flex flex-col gap-3.5 rounded-2xl border border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_9%,white)] p-7">
          <AiLabel>달라진 점</AiLabel>
          {change ? <p className="font-serif text-lg leading-[1.55] lg:text-[1.5rem]">{change.text}</p> : null}
          {change ? (
            <ChangeFeedbackRow record={record} analysis={record.analysis as RecordAnalysis} onNotHelpfulAction={onViewDetail} />
          ) : null}
        </div>
      </div>
    </div>
  )

  // 같은 분야의 직전 기록이 있으면: AI를 기다리지 않고 비교 카드를 바로 보여주고, AI는 도착하면 위에
  // 한 줄로 얹는다. 실패·시간 초과·꺼짐·달라진 점 없음은 비교 카드가 이미 있으니 조용히 사라진다
  // (화면이 완전히 비어 보이는 "실패" 모양은 비교 카드가 없을 때만 쓴다).
  if (previousId != null) {
    // change가 있으면 비교 카드의 왼쪽을 그 근거 기록으로 바꾸고 라벨도 "지난번" 대신 날짜로 바꾼다
    // (AI 문장 쪽에 이미 같은 근거 칩이 있어 아래 비교 카드와 중복으로 보이지 않게).
    const leftSource = change ? change.evidence : previousQuery.data
    const left = leftSource
      ? {
          id: leftSource.id,
          label: change ? formatMonthDay(leftSource.recordDate) : `지난번 · ${formatMonthDay(leftSource.recordDate)}`,
          content: leftSource.content,
          maxLength: COMPARISON_CONTENT_MAX,
        }
      : null

    const main = (
      <>
        {header}

        {changeSentenceBlock}

        {!previousQuery.isLoading || change ? (
          <RecordPairCards
            left={left}
            right={{
              label: `오늘 · ${formatMonthDay(record.recordDate)}`,
              content: record.content,
              maxLength: COMPARISON_CONTENT_MAX,
            }}
          />
        ) : null}

        {record.aiStatus === 'PENDING' && !timedOut ? <p className="text-sm text-ink-2">한 줄 정리 중</p> : null}

        {actionButtons}
      </>
    )

    return (
      <div className="flex flex-col gap-6 lg:min-h-0 lg:flex-1">
        <Split main={main} side={<div className="hidden lg:block"><RecentWeekStrip /></div>} />
      </div>
    )
  }

  const main = (
    <>
      {header}

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
        changeBlock
      ) : record.aiStatus === 'DONE' ? (
        isFirstRecord ? (
          <div className="flex flex-col gap-3.5 rounded-2xl border border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_9%,white)] p-7">
            <AiLabel>첫 기록이에요</AiLabel>
            <p className="font-serif text-lg leading-[1.55] lg:text-[1.5rem]">
              오늘 쓴 기록이 첫 기록이에요. 다음 기록에서 이어서 볼게요.
            </p>
          </div>
        ) : (
          <div className="flex flex-col gap-3.5 rounded-2xl border border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_9%,white)] p-7">
            <AiLabel>AI 정리</AiLabel>
            <p className="font-serif text-lg leading-[1.55] lg:text-[1.5rem]">
              아직 뚜렷하게 달라진 점은 없어요. 다음 기록에서 다시 볼게요.
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

      {actionButtons}
    </>
  )

  return (
    <div className="flex flex-col gap-6 lg:min-h-0 lg:flex-1">
      <Split main={main} side={<div className="hidden lg:block"><RecentWeekStrip /></div>} />
    </div>
  )
}
