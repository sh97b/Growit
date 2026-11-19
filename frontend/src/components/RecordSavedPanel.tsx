import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Button } from './Button'
import { AiLabel } from './AiLabel'
import { AiProgressCard } from './AiProgress'
import { ChangeFeedbackRow } from './ChangeFeedbackRow'
import { NextActionCard } from './NextActionCard'
import { Split } from './PageLayout'
import { RecentWeekStrip } from './RecentWeekStrip'
import { RecordPairCards } from './RecordPairCards'
import { CheckIcon } from './icons'
import { formatMonthDay } from '../lib/date'
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
  const deadlineRef = useRef<number | null>(null)
  if (deadlineRef.current === null) {
    deadlineRef.current = Date.now() + POLL_TIMEOUT_MS
  }
  const [timedOut, setTimedOut] = useState(false)

  // 같은 분야의 직전 기록이 있으면 AI를 기다리지 않고 바로 비교 카드를 보여준다(디자인에 없는 추가).
  // ['records', id]는 useRecord가 쓰는 키와 같아서, 나중에 그 기록 상세로 들어가면 캐시를 그대로 쓴다.
  // T2 3번(c): previousRecord.id를 한 번이라도 확인하면 이 화면에 머무는 동안 고정한다(ref). 캐시가
  // 나중에 어떤 이유로든 previousRecord를 잃어도(병합 누락 등) "첫 기록이에요"로 되돌아가지 않는다.
  const confirmedPreviousIdRef = useRef<number | null>(null)
  if (confirmedPreviousIdRef.current == null && record.previousRecord?.id != null) {
    confirmedPreviousIdRef.current = record.previousRecord.id
  }
  const previousId = confirmedPreviousIdRef.current ?? record.previousRecord?.id ?? null
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

  // 오프라인 저장 화면이 아니라 서버에 저장된 기록의 화면이라, AI가 만든 제안이 있을 때만 보인다(없으면 카드 없음).
  const savedNextAction = record.aiStatus === 'DONE' ? (record.analysis?.nextAction ?? null) : null
  // 이미 해 봤거나 보류한 제안은 다시 내밀지 않는다(상태가 비어 있을 때만 보인다).
  const nextAction = savedNextAction && savedNextAction.state == null ? savedNextAction : null
  const nextActionBlock = nextAction ? <NextActionCard action={nextAction} /> : null
  // AI가 정리하는 동안(시간 초과 전까지)은 "달라진 점"·"다음에 해 볼 일" 자리에 눈에 띄는 진행 카드를 보여 준다.
  // 실패·꺼짐·시간 초과는 지금처럼 조용히 사라진다.
  const aiWorking = record.aiStatus === 'PENDING' && !timedOut
  const progressBlock = aiWorking ? <AiProgressCard message="AI가 기록을 살펴보고 있어요" lines={3} /> : null
  const nextActionInMain = nextActionBlock ? <div className="lg:hidden">{nextActionBlock}</div> : null
  // 웹: 오른쪽 열이 최근 7일만 있어 비어 보여서 카드를 여기로 옮긴다. 간격은 왼쪽 열(gap-5)과 같게 둔다.
  const side = (
    <div className="hidden lg:flex lg:flex-col lg:gap-5">
      <RecentWeekStrip />
      {nextActionBlock}
      {aiWorking ? (
        <div aria-hidden="true" className="h-32 animate-pulse rounded-2xl bg-surface motion-reduce:animate-none" />
      ) : null}
    </div>
  )

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

        {progressBlock}

      {nextActionInMain}

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

        {progressBlock}

        {actionButtons}

        {/* 모바일(폭 1024 미만)에도 최근 7일을 보여준다(T1-2) — 안내 카드 아래, 버튼 줄 아래. */}
        <div className="lg:hidden">
          <RecentWeekStrip />
        </div>
      </>
    )

    return (
      <div className="flex flex-col gap-6 lg:min-h-0 lg:flex-1">
        <Split
          main={main}
          side={side}
          className="lg:min-h-0 lg:flex-1"
        />
      </div>
    )
  }

  // T1-1: 같은 분야에 이전 기록이 없으면(previousId == null) AI 한 줄을 기다리지 않는다.
  // PENDING 로딩·폴링·FAILED 재시도·SKIPPED 안내를 전부 건너뛰고 저장 즉시 이 안내 하나만 보여준다
  // (서비스 전체 첫 기록이든 분야의 첫 기록이든 같은 문구 — isFirstRecord는 더 이상 분기에 안 쓴다).
  void isFirstRecord
  const main = (
    <>
      {header}

      <div className="flex flex-col gap-3.5 rounded-2xl border border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_9%,white)] p-7">
        <AiLabel>첫 기록이에요</AiLabel>
        <p className="font-serif text-lg leading-[1.55] lg:text-[1.5rem]">
          오늘 쓴 기록이 첫 기록이에요.
          <br />
          다음 기록에서 이어서 볼게요.
        </p>
      </div>

      {nextActionInMain}

      {actionButtons}

      {/* 모바일(폭 1024 미만)에도 최근 7일을 보여준다(T1-2) — 안내 카드 아래, 버튼 줄 아래. */}
      <div className="lg:hidden">
        <RecentWeekStrip />
      </div>
    </>
  )

  return (
    <div className="flex flex-col gap-6 lg:min-h-0 lg:flex-1">
      <Split
        main={main}
        side={side}
        className="lg:min-h-0 lg:flex-1"
      />
    </div>
  )
}
