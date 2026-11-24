import type { ReactNode } from 'react'
import { useActivityView } from '../hooks/useActivityView'
import type { ActivityView } from '../lib/dots'
import { ActivityHeatmap, HeatmapLegend } from './ActivityHeatmap'
import { DotPanel } from './DotPanel'

const OPTIONS: { value: ActivityView; label: string }[] = [
  { value: 'grass', label: '잔디' },
  { value: 'dot', label: '도트' },
]

/**
 * 활동 지도 카드. [잔디 | 도트] 보기를 고르고, 선택은 이 기기에 기억한다(홈과 기록 탭이 같은 선택을 쓴다).
 * maxPictureHeight는 웹 홈 사이드처럼 남는 높이가 정해진 자리에서 도트 그림을 줄일 때 쓴다.
 * 잔디 보기는 기존 활동 지도 그대로이고, 기록 탭처럼 다른 지도·요약을 쓰려면 grass로 바꿔 끼운다.
 */
export function HomeActivityCard({
  grass,
  maxPictureHeight,
  home = false,
}: {
  grass?: ReactNode
  maxPictureHeight?: number
  /** 웹 홈: 제목·범례·토글을 한 줄 헤더로 합치고 여백은 홈 밀도 변수(--hpad)를 따른다. */
  home?: boolean
}) {
  const [view, setView] = useActivityView()

  if (home) {
    return (
      <section aria-label="활동 지도" className="home-pad home-grow flex flex-col gap-2 rounded-2xl bg-surface">
        <div className="flex h-8 items-center justify-between gap-3">
          <h2 className="flex-none text-card-title">활동 지도</h2>
          <div className="flex min-w-0 items-center gap-3">
            {view === 'grass' ? (
              <HeatmapLegend />
            ) : null}
            <div className="inline-flex flex-none rounded-lg border border-line-2 bg-white p-[0.125rem]" role="group" aria-label="활동 지도 보기 방식">
              {OPTIONS.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  aria-pressed={view === option.value}
                  onClick={() => setView(option.value)}
                  className={[
                    't-sub h-7 whitespace-nowrap rounded-md px-3 font-medium',
                    view === option.value ? 'bg-ink text-white' : 'bg-transparent text-ink-2',
                  ].join(' ')}
                >
                  {option.label}
                </button>
              ))}
            </div>
          </div>
        </div>
        <div className="flex min-h-0 flex-1 flex-col">
          {view === 'dot' ? <DotPanel maxPictureHeight={maxPictureHeight} /> : (grass ?? <ActivityHeatmap weeks={26} home />)}
        </div>
      </section>
    )
  }

  return (
    <div className="flex flex-col gap-3 rounded-2xl bg-surface p-5">
      <div className="flex justify-end">
        <div className="inline-flex rounded-lg border border-line-2 bg-white p-[0.1875rem]" role="group" aria-label="활동 지도 보기 방식">
          {OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              aria-pressed={view === option.value}
              onClick={() => setView(option.value)}
              className={[
                'tap-min whitespace-nowrap rounded-md px-3 text-caption-min font-medium',
                view === option.value ? 'bg-ink text-white' : 'bg-transparent text-ink-2',
              ].join(' ')}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>
      {view === 'dot' ? <DotPanel maxPictureHeight={maxPictureHeight} /> : (grass ?? <ActivityHeatmap weeks={26} />)}
    </div>
  )
}
