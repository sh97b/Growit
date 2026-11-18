import { useActivityView } from '../hooks/useActivityView'
import type { ActivityView } from '../lib/dots'
import { ActivityHeatmap } from './ActivityHeatmap'
import { DotPanel } from './DotPanel'

const OPTIONS: { value: ActivityView; label: string }[] = [
  { value: 'grass', label: '잔디' },
  { value: 'dot', label: '도트' },
]

/** 홈의 활동 지도 카드. [잔디 | 도트] 보기를 고르고, 선택은 이 기기에 기억한다. 잔디 보기는 기존 활동 지도 그대로다. */
export function HomeActivityCard() {
  const [view, setView] = useActivityView()

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
      {view === 'dot' ? <DotPanel /> : <ActivityHeatmap weeks={26} />}
    </div>
  )
}
