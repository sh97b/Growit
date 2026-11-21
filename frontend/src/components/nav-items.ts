export const REVIEW_PATHS = ['/records', '/changes', '/coach'] as const

export function isRoadmapPath(pathname: string): boolean {
  return pathname === '/roadmap'
}

export function isReviewPath(pathname: string): boolean {
  return (REVIEW_PATHS as readonly string[]).includes(pathname)
}
