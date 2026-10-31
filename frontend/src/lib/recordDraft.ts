import type { Feeling } from './records'

export type RecordDraft = {
  hobbyId: number | null
  content: string
  minutes: number | null
  difficulty: string
  feeling: Feeling | null
}

function draftKey(userId: number): string {
  return `growit_draft_${userId}`
}

export function loadRecordDraft(userId: number): RecordDraft | null {
  try {
    const raw = localStorage.getItem(draftKey(userId))
    if (!raw) return null
    return JSON.parse(raw) as RecordDraft
  } catch {
    return null
  }
}

export function saveRecordDraft(userId: number, draft: RecordDraft): void {
  try {
    localStorage.setItem(draftKey(userId), JSON.stringify(draft))
  } catch {
    // 저장소를 쓸 수 없어도 서비스는 계속 동작해야 한다
  }
}

export function clearRecordDraft(userId: number): void {
  try {
    localStorage.removeItem(draftKey(userId))
  } catch {
    // 저장소를 쓸 수 없어도 서비스는 계속 동작해야 한다
  }
}

export function isRecordDraftEmpty(draft: Omit<RecordDraft, 'hobbyId'>): boolean {
  return (
    draft.content.trim() === '' &&
    draft.minutes == null &&
    draft.difficulty.trim() === '' &&
    draft.feeling == null
  )
}
