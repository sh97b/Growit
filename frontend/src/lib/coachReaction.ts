const TRIED_LINES = [
  '해 봤다는 것만으로 한 걸음이에요.',
  '시도한 것만으로도 충분해요.',
  '작게라도 움직였다는 게 남았어요.',
]

const REST_TODAY_LINES = [
  '오늘은 쉬어도 괜찮아요. 내일 다시 보여 드릴게요.',
  '쉬는 날도 흐름의 일부예요. 다음에 또 볼게요.',
  '오늘은 여기까지면 충분해요.',
]

function pick(lines: string[], seed: number): string {
  const index = Math.abs(seed) % lines.length
  return lines[index]
}

/** 반응 문구는 AI를 부르지 않는 고정 문구다. 같은 결과(id)를 다시 봐도 항상 같은 문구가 보이게 id로 고른다. */
export function reactionLine(value: 'TRIED' | 'REST_TODAY', resultId: number): string {
  return value === 'TRIED' ? pick(TRIED_LINES, resultId) : pick(REST_TODAY_LINES, resultId)
}
