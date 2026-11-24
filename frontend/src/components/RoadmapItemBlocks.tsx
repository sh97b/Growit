import { useEffect, useRef, useState, type KeyboardEvent, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { Link } from 'react-router-dom'
import { AiProgressCard } from './AiProgress'
import { BlockEditor } from './RoadmapBlockEditors'
import { Button } from './Button'
import { FloatingPanel } from './FloatingPanel'
import { CheckCircleIcon, ClockIcon, LinkIcon, MoreIcon, PencilIcon, SearchIcon, SparkleIcon } from './icons'
import { useIsDesktop } from '../hooks/useIsDesktop'
import { useDragSort } from '../hooks/useDragSort'
import {
  useBlocks,
  useCreateBlock,
  useDeleteBlock,
  useGenerateHowto,
  useReorderBlocks,
  useUpdateBlock,
} from '../hooks/useRoadmap'
import { ApiError } from '../lib/api'
import {
  LINK_CATEGORY_LABELS,
  googleSearchUrl,
  youtubeSearchUrl,
  type BlockKind,
  type BlockPayload,
  type LinkCategory,
  type RoadmapBlock,
} from '../lib/roadmap'

type EditableKind = Exclude<BlockKind, 'AI_HOWTO'>

const KIND_INFO: Record<BlockKind, { name: string; description: string; icon: ReactNode }> = {
  MEMO: { name: '메모', description: '짧은 생각이나 기억할 점을 적어 둬요', icon: <PencilIcon width={18} height={18} /> },
  LINK: { name: '링크', description: '영상·악보·자료 주소를 붙여 둬요', icon: <LinkIcon width={18} height={18} /> },
  TIMER: { name: '타이머', description: '정해 둔 시간만큼 집중해요', icon: <ClockIcon width={18} height={18} /> },
  AI_HOWTO: { name: '해 보는 방법', description: 'AI가 일반적인 연습 순서를 알려 줘요', icon: <SparkleIcon width={18} height={18} /> },
  SEARCH: { name: '검색', description: '유튜브·구글 검색 링크를 만들어 줘요', icon: <SearchIcon width={18} height={18} /> },
  SUBLIST: { name: '하위 체크', description: '잘게 나눈 체크 목록을 만들어요', icon: <CheckCircleIcon width={18} height={18} /> },
}

const ADD_ORDER: BlockKind[] = ['MEMO', 'LINK', 'TIMER', 'AI_HOWTO', 'SEARCH', 'SUBLIST']

function errorMessageOf(error: unknown): string {
  return error instanceof ApiError ? error.message : '잠시 후 다시 시도해 주세요'
}

function isWebUrl(value: unknown): value is string {
  return typeof value === 'string' && /^https?:\/\//i.test(value)
}

function asStrings(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((entry): entry is string => typeof entry === 'string') : []
}

/**
 * 로드맵 항목 하나 아래에 펼쳐지는 블록 목록(노션식). 블록은 카드형이고, 웹에서는 끌어서 순서를 바꾸고(키보드: 손잡이에서 스페이스로 집기 →
 * 방향키 → 스페이스로 놓기) 모바일에서는 ⋯ 시트의 위로·아래로를 쓴다. 보관한 로드맵(readOnly)은 보기만 되고, 타이머와 링크 열기는 된다.
 * "/"를 누르면 [+ 블록 추가] 메뉴가 열린다.
 */
export function RoadmapItemBlocks({ itemId, hobbyId, readOnly }: { itemId: number; hobbyId: number; readOnly: boolean }) {
  const desktop = useIsDesktop()
  const blocksQuery = useBlocks(itemId, true)
  const createBlock = useCreateBlock(hobbyId, itemId)
  const updateBlock = useUpdateBlock(hobbyId, itemId)
  const deleteBlock = useDeleteBlock(hobbyId, itemId)
  const reorder = useReorderBlocks(hobbyId, itemId)
  const generate = useGenerateHowto(hobbyId, itemId)

  const blocks = blocksQuery.data ?? []
  const [adding, setAdding] = useState<EditableKind | null>(null)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [menuOpen, setMenuOpen] = useState(false)
  const [sheetBlock, setSheetBlock] = useState<RoadmapBlock | null>(null)
  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null)
  const [confirmRegenerate, setConfirmRegenerate] = useState(false)
  const [suggestion, setSuggestion] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const rootRef = useRef<HTMLDivElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)
  const addButtonRef = useRef<HTMLButtonElement>(null)
  // 웹의 추가 메뉴는 스크롤 영역에 잘리지 않게 화면(fixed)에 띄우고, 아래 공간이 모자라면 위로 연다.
  const [menuPos, setMenuPos] = useState<{ left: number; top?: number; bottom?: number } | null>(null)

  const ids = blocks.map((block) => block.id)
  const { itemProps, keyboardProps, message } = useDragSort(
    ids,
    (id, position) => {
      const next = ids.filter((entry) => entry !== id)
      next.splice(position, 0, id)
      setError(null)
      reorder.mutate(next, { onError: (e) => setError(errorMessageOf(e)) })
    },
    readOnly || !desktop,
  )

  const hasHowto = blocks.some((block) => block.kind === 'AI_HOWTO')

  useEffect(() => {
    if (!menuOpen || !desktop) return
    function onPointerDown(event: PointerEvent) {
      const target = event.target as Node
      if (rootRef.current?.contains(target) || menuRef.current?.contains(target)) return
      setMenuOpen(false)
    }
    document.addEventListener('pointerdown', onPointerDown)
    return () => document.removeEventListener('pointerdown', onPointerDown)
  }, [menuOpen, desktop])

  function toggleMenu() {
    if (!menuOpen && addButtonRef.current) {
      const rect = addButtonRef.current.getBoundingClientRect()
      const left = Math.max(8, Math.min(rect.left, window.innerWidth - 312))
      const below = window.innerHeight - rect.bottom
      setMenuPos(below >= 340 ? { left, top: rect.bottom + 4 } : { left, bottom: window.innerHeight - rect.top + 4 })
    }
    setMenuOpen((value) => !value)
  }

  function onRootKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (readOnly) return
    const target = event.target as HTMLElement
    if (event.key === '/' && !['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName) && !target.isContentEditable) {
      event.preventDefault()
      setMenuOpen(true)
    }
  }

  function chooseKind(kind: BlockKind) {
    setMenuOpen(false)
    setError(null)
    if (kind === 'AI_HOWTO') {
      if (!hasHowto) runGenerate()
      return
    }
    setEditingId(null)
    setAdding(kind)
  }

  function runGenerate() {
    setError(null)
    setConfirmRegenerate(false)
    generate.mutate(undefined, {
      onSuccess: (result) => setSuggestion(result.suggestedSearchQuery),
      onError: (e) => setError(errorMessageOf(e)),
    })
  }

  function submitNew(payload: BlockPayload) {
    if (!adding) return
    setError(null)
    createBlock.mutate(
      { kind: adding, payload },
      { onSuccess: () => setAdding(null), onError: (e) => setError(errorMessageOf(e)) },
    )
  }

  function submitEdit(block: RoadmapBlock, payload: BlockPayload) {
    setError(null)
    updateBlock.mutate(
      { id: block.id, payload },
      { onSuccess: () => setEditingId(null), onError: (e) => setError(errorMessageOf(e)) },
    )
  }

  function moveBy(block: RoadmapBlock, delta: number) {
    const index = ids.indexOf(block.id)
    const to = index + delta
    if (to < 0 || to >= ids.length) return
    const next = ids.filter((entry) => entry !== block.id)
    next.splice(to, 0, block.id)
    setError(null)
    reorder.mutate(next, { onError: (e) => setError(errorMessageOf(e)) })
  }

  function addSuggestedSearch() {
    if (!suggestion) return
    const query = suggestion
    createBlock.mutate(
      { kind: 'SEARCH', payload: { query } },
      { onSuccess: () => setSuggestion(null), onError: (e) => setError(errorMessageOf(e)) },
    )
  }

  function menuEntries(block: RoadmapBlock) {
    const index = ids.indexOf(block.id)
    const entries: { label: string; onSelect: () => void; disabled?: boolean }[] = []
    if (block.kind !== 'AI_HOWTO') {
      entries.push({
        label: '수정',
        onSelect: () => {
          setAdding(null)
          setEditingId(block.id)
        },
      })
    }
    entries.push({ label: '위로 이동', onSelect: () => moveBy(block, -1), disabled: index <= 0 || reorder.isPending })
    entries.push({ label: '아래로 이동', onSelect: () => moveBy(block, 1), disabled: index >= ids.length - 1 || reorder.isPending })
    entries.push({ label: '삭제', onSelect: () => setConfirmDeleteId(block.id) })
    return entries
  }

  const addMenuList = (
    <div role="menu" aria-label="블록 추가" className="flex flex-col gap-1">
      {ADD_ORDER.map((kind) => {
        const info = KIND_INFO[kind]
        const disabled = kind === 'AI_HOWTO' && hasHowto
        return (
          <button
            key={kind}
            type="button"
            role="menuitem"
            disabled={disabled}
            onClick={() => chooseKind(kind)}
            onKeyDown={(event) => {
              const items = [...(event.currentTarget.parentElement?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)') ?? [])]
              const index = items.indexOf(event.currentTarget)
              if (event.key === 'ArrowDown') {
                event.preventDefault()
                items[(index + 1) % items.length]?.focus()
              } else if (event.key === 'ArrowUp') {
                event.preventDefault()
                items[(index - 1 + items.length) % items.length]?.focus()
              } else if (event.key === 'Escape') {
                setMenuOpen(false)
              }
            }}
            className="flex min-h-12 w-full items-center gap-3 rounded-lg px-3 py-1.5 text-left hover:bg-surface focus-visible:bg-surface disabled:opacity-50"
          >
            <span className="flex h-8 w-8 flex-none items-center justify-center rounded-lg bg-surface text-ink-2">{info.icon}</span>
            <span className="flex min-w-0 flex-col">
              <span className="text-sm font-medium text-ink">{info.name}</span>
              <span className="text-xs text-ink-2">{disabled ? '이미 만들었어요' : info.description}</span>
            </span>
          </button>
        )
      })}
    </div>
  )

  return (
    <div ref={rootRef} onKeyDown={onRootKeyDown} className="flex min-w-0 flex-col gap-2 py-2" aria-label="항목 블록">
      {blocksQuery.isLoading ? <p className="text-sm text-ink-2">블록을 불러오고 있어요</p> : null}
      {blocksQuery.isError ? (
        <p className="text-sm text-ink-2">
          블록을 불러오지 못했어요.{' '}
          <button type="button" className="font-medium text-base-ink" onClick={() => blocksQuery.refetch()}>
            다시 받기
          </button>
        </p>
      ) : null}

      {!blocksQuery.isLoading && blocks.length === 0 && !adding && !generate.isPending ? (
        <p className="text-sm text-ink-2">{readOnly ? '붙은 블록이 없어요.' : '아직 붙은 블록이 없어요. 아래에서 필요한 블록을 붙여 보세요.'}</p>
      ) : null}

      <ul className="flex min-w-0 flex-col gap-2">
        {blocks.map((block) =>
          editingId === block.id && block.kind !== 'AI_HOWTO' ? (
            <li key={block.id}>
              <BlockEditor
                kind={block.kind}
                initial={block.payload}
                saving={updateBlock.isPending}
                error={error}
                onSubmit={(payload) => submitEdit(block, payload)}
                onCancel={() => setEditingId(null)}
              />
            </li>
          ) : (
            <li
              key={block.id}
              {...itemProps(block.id)}
              className="group min-w-0 rounded-xl border border-line bg-white data-[dragging]:opacity-40 data-[drop=after]:shadow-[0_2px_0_0_var(--color-base)] data-[drop=before]:shadow-[0_-2px_0_0_var(--color-base)]"
            >
              <div className="flex items-center gap-1 px-2 pt-1.5">
                {!readOnly && desktop ? (
                  <button
                    type="button"
                    {...keyboardProps(block.id, KIND_INFO[block.kind].name)}
                    aria-label={`${KIND_INFO[block.kind].name} 블록 순서 옮기기. 스페이스로 집고 방향키로 옮긴 뒤 스페이스로 놓아요`}
                    title="끌어서 옮기거나, 스페이스로 집어 방향키로 옮겨요"
                    className="inline-flex h-7 w-6 flex-none cursor-grab items-center justify-center rounded-md text-ink-3 opacity-0 hover:bg-surface focus-visible:opacity-100 group-hover:opacity-100"
                  >
                    <span aria-hidden="true" className="text-base leading-none tracking-[-0.2em]">⋮⋮</span>
                  </button>
                ) : null}
                <span className="flex flex-none items-center gap-1.5 text-xs font-medium text-ink-2">
                  {KIND_INFO[block.kind].icon}
                  {KIND_INFO[block.kind].name}
                </span>
                <span className="flex-1" />
                {!readOnly ? (
                  desktop ? (
                    <BlockMenu label={`${KIND_INFO[block.kind].name} 블록 메뉴`} entries={menuEntries(block)} />
                  ) : (
                    <button
                      type="button"
                      aria-label={`${KIND_INFO[block.kind].name} 블록 메뉴`}
                      onClick={() => setSheetBlock(block)}
                      className="inline-flex h-11 w-11 flex-none items-center justify-center rounded-lg text-ink-2 hover:bg-surface"
                    >
                      <MoreIcon width={18} height={18} />
                    </button>
                  )
                ) : null}
              </div>

              <div className="min-w-0 px-3 pb-3 pt-1">
                {confirmDeleteId === block.id ? (
                  <div className="flex flex-col gap-2 py-1">
                    <p className="text-sm text-ink">이 블록을 지울까요?</p>
                    <div className="flex gap-2">
                      <Button
                        type="button"
                        size="sm"
                        disabled={deleteBlock.isPending}
                        onClick={() =>
                          deleteBlock.mutate(block.id, {
                            onSuccess: () => setConfirmDeleteId(null),
                            onError: (e) => setError(errorMessageOf(e)),
                          })
                        }
                      >
                        지우기
                      </Button>
                      <Button type="button" size="sm" variant="secondary" onClick={() => setConfirmDeleteId(null)}>
                        그대로 둘게요
                      </Button>
                    </div>
                  </div>
                ) : (
                  <BlockBody
                    block={block}
                    hobbyId={hobbyId}
                    readOnly={readOnly}
                    regenerating={generate.isPending}
                    confirmRegenerate={confirmRegenerate}
                    onAskRegenerate={() => setConfirmRegenerate(true)}
                    onCancelRegenerate={() => setConfirmRegenerate(false)}
                    onRegenerate={runGenerate}
                    onToggleSublist={(payload) => submitEdit(block, payload)}
                  />
                )}
              </div>
            </li>
          ),
        )}
        <li className="sr-only" role="status" aria-live="polite">
          {message}
        </li>
      </ul>

      {generate.isPending && !hasHowto ? <AiProgressCard message="해 보는 방법을 만드는 중이에요" lines={3} /> : null}

      {suggestion && !readOnly ? (
        <div className="flex flex-wrap items-center gap-2 rounded-xl border border-dashed border-line-2 bg-surface px-3 py-2">
          <p className="min-w-0 flex-1 text-sm text-ink">
            이 검색어로 찾아보면 어때요? <span className="font-medium">“{suggestion}”</span>
          </p>
          <Button type="button" size="sm" disabled={createBlock.isPending} onClick={addSuggestedSearch}>
            추가
          </Button>
          <Button type="button" size="sm" variant="secondary" onClick={() => setSuggestion(null)}>
            닫기
          </Button>
        </div>
      ) : null}

      {adding ? (
        <BlockEditor
          kind={adding}
          initial={null}
          saving={createBlock.isPending}
          error={error}
          onSubmit={submitNew}
          onCancel={() => setAdding(null)}
        />
      ) : null}

      {error && !adding && editingId == null ? (
        <p role="alert" className="text-sm text-ink-2">
          {error}
        </p>
      ) : null}

      {!readOnly ? (
        <div className="relative">
          <button
            ref={addButtonRef}
            type="button"
            aria-haspopup="menu"
            aria-expanded={menuOpen}
            onClick={toggleMenu}
            className="inline-flex min-h-11 items-center gap-1.5 rounded-lg border border-dashed border-line-2 bg-white px-3 text-sm font-medium text-base-ink hover:bg-surface lg:min-h-9"
          >
            + 블록 추가
            <span className="hidden text-xs font-normal text-ink-3 lg:inline">( / 키로도 열려요)</span>
          </button>
          {menuOpen && desktop && menuPos
            ? createPortal(
                <div
                  ref={menuRef}
                  style={{ left: menuPos.left, top: menuPos.top, bottom: menuPos.bottom }}
                  className="fixed z-50 w-[19rem] max-w-[calc(100vw-1rem)] rounded-xl border border-line-2 bg-white p-1.5 shadow-lg"
                >
                  {addMenuList}
                </div>,
                document.body,
              )
            : null}
        </div>
      ) : null}

      {menuOpen && !desktop ? (
        <FloatingPanel title="블록 추가" storageKey="growit.roadmap.blockAdd" mobile onClose={() => setMenuOpen(false)}>
          {addMenuList}
        </FloatingPanel>
      ) : null}

      {sheetBlock ? (
        <FloatingPanel title={`${KIND_INFO[sheetBlock.kind].name} 블록`} storageKey="growit.roadmap.blockMenu" mobile onClose={() => setSheetBlock(null)}>
          <div role="menu" className="flex flex-col gap-1">
            {menuEntries(sheetBlock).map((entry) => (
              <button
                key={entry.label}
                type="button"
                role="menuitem"
                disabled={entry.disabled}
                onClick={() => {
                  setSheetBlock(null)
                  entry.onSelect()
                }}
                className="flex min-h-12 w-full items-center rounded-lg px-3 text-left text-body-min text-ink hover:bg-surface disabled:opacity-40"
              >
                {entry.label}
              </button>
            ))}
          </div>
        </FloatingPanel>
      ) : null}
    </div>
  )
}

