import { ChevronLeftIcon, ChevronRightIcon } from './icons'

type Props = {
  page: number
  totalPages: number
  onChange: (page: number) => void
}

function pageButtonClass(active: boolean): string {
  return [
    'inline-flex h-11 min-w-11 items-center justify-center rounded-lg border px-2 text-sm font-medium',
    active ? 'border-base bg-base text-white' : 'border-line-2 bg-white text-ink',
  ].join(' ')
}

/**
 * 디자인에 없는 추가 부품(U2): 서버의 page·size·totalCount 기반 목록을 넘길 때 공용으로 쓴다.
 * 0부터 시작하는 page를 받아 1부터 보여 준다.
 */
export function Pagination({ page, totalPages, onChange }: Props) {
  if (totalPages <= 1) return null

  const pageNumbers = Array.from({ length: totalPages }, (_, index) => index)

  return (
    <nav aria-label="페이지 이동" className="flex items-center justify-center gap-1.5">
      <button
        type="button"
        aria-label="이전 페이지"
        disabled={page === 0}
        onClick={() => onChange(page - 1)}
        className="inline-flex h-11 w-11 items-center justify-center rounded-lg border border-line-2 bg-white text-ink disabled:opacity-40"
      >
        <ChevronLeftIcon width={16} height={16} />
      </button>
      {pageNumbers.map((pageNumber) => (
        <button
          key={pageNumber}
          type="button"
          aria-label={`${pageNumber + 1}페이지`}
          aria-current={pageNumber === page ? 'page' : undefined}
          onClick={() => onChange(pageNumber)}
          className={pageButtonClass(pageNumber === page)}
        >
          {pageNumber + 1}
        </button>
      ))}
      <button
        type="button"
        aria-label="다음 페이지"
        disabled={page >= totalPages - 1}
        onClick={() => onChange(page + 1)}
        className="inline-flex h-11 w-11 items-center justify-center rounded-lg border border-line-2 bg-white text-ink disabled:opacity-40"
      >
        <ChevronRightIcon width={16} height={16} />
      </button>
    </nav>
  )
}
