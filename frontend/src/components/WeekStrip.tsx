import { formatMonthDayWeekday, weekdayLabel } from '../lib/date'

export type WeekStripDay = { date: string; isToday: boolean; hasRecord: boolean }

export function WeekStrip({ days }: { days: WeekStripDay[] }) {
  return (
    <div className="flex flex-col gap-3.5">
      <h2 className="text-base font-semibold">최근 7일</h2>
      {/* P1 1단계: "오늘" 동그라미의 box-shadow 이중 링이 부모(Split 사이드 열의 overflow-y:auto로
          overflow-x도 auto가 되는 조상)에 잘리지 않게 위아래·양옆에 여유 패딩을 둔다. */}
      <div className="flex justify-between gap-1 px-1.5 py-1">
        {days.map((day) => {
          const localDate = toLocalDate(day.date)
          const label = `${formatMonthDayWeekday(localDate)}, 기록 ${day.hasRecord ? '있음' : '없음'}`
          return (
            <div key={day.date} aria-label={label} className="flex flex-col items-center gap-1.5">
              <span
                className={[
                  'h-7 w-7 rounded-full border-2',
                  day.isToday
                    ? 'border-transparent bg-base'
                    : day.hasRecord
                      ? 'border-transparent bg-base'
                      : 'border-line-2 bg-transparent',
                ].join(' ')}
              />
              <span className={['text-caption-min', day.isToday ? 'font-bold text-ink' : 'text-ink-2'].join(' ')}>
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
