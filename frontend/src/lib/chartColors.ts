/**
 * 성장 차트 색. 값은 모두 index.css의 CSS 변수(한 곳에서 관리)다.
 * 같은 이름에 -edge(진한 가는 테두리)와 -strong(hover·포커스용 한 단계 진한 채움)이 짝으로 있다.
 */
export const CHART_COLORS = {
  /** 기록 수·기록한 날. */
  base: 'var(--bar-count)',
  /** 쌓인 시간. */
  time: 'var(--bar-time)',
  good: 'var(--feel-good)',
  neutral: 'var(--feel-neutral)',
  hard: 'var(--feel-hard)',
  grid: 'var(--chart-grid)',
  axis: 'var(--chart-grid)',
  /** 축·라벨·값 글자. */
  text: 'var(--chart-ink)',
  strong: 'var(--chart-ink)',
} as const

/** 채움 변수에 짝지어진 테두리 변수. */
export function edgeOf(fill: string): string {
  return fill.replace(/\)$/, '-edge)')
}

/** 채움 변수에 짝지어진 hover 채움 변수. */
export function strongOf(fill: string): string {
  return fill.replace(/\)$/, '-strong)')
}
