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
 * ActivityHeatmap 월 라벨 계산(순수 함수). firstWeekMonday는 격자 첫 열(맨 왼쪽 주)의 월요일,
 * weeks는 열 개수다. 월 라벨을 그리는 코드 경로는 이 함수 하나뿐이다(T3 1번 — 이전엔 칸이 넓어
 * 겹칠 일이 없다고 보고 14px 고정 칸 모드에서만 이 중복 제거 없이 전부 그리는 computeAllMonthLabels를
 * 따로 뒀었는데, 격자 첫 열이 강제로 받는 라벨과 그 바로 다음 열의 "1일이 속한 주" 라벨이 1열
 * 차이로 붙는 경우(예: 52주 보기의 맨 왼쪽)는 칸이 넓어도 겹쳤다 — 하나로 합치고 아래 두 규칙을
 * 항상 적용해 해결한다).
 *
 * 규칙: ① 라벨끼리 열 간격이 3 미만이면 왼쪽(먼저 나온) 라벨을 지우되 가장 오른쪽 라벨은 지우지
 * 않는다. ② 그러고도 남은 라벨 중 col이 0이나 1이면 지운다(맨 왼쪽 두 열은 잘려 보이기 쉬워
 * 라벨을 아예 두지 않는다).
 */
export function computeMonthLabels(firstWeekMonday: string, weeks: number): { col: number; label: string }[] {
  const raw = buildRawMonthLabels(firstWeekMonday, weeks)
  const deduped: { col: number; label: string }[] = []
  raw.forEach((item, index) => {
    const next = raw[index + 1]
    if (next && next.col - item.col < 3) return
    deduped.push(item)
  })
  return deduped.filter((item) => item.col >= 2)
}
