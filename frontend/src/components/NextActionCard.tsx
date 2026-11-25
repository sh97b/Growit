import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useHobbies } from '../hooks/useHobbies'
import { useLatestNextAction } from '../hooks/useLatestNextAction'
import { formatMonthDay } from '../lib/date'
import { AiLabel } from './AiLabel'
import { EvidenceRow } from './CoachResultView'
import { fetchRecords, type LatestNextAction, type NextAction } from '../lib/records'

/** 근거 기록 칩은 회고 카드와 같은 모양을 쓴다. 근거가 없는 예전 결과는 칩 줄을 생략한다. */
export function NextActionDetails({ action }: { action: NextAction }) {
  return (
    <>
      <p className="text-body-min leading-[1.6] text-ink">{action.text}</p>
      <span className="text-caption-min text-ink-2">
        약 {action.minutes}분{action.reason ? ` · ${action.reason}` : ''}
      </span>
      {action.evidence ? (
        <EvidenceRow
          evidence={[
            { recordId: action.evidence.id, recordDate: action.evidence.recordDate, content: action.evidence.content },
          ]}
        />
      ) : null}
    </>
  )
}

/** 저장 직후 화면의 "다음에 해 볼 일". 버튼 없이 문장·이유·근거 칩만 담백하게 보여 준다(AI 글이라 산세리프). */
export function NextActionCard({ action, footer }: { action: NextAction; footer?: ReactNode }) {
  return (
    <div className="flex flex-col gap-2 rounded-2xl bg-surface p-5">
      <AiLabel>다음에 해 볼 일</AiLabel>
      <NextActionDetails action={action} />
      {footer ? <div className="mt-1 border-t border-line pt-3">{footer}</div> : null}
    </div>
  )
}

/**
 * [이 일로 기록하기]: 기록 작성 화면을 이 제안이 나온 기록의 분야가 선택된 상태로 연다. 기록 작성 화면은 그 분야의 가장 최근
 * 제안을 "지난 제안" 칩으로 켠 채 보여 주므로(기존 흐름) 따로 넘길 값은 분야뿐이다. 분야가 보관됐거나 찾을 수 없으면 버튼을 숨긴다.
 */
export function NextActionCta({ action }: { action: LatestNextAction }) {
  const recentQuery = useQuery({ queryKey: ['records', 'recent'], queryFn: () => fetchRecords({ size: 1 }) })
  const { data: hobbies } = useHobbies(false)
  const latest = recentQuery.data?.items[0]
  const hobbyId = latest && latest.id === action.recordId ? latest.hobbyId : null
  if (hobbyId == null || !(hobbies ?? []).some((hobby) => hobby.id === hobbyId)) return null
  return (
    <Link
      to={`/records/new?hobbyId=${hobbyId}`}
      className="t-body inline-flex h-11 w-full flex-none items-center justify-center rounded-lg bg-base font-medium text-white no-underline"
    >
      이 일로 기록하기
    </Link>
  )
}

function evidenceChip(action: NextAction) {
  if (!action.evidence) return null
  return (
    <Link
      to={`/records/${action.evidence.id}`}
      title={action.evidence.content}
      className="home-next-evidence t-sub inline-flex h-8 min-w-0 max-w-full items-center gap-2 rounded-lg border border-line-2 bg-white px-2.5 text-ink no-underline"
    >
      <span className="flex-none font-semibold text-base-ink">{formatMonthDay(action.evidence.recordDate)}</span>
      <span className="min-w-0 truncate text-ink-2">{action.evidence.content}</span>
    </Link>
  )
}

/**
 * 모바일 홈 회고 카드 안의 블록. N 이전의 "다음 작은 행동" 카드(CoachActionCard)와 같은 모양에
 * [이 일로 기록하기] 버튼(cta)을 붙인다.
 */
export function HomeNextActionBlock({ action, cta }: { action: NextAction; cta?: ReactNode }) {
  const detail = action.reason ? `${action.minutes}분 · ${action.reason}` : `${action.minutes}분`
  return (
    <div className="flex flex-col gap-3.5 rounded-2xl border border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_9%,white)] p-5">
      <AiLabel>다음에 해 볼 일</AiLabel>
      <p className="t-key">{action.text}</p>
      <span className="t-sub text-ink-2">{detail}</span>
      {action.evidence ? (
        <EvidenceRow
          evidence={[
            { recordId: action.evidence.id, recordDate: action.evidence.recordDate, content: action.evidence.content },
          ]}
        />
      ) : null}
      {cta}
    </div>
  )
}

/**
 * 웹 홈 오른쪽 열의 독립 카드. 위에서 아래로 ✧ 칩 → 핵심 문장(key 22px, 최대 3줄) → 이유(sub 14px 2줄) → 근거 칩 한 줄을 쌓고,
 * [이 일로 기록하기] 버튼은 카드 하단에 둔다. 카드는 남는 높이를 먹되 최대 높이를 넘지 않는다(빈 공간이 커지지 않게 CSS가 제한).
 * 제안이 없으면 한 줄 안내 카드를 같은 자리에 둔다.
 */
export function HomeNextActionCard() {
  const query = useLatestNextAction()
  if (query.isLoading) {
    return <div className="home-next-empty h-[5.5rem] rounded-2xl bg-surface motion-safe:animate-pulse" role="status" aria-label="다음에 해 볼 일을 불러오는 중이에요" />
  }
  const action = query.data && query.data.state == null ? query.data : null
  if (!action) {
    return (
      <div className="home-next-empty rounded-2xl bg-surface px-5 py-4">
        <p className="t-sub text-ink-2">기록하면 다음에 해 볼 일을 제안해 드려요</p>
      </div>
    )
  }
  const detail = action.reason ? `${action.minutes}분 · ${action.reason}` : `${action.minutes}분`
  return (
    <section
      aria-label="다음에 해 볼 일"
      className="home-next flex flex-col gap-2 overflow-hidden rounded-2xl border border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_9%,white)] p-5"
    >
      <AiLabel>다음에 해 볼 일</AiLabel>
      <p className="home-na-title text-ink">{action.text}</p>
      <span className="home-na-reason text-ink-2" title={detail}>
        {detail}
      </span>
      {evidenceChip(action)}
      <div className="mt-auto flex flex-col pt-1">
        <NextActionCta action={action} />
      </div>
    </section>
  )
}
