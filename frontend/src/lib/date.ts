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
