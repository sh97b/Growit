export function formatLocalDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function todayLocalDate(): string {
  return formatLocalDate(new Date())
}

export function yesterdayLocalDate(): string {
  const date = new Date()
  date.setDate(date.getDate() - 1)
  return formatLocalDate(date)
}

export function formatMonthDay(isoDate: string): string {
  const [, month, day] = isoDate.split('-')
  return `${Number(month)}월 ${Number(day)}일`
}

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

export function weekdayLabel(date: Date): string {
  return WEEKDAYS[date.getDay()]
}

export function formatMonthDayWeekday(date: Date): string {
  return `${date.getMonth() + 1}월 ${date.getDate()}일 ${weekdayLabel(date)}요일`
}

export function addDays(isoDate: string, amount: number): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  const date = new Date(year, month - 1, day)
  date.setDate(date.getDate() + amount)
  return formatLocalDate(date)
}

export function formatDurationMinutes(totalMinutes: number): string {
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  if (hours === 0) return `${minutes}분`
  if (minutes === 0) return `${hours}시간`
  return `${hours}시간 ${minutes}분`
}

export function daysBetween(fromIsoDate: string, toIsoDate: string): number {
  const [fy, fm, fd] = fromIsoDate.split('-').map(Number)
  const [ty, tm, td] = toIsoDate.split('-').map(Number)
  const from = Date.UTC(fy, fm - 1, fd)
  const to = Date.UTC(ty, tm - 1, td)
  return Math.round((to - from) / (24 * 60 * 60 * 1000))
}

export function startOfWeekLocal(date: Date): string {
  const day = date.getDay()
  const diffToMonday = day === 0 ? -6 : 1 - day
  const monday = new Date(date)
  monday.setDate(monday.getDate() + diffToMonday)
  return formatLocalDate(monday)
}
