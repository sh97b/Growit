import { ChartIcon, ChatIcon, HomeIcon, ListIcon } from './icons'

export const NAV_ITEMS = [
  { to: '/', label: '홈', shortLabel: '홈', icon: HomeIcon, end: true },
  { to: '/records', label: '기록 모아보기', shortLabel: '기록', icon: ListIcon, end: false },
  { to: '/changes', label: '나의 변화', shortLabel: '변화', icon: ChartIcon, end: false },
  { to: '/coach', label: 'AI 회고', shortLabel: '회고', icon: ChatIcon, end: false },
] as const
