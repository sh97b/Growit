import { addDays } from './date'

function monthLabel(isoDate: string): string {
  const [, month] = isoDate.split('-')
  return `${Number(month)}월`
}

/**
 * 각 달의 라벨은 그 달 1일이 속한 주의 열에 둔다. 격자 첫 열이 속한 달은(그 주에 1일이 없어도)
 * 첫 열에 둔다 — 이전 구현은 "그 주 월요일이 속한 달"로 라벨을 매겼는데, 월 1일이 주 중간(화~일)에
 * 걸리면 실제로 그 달이 시작하는 열보다 한 주 늦게(또는 월요일이 지난달에 속하면 한 주 빠르게)
 * 라벨이 매겨져 월 라벨끼리 겹치거나("11월12월") 중복 제거 로직이 엉뚱한 열 간격을 보고 오판했다.
 */
function buildRawMonthLabels(firstWeekMonday: string, weeks: number): { col: number; label: string }[] {
  const raw: { col: number; label: string }[] = []
  for (let col = 0; col < weeks; col += 1) {
    const weekStart = addDays(firstWeekMonday, col * 7)
    if (col === 0) {
      raw.push({ col: 0, label: monthLabel(weekStart) })
      continue
    }
    for (let dayOffset = 0; dayOffset < 7; dayOffset += 1) {
      const date = addDays(weekStart, dayOffset)
      if (Number(date.split('-')[2]) === 1) {
        raw.push({ col, label: monthLabel(date) })
        break
      }
    }
  }
  return raw
}

/**
 * ActivityHeatmap 월 라벨 계산(순수 함수, T2 1번). firstWeekMonday는 격자 첫 열(맨 왼쪽 주)의
 * 월요일, weeks는 열 개수다. 라벨끼리 열 간격이 3 미만이면 왼쪽(먼저 나온) 라벨을 지우되 가장
 * 오른쪽 라벨은 지우지 않는다.
 */
export function computeMonthLabels(firstWeekMonday: string, weeks: number): { col: number; label: string }[] {
  const raw = buildRawMonthLabels(firstWeekMonday, weeks)
  const labels: { col: number; label: string }[] = []
  raw.forEach((item, index) => {
    const next = raw[index + 1]
    if (next && next.col - item.col < 3) return
    labels.push(item)
  })
  return labels
}

/**
 * 기록 탭 데스크톱 한 줄 모드 전용: 칸이 14px로 고정돼 월 한 칸이 라벨끼리 겹칠 만큼 좁아지지
 * 않으므로 숨기는 규칙 없이 12개월 전부 표시한다.
 */
export function computeAllMonthLabels(firstWeekMonday: string, weeks: number): { col: number; label: string }[] {
  return buildRawMonthLabels(firstWeekMonday, weeks)
}
