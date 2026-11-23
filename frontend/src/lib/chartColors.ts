/** 성장 차트 색. 힘들어요는 빨강 대신 차분한 따뜻한 색을 쓴다. */
export const CHART_COLORS = {
  /** 기록한 날(앱 초록). */
  base: '#3E7C68',
  /** 쌓인 시간(같은 계열의 맑은 초록, 흰 배경 대비 3:1 이상). */
  time: '#4FA383',
  /** 보통이에요. */
  neutral: 'var(--feel-neutral)',
  /** 힘들어요: 빨강 대신 차분한 따뜻한 색. */
  hard: 'var(--feel-hard)',
  /** 좋아요. 기록한 날(base)과 달리 감정 전용 색이다. */
  good: 'var(--feel-good)',
  grid: '#E7E1D6',
  axis: '#CFC7B8',
  /** 축 눈금·라벨 글자(흰 배경 대비 4.5:1 이상). */
  text: '#57524A',
  strong: '#2B2823',
} as const