/** 웹 블록 카드의 ⋯ 메뉴(수정·위/아래 이동(키보드용)·삭제). */
function BlockMenu({ label, entries }: { label: string; entries: { label: string; onSelect: () => void; disabled?: boolean }[] }) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!open) return
    function onPointerDown(event: PointerEvent) {
      if (ref.current && !ref.current.contains(event.target as Node)) setOpen(false)
    }
    function onKey(event: globalThis.KeyboardEvent) {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])
  return (
    <div ref={ref} className="relative flex-none">
      <button
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={label}
        onClick={() => setOpen((value) => !value)}
        className="inline-flex h-8 w-8 items-center justify-center rounded-md text-ink-2 opacity-0 hover:bg-surface focus-visible:opacity-100 group-hover:opacity-100 aria-expanded:opacity-100"
      >
        <MoreIcon width={16} height={16} />
      </button>
      {open ? (
        <div role="menu" aria-label={label} className="absolute right-0 top-full z-30 mt-1 min-w-[9rem] rounded-lg border border-line-2 bg-white p-1.5 shadow-sm">
          {entries.map((entry) => (
            <button
              key={entry.label}
              type="button"
              role="menuitem"
              disabled={entry.disabled}
              onClick={() => {
                setOpen(false)
                entry.onSelect()
              }}
              className="flex min-h-9 w-full items-center whitespace-nowrap rounded-md px-3 text-left text-sm text-ink hover:bg-surface focus-visible:bg-surface disabled:opacity-40"
            >
              {entry.label}
            </button>
          ))}
        </div>
      ) : null}
    </div>
  )
}

