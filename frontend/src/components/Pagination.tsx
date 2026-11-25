import { ChevronLeftIcon, ChevronRightIcon } from './icons'

type Props = {
  page: number
  totalPages: number
  onChange: (page: number) => void
}

/** 한 번에 점으로 보여 줄 최대 페이지 수. 넘으면 현재 쪽 주변만 보여 주고 가장자리 점을 누르면 창이 옮겨 간다. */
const MAX_DOTS = 9

/**
 * 서버의 page·size·totalCount 기반 목록을 넘길 때 공용으로 쓴다. 0부터 시작하는 page를 받는다.
 * 숫자 대신 점으로 보여 주고 현재 쪽은 길쭉한 초록 알약, 나머지는 흰 점(옅은 테두리)으로 표시한다. 양옆의 작은 화살표로 한 쪽씩 넘긴다.
 * 누르는 영역은 점보다 크게(높이 44) 둔다.
 */
export function Pagination({ page, totalPages, onChange }: Props) {
  if (totalPages <= 1) return null

  const count = Math.min(totalPages, MAX_DOTS)
  const start = Math.min(Math.max(0, page - Math.floor(count / 2)), totalPages - count)
  const pageNumbers = Array.from({ length: count }, (_, index) => start + index)

  return (
    <nav aria-label="페이지 이동" className="flex items-center justify-center gap-1">
      <button
        type="button"
        aria-label="이전 페이지"
        disabled={page === 0}
        onClick={() => onChange(page - 1)}
        className="inline-flex h-11 w-9 items-center justify-center rounded-full text-ink-2 hover:text-ink disabled:opacity-30 disabled:hover:text-ink-2"
      >
        <ChevronLeftIcon width={14} height={14} />
      </button>
      {pageNumbers.map((pageNumber) => {
        const active = pageNumber === page
        return (
          <button
            key={pageNumber}
            type="button"
            aria-label={`${pageNumber + 1}페이지`}
            aria-current={active ? 'page' : undefined}
            onClick={() => onChange(pageNumber)}
            className="group inline-flex h-11 items-center justify-center px-1.5"
          >
            <span
              aria-hidden="true"
              className={[
                'block h-2.5 rounded-full transition-[width,background-color] duration-200 motion-reduce:transition-none',
                active
                  ? 'w-7 bg-base shadow-[0_1px_5px_color-mix(in_srgb,var(--color-base)_45%,transparent)]'
                  : 'w-2.5 border border-line-2 bg-white shadow-[0_1px_2px_color-mix(in_srgb,var(--color-base)_18%,transparent)] group-hover:border-base',
              ].join(' ')}
            />
          </button>
        )
      })}
      <button
        type="button"
        aria-label="다음 페이지"
        disabled={page >= totalPages - 1}
        onClick={() => onChange(page + 1)}
        className="inline-flex h-11 w-9 items-center justify-center rounded-full text-ink-2 hover:text-ink disabled:opacity-30 disabled:hover:text-ink-2"
      >
        <ChevronRightIcon width={14} height={14} />
      </button>
    </nav>
  )
}
