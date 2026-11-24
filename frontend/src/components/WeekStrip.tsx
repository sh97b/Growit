import { formatMonthDayWeekday, weekdayLabel } from '../lib/date'

export type WeekStripDay = { date: string; isToday: boolean; isFuture?: boolean; hasRecord: boolean }

/** home은 웹 홈용: 넉넉함·보통은 제목이 위·원이 아래, 컴팩트는 한 줄(CSS 변수 --wk-dir로 전환). */
export function WeekStrip({ days, home = false }: { days: WeekStripDay[]; home?: boolean }) {
  return (
    <div className={home ? 'home-week' : 'flex flex-col gap-3.5'} role="group" aria-label="이번 주 기록">
      {/* P1 1단계: "오늘" 동그라미의 box-shadow 이중 링이 부모(Split 사이드 열의 overflow-y:auto로
          overflow-x도 auto가 되는 조상)에 잘리지 않게 위아래·양옆에 여유 패딩을 둔다. */}
      <div className={home ? 'home-week-days flex justify-between gap-1 px-1' : 'flex justify-between gap-1 px-1.5 py-1'}>
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
          // 이번 주(월~일): 기록한 날은 초록 채움, 오늘은 굵은 점선 원(기록했으면 채움 위에 점선), 아직 오지 않은 날은 흐리게.
          const circleClass = day.isToday
            ? ['border-dashed !border-[3px] border-base', day.hasRecord ? 'bg-base bg-clip-padding' : 'bg-transparent'].join(' ')
            : day.hasRecord
              ? 'border-line bg-base bg-clip-padding'
              : 'border-line bg-transparent'
          return (
            <div key={day.date} aria-label={label} className={['flex flex-col items-center', home ? 'gap-1' : 'gap-1.5', day.isFuture ? 'opacity-40' : ''].join(' ')}>
              <span className={[home ? 'home-week-circle' : 'h-7 w-7', 'rounded-full border-2', circleClass].join(' ')} />
              <span className={[home ? 'text-xs' : 'text-caption-min', day.isToday ? 'font-bold text-ink' : 'text-ink-2'].join(' ')}>
                {weekdayLabel(localDate)}
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
