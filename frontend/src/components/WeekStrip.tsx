import { formatMonthDayWeekday, weekdayLabel } from '../lib/date'

export type WeekStripDay = { date: string; isToday: boolean; hasRecord: boolean }

export function WeekStrip({ days, compact = false }: { days: WeekStripDay[]; compact?: boolean }) {
  return (
    <div className={compact ? 'flex items-center justify-between gap-3 rounded-xl bg-surface px-3 py-2' : 'flex flex-col gap-3.5'}>
      <h2 className={compact ? 'flex-none text-sm font-semibold' : 'text-base font-semibold'}>최근 7일</h2>
      {/* P1 1단계: "오늘" 동그라미의 box-shadow 이중 링이 부모(Split 사이드 열의 overflow-y:auto로
          overflow-x도 auto가 되는 조상)에 잘리지 않게 위아래·양옆에 여유 패딩을 둔다. */}
      <div className={compact ? 'flex min-w-0 flex-1 justify-between gap-1 px-1' : 'flex justify-between gap-1 px-1.5 py-1'}>
        {days.map((day) => {
          const localDate = toLocalDate(day.date)
          const label = `${formatMonthDayWeekday(localDate)}, 기록 ${day.hasRecord ? '있음' : '없음'}`
          // R1 2번: 모든 원은 같은 크기·같은 2px 테두리(박스 크기 고정, 테두리 색·모양만 변경).
          // 기록 여부(day.hasRecord, 서버 activity-map 데이터)만으로 채움 여부를 정하고, isToday는
          // 테두리 모양(점선/실선)에만 쓴다 — 전에는 isToday만으로 채워서 "오늘인데 아직 기록
          // 안 한 날"도 초록으로 채워지는 버그가 있었다.
          // T1-3: 테두리 색을 전부 빈 원 기준(line 토큰)으로 통일한다(기존엔 채워진 원이
          // border-transparent, 빈 원이 border-line-2로 서로 달랐다). 채워진 원은 bg-clip-padding으로
          // 초록 채움이 테두리 밖으로 번지지 않게 한다. "오늘 + 기록 전"만 점선 초록 테두리로 예외.
          const isTodayWithoutRecord = day.isToday && !day.hasRecord
          const circleClass = isTodayWithoutRecord
            ? 'border-dashed border-base bg-transparent'
            : day.hasRecord
              ? 'border-line bg-base bg-clip-padding'
              : 'border-line bg-transparent'
          return (
            <div key={day.date} aria-label={label} className={['flex flex-col items-center', compact ? 'gap-0.5' : 'gap-1.5'].join(' ')}>
              <span className={[compact ? 'h-5 w-5' : 'h-7 w-7', 'rounded-full border-2', circleClass].join(' ')} />
              <span className={[compact ? 'text-xs' : 'text-caption-min', day.isToday ? 'font-bold text-ink' : 'text-ink-2'].join(' ')}>
                {day.isToday ? '오늘' : weekdayLabel(localDate)}
              </span>
            </div>
          )
        })}
      </div>
    </div>
  )
}

function toLocalDate(isoDate: string): Date {
  const [year, month, day] = isoDate.split('-').map(Number)
  return new Date(year, month - 1, day)
}
