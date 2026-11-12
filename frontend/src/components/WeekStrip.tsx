import { formatMonthDayWeekday, weekdayLabel } from '../lib/date'

export type WeekStripDay = { date: string; isToday: boolean; hasRecord: boolean }

export function WeekStrip({ days }: { days: WeekStripDay[] }) {
  return (
    <div className="flex flex-col gap-3.5">
      <h2 className="text-base font-semibold">최근 7일</h2>
      <div className="flex justify-between gap-1">
        {days.map((day) => {
          const localDate = toLocalDate(day.date)
          const label = `${formatMonthDayWeekday(localDate)}, 기록 ${day.hasRecord ? '있음' : '없음'}`
          return (
            <div key={day.date} aria-label={label} className="flex flex-col items-center gap-1.5">
              <span
                className={[
                  'h-7 w-7 rounded-full border-2',
                  day.hasRecord ? 'border-transparent bg-base' : 'border-line-2 bg-transparent',
                  day.isToday && !day.hasRecord ? 'border-ink' : '',
                  day.isToday && day.hasRecord ? 'shadow-[0_0_0_2px_white,0_0_0_4px_var(--color-ink)]' : '',
                ].join(' ')}
              />
              <span className="text-caption-min text-ink-2">{day.isToday ? '오늘' : weekdayLabel(localDate)}</span>
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
