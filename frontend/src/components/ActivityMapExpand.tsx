import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Loading } from './Loading'
import { ChevronRightIcon } from './icons'
import { useHobbies } from '../hooks/useHobbies'
import { addDays, formatMonthDay, startOfWeekLocal, todayLocalDate } from '../lib/date'
import { fetchActivityMap } from '../lib/stats'

type Props = {
  weekCount: number
}

function addWeeksLocal(weekStart: string, amount: number): string {
  return addDays(weekStart, amount * 7)
}

/**
 * 기록한 날 수(0~7)를 7일 대비 비율로 0~4단계로 나눈다. 디자인 토큰(§7)의 24/48/74/100%
 * 경계값을 그대로 썼다(정확한 날짜 수 기준은 디자인에 없어 비율로 해석함).
 */
function levelOf(recordedDays: number): 0 | 1 | 2 | 3 | 4 {
  if (recordedDays <= 0) return 0
  const ratio = recordedDays / 7
  if (ratio <= 0.24) return 1
  if (ratio <= 0.48) return 2
  if (ratio <= 0.74) return 3
  return 4
}

const LEVEL_BG: Record<0 | 1 | 2 | 3 | 4, string> = {
  0: 'bg-rest',
  1: 'bg-[color-mix(in_srgb,var(--color-base)_24%,var(--color-paper))]',
  2: 'bg-[color-mix(in_srgb,var(--color-base)_48%,var(--color-paper))]',
  3: 'bg-[color-mix(in_srgb,var(--color-base)_74%,var(--color-paper))]',
  4: 'bg-base',
}

/**
 * 지난 기록의 "최근 N주 활동 펼침" 영역. 칸 하나 = 한 분야의 한 주(기록한 날 수로 0~4단계 색).
 * 모바일 8주·데스크톱 12주는 각각 081·077 보드를 따르되, 데스크톱은 L 단계 사용자 지시로
 * 사이드에 배치한다(보드 자체는 본문에 두지만, 이 배치는 지시문이 명시한 디자인과의 차이).
 */
export function ActivityMapExpand({ weekCount }: Props) {
  const [expanded, setExpanded] = useState(false)
  const { data: activeHobbies, isLoading: hobbiesLoading } = useHobbies(false)

  const today = todayLocalDate()
  const currentWeekStart = startOfWeekLocal(new Date())
  const weeks = useMemo(
    () => Array.from({ length: weekCount }, (_, index) => addWeeksLocal(currentWeekStart, index - (weekCount - 1))),
    [currentWeekStart, weekCount],
  )
  const from = weeks[0]

  const mapQuery = useQuery({
    queryKey: ['stats', 'activity-map', 'expand', from, today],
    queryFn: () => fetchActivityMap({ from, to: today }),
    enabled: expanded,
  })

  if (hobbiesLoading) return <Loading />
  if (!activeHobbies || activeHobbies.length === 0) return null

  const recordedDaysByHobbyWeek = new Map<string, Set<string>>()
  for (const entry of mapQuery.data ?? []) {
    const weekStart = weeks.find((w) => entry.date >= w && entry.date < addWeeksLocal(w, 1)) ?? weeks[0]
    const key = `${entry.hobbyId}:${weekStart}`
    const set = recordedDaysByHobbyWeek.get(key) ?? new Set<string>()
    set.add(entry.date)
    recordedDaysByHobbyWeek.set(key, set)
  }

  return (
    <div className="flex flex-col gap-2.5">
      <button
        type="button"
        aria-expanded={expanded}
        onClick={() => setExpanded((prev) => !prev)}
        className="inline-flex min-h-11 w-fit items-center gap-1 text-sm font-medium text-base-ink underline underline-offset-4"
      >
        <ChevronRightIcon width={14} height={14} className={expanded ? 'rotate-90' : ''} />
        최근 {weekCount}주 활동 {expanded ? '접기' : '보기'}
      </button>

      {expanded ? (
        mapQuery.isLoading ? (
          <Loading />
        ) : (
          <div className="flex flex-col gap-2.5">
            <div
              role="img"
              aria-label={`최근 ${weekCount}주 활동 지도`}
              className="flex flex-col gap-1.5"
            >
              {activeHobbies.map((hobby) => (
                <div key={hobby.id} className="grid grid-cols-[40px_repeat(var(--wc),minmax(0,1fr))] items-center gap-1" style={{ ['--wc' as string]: weekCount }}>
                  <span className="truncate text-[13px] font-medium text-ink">{hobby.name}</span>
                  {weeks.map((weekStart) => {
                    const days = recordedDaysByHobbyWeek.get(`${hobby.id}:${weekStart}`)?.size ?? 0
                    const level = levelOf(days)
                    return (
                      <span
                        key={weekStart}
                        role="img"
                        aria-label={`${formatMonthDay(weekStart)} 주, ${hobby.name}, ${days}일`}
                        className={['h-7 rounded', LEVEL_BG[level]].join(' ')}
                      />
                    )
                  })}
                </div>
              ))}
            </div>
            <div className="flex items-center gap-1 text-xs text-ink-2">
              <span>적음</span>
              <span className={['h-3.5 w-3.5 rounded', LEVEL_BG[1]].join(' ')} />
              <span className={['h-3.5 w-3.5 rounded', LEVEL_BG[2]].join(' ')} />
              <span className={['h-3.5 w-3.5 rounded', LEVEL_BG[3]].join(' ')} />
              <span className={['h-3.5 w-3.5 rounded', LEVEL_BG[4]].join(' ')} />
              <span>많음</span>
            </div>
            <Link
              to={`/weeks/${currentWeekStart}`}
              className="inline-flex min-h-11 w-fit items-center gap-1 text-sm font-medium text-base-ink no-underline underline underline-offset-4"
            >
              주별로 보기
              <ChevronRightIcon width={16} height={16} />
            </Link>
          </div>
        )
      ) : null}
    </div>
  )
}
