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

export function startOfWeekLocal(date: Date): string {
  const day = date.getDay()
  const diffToMonday = day === 0 ? -6 : 1 - day
  const monday = new Date(date)
  monday.setDate(monday.getDate() + diffToMonday)
  return formatLocalDate(monday)
}
