import { useId, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { AiLabel } from './AiLabel'
import { FeedbackRow } from './FeedbackRow'
import { Note } from './Note'
import { ApiError } from '../lib/api'
import {
  simplifyCoachAction,
  submitAiResultFeedback,
  submitCoachReaction,
  type CoachEvidenceItem,
  type CoachResult,
  type CoachStats,
  type CoachTextItem,
} from '../lib/ai'
import { reactionLine } from '../lib/coachReaction'
import { formatMonthDay } from '../lib/date'

export function EvidenceRow({ evidence, collapsible = false }: { evidence: CoachEvidenceItem[]; collapsible?: boolean }) {
  const [open, setOpen] = useState(false)
  const listId = useId()
  if (evidence.length === 0) return null
  const chips = (
    <div id={listId} className="flex flex-wrap gap-2">
      {evidence.map((item) => (
        <Link
          key={item.recordId}
          to={`/records/${item.recordId}`}
          title={item.content}
          className="inline-flex min-h-11 max-w-full items-center gap-2 rounded-lg border border-line-2 bg-white px-3 text-caption-min text-ink no-underline"
        >
          <span className="flex-none font-semibold text-base-ink">{formatMonthDay(item.recordDate)}</span>
          <span className="min-w-0 overflow-hidden text-ellipsis whitespace-nowrap text-ink-2">{item.content}</span>
        </Link>
      ))}
    </div>
  )
  if (!collapsible) return chips
  // 회고 화면: 근거 기록은 기본적으로 접어 두고 눌러서 펼친다.
  return (
    <div className="flex flex-col gap-2">
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
        aria-controls={listId}
        className="inline-flex min-h-11 w-fit items-center gap-1 rounded-full bg-transparent px-1 text-caption-min font-medium text-ink-2 hover:text-ink"
      >
        근거 {evidence.length}개 <span aria-hidden="true">{open ? '▴' : '▾'}</span>
      </button>
      {open ? chips : null}
    </div>
  )
}

/** P2: 회고 상세의 섹션 카드 한 겹(흰 배경, 옅은 테두리, 모서리 토큰, 안쪽 여백 1.25rem). */
export const RESULT_CARD_CLASS = 'rounded-2xl border border-line bg-white p-5'

/** 문장 앞에 이미 같은 날짜가 적혀 있으면 화면에서만 지운다(왼쪽 날짜 열과 겹쳐 보이지 않게). 문장 중간의 날짜는 그대로 둔다. */
function stripLeadingDate(text: string, date: string | undefined): string {
  if (!date) return text
  const label = formatMonthDay(date)
  if (!text.startsWith(label)) return text
  const rest = text.slice(label.length).replace(/^[\s,·:-]+/, '')
  return rest.length > 0 ? rest : text
}

/** 흐름 한 줄: 문장 끝 줄 오른쪽에 "근거 N개 ▾"를 두고, 펼치면 아래에 근거 칩이 나온다. */
function TimelineLine({ item, date }: { item: CoachTextItem; date: string | undefined }) {
  const [open, setOpen] = useState(false)
  const listId = useId()
  return (
    <div className="flex min-w-0 flex-col gap-2">
      <div className="flex min-w-0 items-end justify-between gap-3">
        <p className="min-w-0 text-body-min leading-[1.7] text-ink">{stripLeadingDate(item.text, date)}</p>
        {item.evidence.length > 0 ? (
          <button
            type="button"
            onClick={() => setOpen((value) => !value)}
            aria-expanded={open}
            aria-controls={listId}
            className="inline-flex min-h-11 flex-none items-center gap-1 rounded-full bg-transparent px-1 text-caption-min font-medium text-ink-2 hover:text-ink lg:min-h-8"
          >
            근거 {item.evidence.length}개 <span aria-hidden="true">{open ? '▴' : '▾'}</span>
          </button>
        ) : null}
      </div>
      {open ? (
        <div id={listId}>
          <EvidenceRow evidence={item.evidence} />
        </div>
      ) : null}
    </div>
  )
}

/** "이 기간에 한 일": 날짜는 한 번만(웹은 왼쪽 열, 모바일은 문장 위), 같은 날은 묶고 줄 사이는 연한 구분선으로 나눈다. */
function TimelineList({ items }: { items: CoachTextItem[] }) {
  const groups: { date: string | undefined; lines: CoachTextItem[] }[] = []
  for (const item of items) {
    const date = item.evidence[0]?.recordDate
    const last = groups[groups.length - 1]
    if (last && last.date === date) last.lines.push(item)
    else groups.push({ date, lines: [item] })
  }
  return (
    <ul className="m-0 flex list-none flex-col p-0">
      {groups.map((group, index) => (
        <li
          key={`${group.date ?? 'none'}-${index}`}
          className="flex flex-col gap-1 border-t border-line py-3 first:border-t-0 first:pt-0 lg:grid lg:grid-cols-[6rem_minmax(0,1fr)] lg:gap-4"
        >
          <span className="text-caption-min font-semibold text-base-ink lg:pt-1.5">
            {group.date ? formatMonthDay(group.date) : ''}
          </span>
          <div className="flex min-w-0 flex-col gap-1">
            {group.lines.map((line, lineIndex) => (
              <TimelineLine key={lineIndex} item={line} date={group.date} />
            ))}
          </div>
        </li>
      ))}
    </ul>
  )
}

/*
 * 항목은 카드 안에서 배경 없는 평문(16px, 줄 높이 1.7)과 그 아래 근거 칩 한 줄로만 구성한다(카드
 * 안에 또 테두리 카드를 두지 않는다). 섹션 제목은 섹션 제목 토큰(20px)을 쓴다.
 *
 * S1 D-5단계: 모바일에서는 기본 접힘(제목 줄 44px 버튼)인 접이식으로 만들고, 데스크톱은 이전처럼
 * 항상 펼쳐 둔다(펼침 상태와 무관하게 lg: 접두사로 항상 보여주는 블록을 따로 둬 데스크톱 동작을
 * 전혀 바꾸지 않는다).
 */
function TextItemBlock({
  title,
  items,
  note,
  showDate = false,
}: {
  title: string
  items: CoachTextItem[]
  /** 제목 아래의 짧은 안내 한 줄. */
  note?: string
  /** 흐름처럼 각 문장 앞에 근거 기록의 날짜를 붙인다. */
  showDate?: boolean
}) {
  const [open, setOpen] = useState(false)
  const contentId = useId()
  if (items.length === 0) return null
  const content = showDate ? (
    <TimelineList items={items} />
  ) : (
    <>
      {items.map((item, index) => (
        <div key={index} className="flex flex-col gap-2">
          <p className="text-body-min leading-[1.7] text-ink">{item.text}</p>
          <EvidenceRow evidence={item.evidence} collapsible />
        </div>
      ))}
    </>
  )
  return (
    <div className={`flex flex-col gap-3 ${RESULT_CARD_CLASS}`}>
      <button
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        aria-expanded={open}
        aria-controls={contentId}
        className="flex min-h-11 w-full items-center justify-between gap-2 bg-transparent p-0 text-left lg:hidden"
      >
        <h2 className="text-section-title">{title}</h2>
        <span aria-hidden="true" className="text-caption-min text-ink-2">
          {open ? '▴' : '▾'}
        </span>
      </button>
      <h2 className="hidden text-section-title lg:block">{title}</h2>
      <div id={contentId} className={open ? 'flex flex-col gap-3' : 'hidden lg:flex lg:flex-col lg:gap-3'}>
        {note ? <p className="text-caption-min text-ink-2">{note}</p> : null}
        {content}
      </div>
    </div>
  )
}

/**
 * 다음 작은 행동(또는 복귀 행동) 카드. 해봤어요·오늘은 쉴게요는 반응만 남기는 낙관적 갱신이고,
 * 너무 어려워요는 실제로 더 작은 행동을 받아오는 호출(POST .../simplify)이라 결과가 통째로 바뀐다.
 * 그래서 상태를 이 컴포넌트 안에 두지 않고 result 전체를 부모에게서 받아 onResultChange로만 바꾼다.
 */
export function CoachActionCard({
  result,
  comeback,
  onResultChange,
  hideReactions = false,
}: {
  result: CoachResult
  comeback: boolean
  onResultChange: (updated: CoachResult) => void
  /** 홈의 만료(EXPIRED) 회고 카드처럼, 행동 내용은 보여주되 반응 버튼만 숨길 때 쓴다(P1 5단계). */
  hideReactions?: boolean
}) {
  const action = comeback ? result.comebackAction : result.nextAction
  const [simplifyError, setSimplifyError] = useState<string | null>(null)
  const reactionPendingRef = useRef(false)
  const simplifyPendingRef = useRef(false)

  const reactionMutation = useMutation({
    mutationFn: ({ value }: { value: 'TRIED' | 'REST_TODAY' }) => submitCoachReaction(result.id!, value),
  })
  const simplifyMutation = useMutation({
    mutationFn: () => simplifyCoachAction(result.id!),
  })

  if (!action) return null

  function handleReaction(value: 'TRIED' | 'REST_TODAY') {
    if (!result.id || reactionPendingRef.current) return
    reactionPendingRef.current = true
    const previous = result.reaction
    onResultChange({ ...result, reaction: value })
    reactionMutation.mutate(
      { value },
      {
        onSettled: () => {
          reactionPendingRef.current = false
        },
        onError: () => onResultChange({ ...result, reaction: previous }),
      },
    )
  }

  function handleSimplify() {
    if (!result.id || simplifyPendingRef.current) return
    simplifyPendingRef.current = true
    setSimplifyError(null)
    simplifyMutation.mutate(undefined, {
      onSuccess: (updated) => onResultChange(updated),
      onError: (error) => {
        if (error instanceof ApiError && error.code === 'AI_ALREADY_SIMPLIFIED') {
          // 이미 한 번 줄인 결과에서 또 너무 어렵다고 한 경우: 더 줄이는 대신 쉬어가는 상태로 끝맺는다.
          setSimplifyError(error.message)
          submitCoachReaction(result.id!, 'TOO_HARD').then((updated) => onResultChange(updated))
          return
        }
        setSimplifyError(error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요')
      },
      onSettled: () => {
        simplifyPendingRef.current = false
      },
    })
  }

  return (
    <div className="flex flex-col gap-3.5 rounded-2xl border border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_9%,white)] p-5">
      <AiLabel>{comeback ? '가볍게 다시 시작하려면' : '다음 작은 행동'}</AiLabel>
      <p className="text-section-title">{action.title}</p>
      <span className="text-caption-min text-ink-2">{action.minutes}분 · {action.reason}</span>
      <EvidenceRow evidence={action.evidence} collapsible />
      {hideReactions ? null : result.reaction === 'TRIED' || result.reaction === 'REST_TODAY' ? (
        <p className="t-sub text-ink-2">{reactionLine(result.reaction, result.id ?? 0)}</p>
      ) : (
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => handleReaction('TRIED')}
            className="inline-flex min-h-11 flex-none items-center justify-center whitespace-nowrap rounded-lg border border-line-2 bg-white px-4 t-sub font-medium text-ink hover:bg-[color-mix(in_srgb,var(--color-ink)_6%,white)]"
          >
            해봤어요
          </button>
          <button
            type="button"
            onClick={() => handleReaction('REST_TODAY')}
            className="inline-flex min-h-11 flex-none items-center justify-center whitespace-nowrap rounded-lg border border-line-2 bg-white px-4 t-sub font-medium text-ink hover:bg-[color-mix(in_srgb,var(--color-ink)_6%,white)]"
          >
            오늘은 쉴게요
          </button>
          <button
            type="button"
            onClick={handleSimplify}
            className="inline-flex min-h-11 flex-none items-center justify-center whitespace-nowrap rounded-lg border border-line-2 bg-white px-4 t-sub font-medium text-ink hover:bg-[color-mix(in_srgb,var(--color-ink)_6%,white)]"
          >
            너무 어려워요
          </button>
        </div>
      )}
      {simplifyError ? <Note variant="sand">{simplifyError}</Note> : null}
    </div>
  )
}

type NumberedCard = { text: string; steps?: string[]; evidence: CoachEvidenceItem[] }

/** 번호 원형 배지가 붙은 카드 목록(질문·방향 후보 공용). 카드마다 연한 배경과 여백으로 구분한다. */
function NumberedSection({ title, note, cards }: { title: string; note?: string; cards: NumberedCard[] }) {
  return (
    <div className={`flex flex-col gap-3 ${RESULT_CARD_CLASS}`}>
      <h2 className="text-section-title">{title}</h2>
      {note ? <p className="text-caption-min text-ink-2">{note}</p> : null}
      <ol className="m-0 flex list-none flex-col gap-4 p-0">
        {cards.map((card, index) => (
          <li
            key={index}
            className="flex gap-3 rounded-xl bg-[color-mix(in_srgb,var(--color-base)_7%,white)] p-4"
          >
            <span
              aria-hidden="true"
              className="inline-flex h-7 w-7 flex-none items-center justify-center rounded-full bg-base t-sub font-semibold text-white"
            >
              {index + 1}
            </span>
            <div className="flex min-w-0 flex-1 flex-col gap-2">
              <p className="text-body-min font-semibold leading-[1.7] text-ink">{card.text}</p>
              {card.steps && card.steps.length > 0 ? (
                <>
                  <p className="text-caption-min font-medium text-ink-2">이렇게 시작해 볼 수 있어요</p>
                  <ul className="m-0 flex list-disc flex-col gap-1 pl-5 text-caption-min leading-[1.6] text-ink-2">
                    {card.steps.map((step, stepIndex) => (
                      <li key={stepIndex}>{step}</li>
                    ))}
                  </ul>
                </>
              ) : null}
              <EvidenceRow evidence={card.evidence} collapsible />
            </div>
          </li>
        ))}
      </ol>
    </div>
  )
}

/** 방향 후보 + 시작 제안. 후보가 하나도 없으면 섹션을 비우지 않고 안내 한 줄을 보여 준다. */
function DirectionsBlock({ result }: { result: CoachResult }) {
  const plans = result.directionPlans ?? []
  const sentences = result.directions ?? []
  const title = '앞으로의 방향 후보'
  const note = '고르는 건 내 몫이에요. 마음이 가는 쪽만 보면 돼요.'
  if (plans.length === 0 && sentences.length > 0) {
    return (
      <NumberedSection
        title={title}
        note={note}
        cards={sentences.map((item) => ({ text: item.text, evidence: item.evidence }))}
      />
    )
  }
  if (plans.length === 0) {
    return (
      <div className={`flex flex-col gap-3 ${RESULT_CARD_CLASS}`}>
        <h2 className="text-section-title">{title}</h2>
        <p className="text-body-min leading-[1.7] text-ink-2">기록이 조금 더 쌓이면 방향을 제안해 드릴게요.</p>
      </div>
    )
  }
  return (
    <NumberedSection
      title={title}
      note={note}
      cards={plans.map((plan) => ({ text: plan.direction, steps: plan.steps, evidence: plan.evidence }))}
    />
  )
}

/** 기간 요약 문장(크고 굵은 세리프)과, 그 근거가 된 기록을 접었다 펼 수 있는 줄. */
function SummaryBlock({ result }: { result: CoachResult }) {
  const [open, setOpen] = useState(false)
  const listId = useId()
  const sections = [result.flow, result.changes, result.repeatedDifficulties, result.resolvedDifficulties]
  const byId = new Map<number, CoachEvidenceItem>()
  for (const section of sections) {
    for (const item of section ?? []) {
      for (const evidence of item.evidence) byId.set(evidence.recordId, evidence)
    }
  }
  const records = [...byId.values()].sort((a, b) => a.recordDate.localeCompare(b.recordDate))
  return (
    <div className={`flex flex-col gap-2 ${RESULT_CARD_CLASS}`}>
      <p className="t-key text-ink">
        {result.periodSummary}
      </p>
      {records.length > 0 ? (
        <>
          <button
            type="button"
            aria-expanded={open}
            aria-controls={listId}
            onClick={() => setOpen((prev) => !prev)}
            className="inline-flex min-h-11 w-fit items-center gap-1.5 bg-transparent p-0 text-caption-min font-medium text-ink-2"
          >
            근거 {records.length}개
            <span aria-hidden="true">{open ? '▴' : '▾'}</span>
          </button>
          <ul id={listId} hidden={!open} className="m-0 flex list-none flex-col gap-1 p-0">
            {records.map((record) => (
              <li key={record.recordId} className="t-cap text-ink-3">
                <Link to={`/records/${record.recordId}`} className="text-ink-3 no-underline">
                  <span className="font-medium">{formatMonthDay(record.recordDate)}</span>{' '}
                  <span className="line-clamp-1">{record.content}</span>
                </Link>
              </li>
            ))}
          </ul>
        </>
      ) : null}
    </div>
  )
}

function formatMinutes(total: number): string {
  const hours = Math.floor(total / 60)
  const minutes = total % 60
  if (hours === 0) return `${minutes}분`
  return minutes === 0 ? `${hours}시간` : `${hours}시간 ${minutes}분`
}

/** 기록한 날·쌓인 시간·감정 분포. 서버가 센 값을 그대로 보여 준다(순위나 비교 없이 담백하게). */
function StatsLine({ stats }: { stats: CoachStats }) {
  const parts = [`기록한 날 ${stats.recordedDays}일`]
  if (stats.totalMinutes > 0) parts.push(`쌓인 시간 ${formatMinutes(stats.totalMinutes)}`)
  if (stats.feelings.length > 0) {
    parts.push(`감정 ${stats.feelings.map((item) => `${item.label} ${item.count}`).join(' · ')}`)
  }
  return <p className={`text-body-min leading-[1.7] text-ink-2 ${RESULT_CARD_CLASS}`}>{parts.join(' · ')}</p>
}

/** 새 구성: ① 기간 돌아보기 ② 스스로 생각해 볼 질문 ③ 앞으로의 방향 후보. 다음 행동은 기록 직후의 카드가 맡는다. */
function ReflectionBody({ result }: { result: CoachResult }) {
  return (
    <>
      {result.stats ? <StatsLine stats={result.stats} /> : null}
      <TextItemBlock title="이 기간에 한 일" items={result.flow ?? []} showDate />
      <TextItemBlock title="새로 해 본 것" items={result.changes} />
      <TextItemBlock title="반복된 어려움" items={result.repeatedDifficulties} />
      <TextItemBlock title="풀린 것" items={result.resolvedDifficulties ?? []} />
      {(result.questions ?? []).length > 0 ? (
        <NumberedSection
          title="스스로 생각해 볼 질문"
          note="정답은 없어요. 떠오르는 대로 생각해 보세요."
          cards={(result.questions ?? []).map((item) => ({ text: item.text, evidence: item.evidence }))}
        />
      ) : null}
      <DirectionsBlock result={result} />
    </>
  )
}

export function CoachResultView({
  result,
  onResultChange,
}: {
  result: CoachResult
  onResultChange: (updated: CoachResult) => void
}) {
  const feedbackPendingRef = useRef(false)
  const [feedbackError, setFeedbackError] = useState<string | null>(null)
  const feedbackMutation = useMutation({
    mutationFn: ({ value }: { value: 'HELPFUL' | 'NOT_HELPFUL' }) =>
      submitAiResultFeedback<CoachResult>(result.id!, value),
  })

  function handleFeedback(value: 'HELPFUL' | 'NOT_HELPFUL') {
    if (!result.id || feedbackPendingRef.current) return
    feedbackPendingRef.current = true
    setFeedbackError(null)
    const previous = result.feedback
    onResultChange({ ...result, feedback: value })
    feedbackMutation.mutate(
      { value },
      {
        onError: (mutationError) => {
          onResultChange({ ...result, feedback: previous })
          setFeedbackError(mutationError instanceof ApiError ? mutationError.message : '잠시 후 다시 시도해 주세요')
        },
        onSettled: () => {
          feedbackPendingRef.current = false
        },
      },
    )
  }

  if (result.schemaVersion === 2) {
    return (
      <div className="flex flex-col gap-4 lg:max-w-[48rem]">
        <div className="flex flex-wrap items-center gap-2 text-caption-min text-ink-2">
          {result.startDate && result.endDate ? (
            <span>
              {formatMonthDay(result.startDate)} – {formatMonthDay(result.endDate)} · 전체 분야
              {result.recordsRead != null ? ` · 기록 ${result.recordsRead}개 읽음` : ''}
            </span>
          ) : null}
          {result.isNew ? (
            <span className="inline-flex h-5 items-center rounded-md bg-[color-mix(in_srgb,var(--color-base)_16%,white)] px-1.5 t-cap font-medium text-base-ink">
              새로 도착
            </span>
          ) : null}
        </div>

        <SummaryBlock result={result} />

        <ReflectionBody result={result} />

        <FeedbackRow
          question="이 회고가 도움됐나요?"
          status={result.feedback === 'HELPFUL' ? 'positive' : result.feedback === 'NOT_HELPFUL' ? 'negative' : null}
          pending={feedbackMutation.isPending}
          onPositive={() => handleFeedback('HELPFUL')}
          onNegative={() => handleFeedback('NOT_HELPFUL')}
          errorMessage={feedbackError}
        />
      </div>
    )
  }

  // P1 4단계: 데스크톱은 한 열 문서 구성(최대 44rem) — 메타 줄 → AI 회고 한 줄(카드 배경 없음) →
  // 섹션들 → 다음 작은 행동(유일한 강조 카드) → 맨 아래 피드백.
  // S1 D-5단계: 모바일은 다음 작은 행동을 맨 위(강조)로 올린다 — DOM 순서(데스크톱 순서)는 그대로
  // 두고 CSS order만 모바일에서 바꾼다(lg: 값은 원래 DOM 순서와 같게 둬 데스크톱은 그대로).
  return (
    <div className="flex flex-col gap-4 lg:max-w-[48rem]">
      <div className="order-2 flex flex-wrap items-center gap-2 text-caption-min text-ink-2 lg:order-1">
        {result.startDate && result.endDate ? (
          <span>
            {formatMonthDay(result.startDate)} – {formatMonthDay(result.endDate)} · 전체 분야
            {result.recordsRead != null ? ` · 기록 ${result.recordsRead}개 읽음` : ''}
          </span>
        ) : null}
        {result.isNew ? (
          <span className="inline-flex h-5 items-center rounded-md bg-[color-mix(in_srgb,var(--color-base)_16%,white)] px-1.5 t-cap font-medium text-base-ink">
            새로 도착
          </span>
        ) : null}
      </div>

      <p className={`order-3 font-serif text-section-title leading-[1.7] text-ink lg:order-2 ${RESULT_CARD_CLASS}`}>
        {result.periodSummary}
      </p>

      <div className="order-4 lg:order-3">
        <TextItemBlock title="새로 해 본 것 · 나아진 점" items={result.changes} />
      </div>
      <div className="order-5 lg:order-4">
        <TextItemBlock title="아직 남은 어려움" items={result.repeatedDifficulties} />
      </div>

      <div className="order-1 lg:order-5">
        <CoachActionCard result={result} comeback={result.mode === 'COMEBACK'} onResultChange={onResultChange} />
      </div>

      <div className="order-6">
        <FeedbackRow
          question="이 회고가 도움됐나요?"
          status={result.feedback === 'HELPFUL' ? 'positive' : result.feedback === 'NOT_HELPFUL' ? 'negative' : null}
          pending={feedbackMutation.isPending}
          onPositive={() => handleFeedback('HELPFUL')}
          onNegative={() => handleFeedback('NOT_HELPFUL')}
          errorMessage={feedbackError}
        />
      </div>
    </div>
  )
}
