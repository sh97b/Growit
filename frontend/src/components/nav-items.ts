export const REVIEW_PATHS = ['/records', '/changes', '/coach'] as const

export function isReviewPath(pathname: string): boolean {
  return (REVIEW_PATHS as readonly string[]).includes(pathname)
}