function BlockBody({
  block,
  hobbyId,
  readOnly,
  regenerating,
  confirmRegenerate,
  onAskRegenerate,
  onCancelRegenerate,
  onRegenerate,
  onToggleSublist,
}: {
  block: RoadmapBlock
  hobbyId: number
  readOnly: boolean
  regenerating: boolean
  confirmRegenerate: boolean
  onAskRegenerate: () => void
  onCancelRegenerate: () => void
  onRegenerate: () => void
  onToggleSublist: (payload: BlockPayload) => void
}) {
  const payload = block.payload

  if (block.kind === 'MEMO') {
    return <p className="whitespace-pre-wrap break-words font-serif text-body-min leading-[1.6] text-ink">{String(payload.text ?? '')}</p>
  }

  if (block.kind === 'LINK') {
    const url = payload.url
    const title = typeof payload.title === 'string' && payload.title ? payload.title : String(payload.host ?? '')
    const category = LINK_CATEGORY_LABELS[(payload.category as LinkCategory) ?? 'OTHER'] ?? '기타'
    const inner = (
      <>
        <span className="flex min-w-0 flex-col">
          <span className="truncate text-sm font-medium text-ink">{title}</span>
          <span className="truncate text-xs text-ink-2">{String(payload.host ?? '')}</span>
        </span>
        <span className="flex-none rounded-md bg-tag-topic-bg px-2 py-0.5 text-xs font-medium text-tag-topic-fg">{category}</span>
      </>
    )
    return isWebUrl(url) ? (
      <a
        href={url}
        target="_blank"
        rel="noopener noreferrer"
        className="flex min-h-11 items-center justify-between gap-3 rounded-lg bg-surface px-3 py-2 no-underline"
      >
        {inner}
      </a>
    ) : (
      <div className="flex items-center justify-between gap-3 rounded-lg bg-surface px-3 py-2">{inner}</div>
    )
  }

  if (block.kind === 'TIMER') {
    return <TimerBody minutes={Number(payload.minutes ?? 10)} hobbyId={hobbyId} />
  }

  if (block.kind === 'SEARCH') {
    const query = String(payload.query ?? '')
    return (
      <div className="flex flex-col gap-2">
        <p className="break-words text-sm font-medium text-ink">“{query}”</p>
        <div className="flex flex-wrap gap-2">
          <a
            href={youtubeSearchUrl(query)}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex min-h-11 items-center rounded-lg border border-line-2 bg-white px-3 text-sm font-medium text-ink no-underline lg:min-h-9"
          >
            유튜브에서 찾기
          </a>
          <a
            href={googleSearchUrl(query)}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex min-h-11 items-center rounded-lg border border-line-2 bg-white px-3 text-sm font-medium text-ink no-underline lg:min-h-9"
          >
            구글에서 찾기
          </a>
        </div>
      </div>
    )
  }

  if (block.kind === 'SUBLIST') {
    const items = (Array.isArray(payload.items) ? payload.items : []) as { text: string; done: boolean }[]
    const done = items.filter((item) => item.done).length
    return (
      <div className="flex flex-col gap-1">
        <p className="text-xs text-ink-2">
          {items.length}개 중 {done}개 체크했어요
        </p>
        {items.map((item, index) => (
          <label key={index} className="flex min-h-11 cursor-pointer items-center gap-3 lg:min-h-9">
            <input
              type="checkbox"
              checked={!!item.done}
              disabled={readOnly}
              onChange={(event) =>
                onToggleSublist({
                  items: items.map((entry, i) => (i === index ? { text: entry.text, done: event.target.checked } : { text: entry.text, done: !!entry.done })),
                })
              }
              className="h-5 w-5 flex-none accent-[var(--color-base)]"
            />
            <span className={['min-w-0 break-words text-sm', item.done ? 'text-ink-2 line-through' : 'text-ink'].join(' ')}>{item.text}</span>
          </label>
        ))}
      </div>
    )
  }

  // AI_HOWTO
  const steps = asStrings(payload.steps)
  const checks = asStrings(payload.checks)
  const mistakes = asStrings(payload.mistakes)
  return (
    <div className="flex flex-col gap-3">
      <p className="text-xs text-ink-2">AI가 제안한 일반적인 연습 순서예요. 내 상황에 맞게 바꿔 쓰세요.</p>
      <div className="flex flex-col gap-1.5">
        <h4 className="text-sm font-semibold text-ink">해 보는 순서</h4>
        <ol className="flex flex-col gap-1.5">
          {steps.map((step, index) => (
            <li key={index} className="flex gap-2.5">
              <span aria-hidden="true" className="mt-0.5 flex h-5 w-5 flex-none items-center justify-center rounded-full bg-base text-xs font-semibold text-white">
                {index + 1}
              </span>
              <span className="min-w-0 break-words text-sm leading-[1.5] text-ink">{step}</span>
            </li>
          ))}
        </ol>
      </div>
      {checks.length > 0 ? (
        <div className="flex flex-col gap-1 rounded-lg bg-[color-mix(in_srgb,var(--color-base)_7%,white)] p-2.5">
          <h4 className="text-sm font-semibold text-base-ink">이렇게 되면 된 거예요</h4>
          <ul className="flex list-disc flex-col gap-0.5 pl-5">
            {checks.map((check, index) => (
              <li key={index} className="break-words text-sm text-ink">
                {check}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
      {mistakes.length > 0 ? (
        <div className="flex flex-col gap-1 rounded-lg bg-note-sand-bg p-2.5">
          <h4 className="text-sm font-semibold text-note-sand-fg">흔히 하는 실수</h4>
          <ul className="flex list-disc flex-col gap-0.5 pl-5">
            {mistakes.map((mistake, index) => (
              <li key={index} className="break-words text-sm text-ink">
                {mistake}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
      {!readOnly ? (
        regenerating ? (
          <AiProgressCard message="해 보는 방법을 다시 만드는 중이에요" lines={2} />
        ) : confirmRegenerate ? (
          <div className="flex flex-col gap-2 rounded-lg bg-surface p-2.5">
            <p className="text-sm text-ink">다시 만들면 지금 내용이 바뀌어요. 하루 3번까지 다시 만들 수 있어요. 만들까요?</p>
            <div className="flex gap-2">
              <Button type="button" size="sm" onClick={onRegenerate}>
                다시 만들기
              </Button>
              <Button type="button" size="sm" variant="secondary" onClick={onCancelRegenerate}>
                그대로 둘게요
              </Button>
            </div>
          </div>
        ) : (
          <button
            type="button"
            onClick={onAskRegenerate}
            className="inline-flex min-h-11 w-fit items-center rounded-lg px-2 text-sm font-medium text-base-ink hover:bg-surface lg:min-h-9"
          >
            다시 만들기
          </button>
        )
      ) : null}
    </div>
  )
}

/** 타이머: 시작·일시정지·초기화. 끝나면 화면에만 알리고 [기록하기]로 이 분야가 선택된 기록 작성 화면을 연다. */
function TimerBody({ minutes, hobbyId }: { minutes: number; hobbyId: number }) {
  const total = Math.max(1, minutes) * 60
  const [remaining, setRemaining] = useState(total)
  const [running, setRunning] = useState(false)
  const endAtRef = useRef(0)

  useEffect(() => {
    if (!running) return
    const timer = window.setInterval(() => {
      const left = Math.max(0, Math.ceil((endAtRef.current - Date.now()) / 1000))
      setRemaining(left)
      if (left <= 0) setRunning(false)
    }, 250)
    return () => window.clearInterval(timer)
  }, [running])

  function start() {
    endAtRef.current = Date.now() + remaining * 1000
    setRunning(true)
  }
  function pause() {
    setRunning(false)
  }
  function reset() {
    setRunning(false)
    setRemaining(total)
  }

  const done = remaining <= 0
  const shown = `${String(Math.floor(remaining / 60)).padStart(2, '0')}:${String(remaining % 60).padStart(2, '0')}`
  return (
    <div className="flex flex-col gap-2">
      <p
        className="font-serif text-[2.25rem] leading-none tabular-nums text-ink"
        role="timer"
        aria-label={`남은 시간 ${Math.floor(remaining / 60)}분 ${remaining % 60}초`}
      >
        {shown}
      </p>
      <div className="flex flex-wrap items-center gap-2">
        {running ? (
          <Button type="button" size="sm" variant="secondary" onClick={pause}>
            일시정지
          </Button>
        ) : (
          <Button type="button" size="sm" disabled={done} onClick={start}>
            {remaining < total ? '이어서 시작' : '시작'}
          </Button>
        )}
        <Button type="button" size="sm" variant="secondary" onClick={reset} disabled={remaining === total && !running}>
          초기화
        </Button>
      </div>
      {done ? (
        <div role="status" className="flex flex-wrap items-center gap-2 rounded-lg bg-[color-mix(in_srgb,var(--color-base)_9%,white)] px-3 py-2">
          <p className="min-w-0 flex-1 text-sm text-ink">시간이 다 됐어요. 한 줄 남겨 볼까요?</p>
          <Link
            to={`/records/new?hobbyId=${hobbyId}`}
            className="inline-flex min-h-11 items-center rounded-lg bg-base px-4 text-sm font-medium text-white no-underline lg:min-h-9"
          >
            기록하기
          </Link>
        </div>
      ) : null}
    </div>
  )
}
