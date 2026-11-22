/** 성장 차트의 축·눈금 계산. 화면 부품과 분리해 둔다(순수 함수). */

import { formatLocalDate } from './date'

function parts(iso: string): { y: number; m: number; d: number } {
  return { y: Number(iso.slice(0, 4)), m: Number(iso.slice(5, 7)), d: Number(iso.slice(8, 10)) }
}

/** 오늘이 속한 주의 월요일. */
export function currentWeekStart(): string {
  const now = new Date()
  const offset = (now.getDay() + 6) % 7
  now.setDate(now.getDate() - offset)
  return formatLocalDate(now)
}

/**
 * 필터 기준으로 기록이 처음 있는 주부터 보여 준다(그보다 앞의 빈 주는 뺀다). 오늘이 속한 주까지 최소 minWeeks주는
 * 남기고, 중간의 빈 주는 그대로 둔다. weeks는 오래된 주부터 오늘 주까지 순서다.
 */
export function trimWeeks<T extends { days: number }>(weeks: T[], minWeeks = 6): T[] {
  const firstWithRecord = weeks.findIndex((week) => week.days > 0)
  const latestStart = Math.max(0, weeks.length - minWeeks)
  const start = firstWithRecord === -1 ? latestStart : Math.min(firstWithRecord, latestStart)
  return weeks.slice(start)
}

/**
 * 주 시작일 목록의 x축 라벨. 8개 이하면 모두 "11/16", 많으면 월이 바뀌는 곳에만 "11월", 해가 바뀌면 "2027.1".
 * 라벨은 최대 8개이고, 현재 주가 있으면 마지막 막대에 "이번 주"를 단다.
 */
export function weekTicks(starts: string[], hasCurrent: boolean): (string | undefined)[] {
  const n = starts.length
  const out: (string | undefined)[] = new Array(n).fill(undefined)
  if (n <= 8) {
    starts.forEach((start, index) => {
      const p = parts(start)
      out[index] = `${p.m}/${p.d}`
    })
  } else {
    const spansYears = parts(starts[0]).y !== parts(starts[n - 1]).y
    let candidates: { index: number; label: string; yearMark: boolean }[] = []
    starts.forEach((start, index) => {
      const p = parts(start)
      const prev = index > 0 ? parts(starts[index - 1]) : null
      if (prev && prev.m === p.m) return
      const yearMark = prev ? prev.y !== p.y : spansYears
      candidates.push({ index, label: yearMark ? `${p.y}.${p.m}` : `${p.m}월`, yearMark })
    })
    while (candidates.length > 8) {
      candidates = candidates.filter((candidate, position) => position % 2 === 0 || candidate.yearMark)
      if (candidates.length > 8 && candidates.every((candidate) => candidate.yearMark)) break
    }
    candidates.slice(0, 8).forEach((candidate) => {
      out[candidate.index] = candidate.label
    })
  }
  if (hasCurrent && n > 0) {
    out[n - 1] = '이번 주'
    for (let back = 2; back <= 3 && n - back >= 0; back += 1) out[n - back] = undefined
  }
  return out
}

/** 하루 단위 구간의 x축 라벨(최대 8개 안팎): 첫 날과 월이 바뀌는 날은 "11/1", 나머지는 일자. */
export function dayTicks(dates: string[]): (string | undefined)[] {
  const step = Math.ceil(dates.length / 8)
  return dates.map((date, index) => {
    const p = parts(date)
    const prev = index > 0 ? parts(dates[index - 1]) : null
    const monthStart = index === 0 || (prev !== null && prev.m !== p.m)
    if (index % step !== 0 && !monthStart) return undefined
    return monthStart ? `${p.m}/${p.d}` : String(p.d)
  })
}

/** 쌓인 시간 축: 0부터 최댓값을 60분(1시간) 단위로 올림. 보조선이 최대 3개가 되도록 칸 간격을 정한다. */
export function minutesAxis(maxMinutes: number): { yMax: number; ticks: number[] } {
  const hours = Math.max(1, Math.ceil(maxMinutes / 60))
  const stepHours = hours <= 3 ? 1 : Math.ceil(hours / 3)
  const yMaxHours = Math.ceil(hours / stepHours) * stepHours
  const ticks: number[] = []
  for (let hour = 0; hour <= yMaxHours; hour += stepHours) ticks.push(hour * 60)
  return { yMax: yMaxHours * 60, ticks }
}

/** 개수 축: 0부터 최댓값(최소 1)까지. 작으면 정수 칸마다, 크면 0·중간·최댓값. */
export function countAxis(maxCount: number): { yMax: number; ticks: number[] } {
  const yMax = Math.max(1, maxCount)
  const ticks = yMax <= 4 ? Array.from({ length: yMax + 1 }, (_, index) => index) : [0, Math.round(yMax / 2), yMax]
  return { yMax, ticks }
}
