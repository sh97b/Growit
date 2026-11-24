import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Dialog } from '../components/Dialog'
import { DotPicture } from '../components/DotPicture'
import { ChevronLeftIcon, XIcon } from '../components/icons'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
import { PageHeader } from '../components/PageLayout'
import { useDotAlbum, useDotGoalDetail } from '../hooks/useDots'
import type { DotGoal } from '../lib/dots'
import { formatYearMonthDay } from '../lib/date'

const THUMB_BOX_PX = 104
const DETAIL_TITLE_ID = 'dot-album-detail-title'

function DetailDialog({ goal, onClose }: { goal: DotGoal; onClose: () => void }) {
  const detail = useDotGoalDetail(goal.id, goal).data ?? goal
  return (
    <Dialog onClose={onClose} labelledBy={DETAIL_TITLE_ID}>
      <div className="flex items-start justify-between gap-3">
        <h2 id={DETAIL_TITLE_ID} className="font-serif t-section font-semibold">
          {detail.title}
        </h2>
        <button
          type="button"
          onClick={onClose}
          aria-label="닫기"
          className="tap-min -mr-2 -mt-1 inline-flex flex-none items-center justify-center rounded-full text-ink-2 hover:bg-surface"
        >
          <XIcon width={20} height={20} />
        </button>
      </div>
      <div className="mt-4 flex flex-col gap-3">
        <div className="flex items-center justify-center rounded-xl bg-surface px-2 py-4">
          <DotPicture
            width={detail.width}
            height={detail.height}
            palette={detail.palette}
            cells={detail.cells}
            filledCount={detail.filledCount}
            maxHeight={260}
            ariaLabel={`${detail.title} 도트 그림 ${detail.goalDays}칸 완성`}
          />
        </div>
        <div className="flex flex-col gap-1">
          <p className="text-body-min font-medium text-ink">{detail.goalDays}칸을 모두 채웠어요</p>
          <p className="text-caption-min leading-[1.6] text-ink-2">
            {formatYearMonthDay(detail.startedOn)}에 시작해서{' '}
            {detail.completedOn ? `${formatYearMonthDay(detail.completedOn)}에 완성했어요` : '완성했어요'}
          </p>
        </div>
      </div>
    </Dialog>
  )
}

/**
 * 도트 그림 도감. 홈 안의 모달이 아니라 따로 주소(/dots/collection)를 둔 이유: 완성한 그림이 쌓일수록 목록이 길어지고,
 * 뒤로 가기·새로고침·주소 공유가 되는 다른 상세 화면(/weeks/…, /records/…)과 같은 구조로 맞추려는 것이다.
 * 항목의 큰 그림만 모달로 연다.
 */
export function DotCollectionPage() {
  const query = useDotAlbum()
  const [openGoal, setOpenGoal] = useState<DotGoal | null>(null)

  return (
    <div className="flex flex-col gap-5 lg:min-h-0 lg:flex-1">
      <nav className="t-sub text-ink-2">
        <Link to="/" className="inline-flex min-h-11 items-center gap-1 text-ink-2 no-underline">
          <ChevronLeftIcon width={16} height={16} />홈
        </Link>
      </nav>
      <PageHeader title="도감" subtitle="완성한 도트 그림이 모여 있어요" />

      <div className="flex flex-col gap-5 lg:min-h-0 lg:flex-1 lg:overflow-y-auto lg:pr-2 scrollbar-thin-fade">
        {query.isLoading ? (
          <Loading />
        ) : query.isError ? (
          <div className="flex flex-col items-start gap-2">
            <Note variant="sand">도감을 지금은 불러오지 못했어요. 그림은 그대로 안전해요.</Note>
            <button
              type="button"
              onClick={() => query.refetch()}
              className="inline-flex min-h-11 items-center rounded-full px-2 -mx-2 text-caption-min font-medium text-base-ink hover:bg-[color-mix(in_srgb,var(--color-base)_10%,transparent)]"
            >
              다시 불러오기
            </button>
          </div>
        ) : (query.data ?? []).length === 0 ? (
          <div className="flex flex-col items-start gap-3 rounded-2xl bg-surface p-6">
            <p className="text-body-min font-medium text-ink">완성한 그림이 여기에 모여요.</p>
            <Link to="/" className="inline-flex min-h-11 items-center text-body-min font-medium text-base-ink no-underline">
              홈에서 도트 그림 시작하기
            </Link>
          </div>
        ) : (
          <ul className="m-0 grid list-none grid-cols-2 gap-3 p-0 sm:grid-cols-3 lg:grid-cols-4">
            {(query.data ?? []).map((goal) => (
              <li key={goal.id}>
                <button
                  type="button"
                  onClick={() => setOpenGoal(goal)}
                  aria-label={`${goal.title} ${goal.goalDays}칸, ${goal.completedOn ? formatYearMonthDay(goal.completedOn) : ''} 완성. 크게 보기`}
                  className="flex h-full w-full flex-col items-center gap-2 rounded-2xl border border-line bg-white p-3 text-left"
                >
                  <div className="flex items-center justify-center" style={{ width: THUMB_BOX_PX, height: THUMB_BOX_PX }}>
                    <DotPicture
                      width={goal.width}
                      height={goal.height}
                      palette={goal.palette}
                      cells={goal.cells}
                      filledCount={goal.filledCount}
                      box={THUMB_BOX_PX}
                      ariaLabel={`${goal.title} 도트 그림`}
                    />
                  </div>
                  <div className="flex w-full flex-col gap-0.5">
                    <span className="text-body-min font-medium text-ink">{goal.title}</span>
                    <span className="text-caption-min text-ink-2">{goal.goalDays}칸</span>
                    <span className="text-caption-min text-ink-3">시작 {formatYearMonthDay(goal.startedOn)}</span>
                    {goal.completedOn ? (
                      <span className="text-caption-min text-ink-3">완성 {formatYearMonthDay(goal.completedOn)}</span>
                    ) : null}
                  </div>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      {openGoal ? <DetailDialog goal={openGoal} onClose={() => setOpenGoal(null)} /> : null}
    </div>
  )
}
