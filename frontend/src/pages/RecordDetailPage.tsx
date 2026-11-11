import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { AiLabel } from '../components/AiLabel'
import { Button } from '../components/Button'
import { ChangeFeedbackRow } from '../components/ChangeFeedbackRow'
import { EvidenceChip } from '../components/EvidenceChip'
import { FeedbackRow } from '../components/FeedbackRow'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
import { Tag } from '../components/Tag'
import {
  ChevronLeftIcon,
  ChevronRightIcon,
  PencilIcon,
  PlusIcon,
  RefreshIcon,
  TrashIcon,
  XIcon,
} from '../components/icons'
import { useCurrentUser } from '../hooks/useCurrentUser'
import { useHobbies } from '../hooks/useHobbies'
import { useDeleteRecord, useEditAnalysisTags, useReanalyzeRecord, useRecord, useSubmitAnalysisFeedback } from '../hooks/useRecords'
import { ApiError } from '../lib/api'
import { formatMonthDay } from '../lib/date'
import { FEELINGS } from '../lib/recordForm'
import type { RecordAnalysis, RecordDetail } from '../lib/records'

const TAG_MAX_LENGTH = 12
const TAGS_MAX_COUNT = 3

export function RecordDetailPage() {
  const { id } = useParams()
  const recordId = Number(id)
  const navigate = useNavigate()
  const { data: record, isLoading, isError, error } = useRecord(recordId)
  const { data: hobbies } = useHobbies(true)
  const { data: currentUser } = useCurrentUser()
  const deleteRecord = useDeleteRecord()
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  if (isLoading) {
    return <Loading />
  }

  if (isError || !record) {
    const notFound = error instanceof ApiError && error.status === 404
    return (
      <div className="flex flex-col items-center gap-4 py-16 text-center">
        <p className="text-ink-2">
          {notFound ? '기록을 찾을 수 없어요. 지워졌거나 다른 계정의 기록이에요.' : '잠시 후 다시 시도해 주세요.'}
        </p>
        <Link to="/records" className="text-sm font-medium text-base-ink underline underline-offset-4">
          지난 기록으로 가기
        </Link>
      </div>
    )
  }

  const hobbyName = hobbies?.find((hobby) => hobby.id === record.hobbyId)?.name ?? ''
  const feelingLabel = FEELINGS.find((item) => item.value === record.feeling)?.label

  async function handleDelete() {
    await deleteRecord.mutateAsync(recordId)
    navigate('/records', { replace: true })
  }

  return (
    <div className="flex flex-col gap-6 lg:mx-auto lg:max-w-[720px]">
      <nav className="text-sm text-ink-2">
        <Link to="/records" className="text-ink-2 no-underline">
          지난 기록
        </Link>{' '}
        › {formatMonthDay(record.recordDate)} 기록
      </nav>

      <div className="flex flex-col gap-3">
        <span className="text-sm font-medium text-ink-2">
          {formatMonthDay(record.recordDate)} · {hobbyName}
          {record.minutes != null ? ` · ${record.minutes}분` : ''}
        </span>
        {record.aiStatus === 'DONE' && record.analysis?.title ? (
          <h1 className="text-page-title">{record.analysis.title}</h1>
        ) : null}
        <p className="font-serif text-xl leading-[1.7] whitespace-pre-wrap">{record.content}</p>

        {feelingLabel ? (
          <div className="flex flex-wrap gap-2">
            <Tag variant="feel">{feelingLabel}</Tag>
          </div>
        ) : null}

        {record.difficulty ? <p className="text-sm leading-[1.6] text-ink-2">막힌 점이나 나아진 점 · {record.difficulty}</p> : null}

        <div className="flex gap-2 pt-1">
          <Button type="button" variant="secondary" size="sm" onClick={() => navigate(`/records/${record.id}/edit`)}>
            <PencilIcon width={16} height={16} />
            수정
          </Button>
          <Button type="button" variant="secondary" size="sm" onClick={() => setConfirmingDelete(true)}>
            <TrashIcon width={16} height={16} />
            삭제
          </Button>
        </div>
      </div>

      {confirmingDelete ? (
        <Note variant="sand">
          <div className="flex flex-col gap-3">
            <p>이 기록을 지울까요? 지우면 되돌릴 수 없어요.</p>
            <div className="flex gap-2">
              <Button type="button" variant="secondary" size="sm" onClick={() => setConfirmingDelete(false)}>
                취소
              </Button>
              <Button type="button" size="sm" disabled={deleteRecord.isPending} onClick={handleDelete}>
                삭제하기
              </Button>
            </div>
          </div>
        </Note>
      ) : null}

      <hr className="border-line" />

      <AnalysisSection record={record} aiEnabled={currentUser?.aiEnabled ?? true} />

      {record.previousRecord || record.nextRecord ? (
        <div className="flex items-center justify-between border-t border-line pt-4 text-sm font-medium">
          {record.previousRecord ? (
            <Link
              to={`/records/${record.previousRecord.id}`}
              className="inline-flex items-center gap-1 text-ink no-underline"
            >
              <ChevronLeftIcon width={16} height={16} />
              이전 기록 · {formatMonthDay(record.previousRecord.recordDate)}
            </Link>
          ) : (
            <span />
          )}
          {record.nextRecord ? (
            <Link
              to={`/records/${record.nextRecord.id}`}
              className="inline-flex items-center gap-1 text-ink no-underline"
            >
              다음 기록 · {formatMonthDay(record.nextRecord.recordDate)}
              <ChevronRightIcon width={16} height={16} />
            </Link>
          ) : (
            <span />
          )}
        </div>
      ) : null}
    </div>
  )
}

/**
 * "아쉬워요" 안내 링크를 따로 두지 않는다. 같은 페이지 아래에 태그 맞아요/달라요·태그 고치기
 * 영역이 이미 보이므로, 저장 직후 화면과 달리 여기서 또 안내 링크를 만들면 중복이 된다.
 */
function ChangeBlock({ record, analysis }: { record: RecordDetail; analysis: RecordAnalysis }) {
  const change = analysis.change
  if (!change) return null

  return (
    <div className="flex flex-col gap-3.5 rounded-2xl border border-[color-mix(in_srgb,var(--color-base)_28%,white)] bg-[color-mix(in_srgb,var(--color-base)_9%,white)] p-6">
      <AiLabel>달라진 점</AiLabel>
      <p className="font-serif text-lg leading-[1.55]">{change.text}</p>
      <div>
        <EvidenceChip record={change.evidence} />
      </div>
      <ChangeFeedbackRow record={record} analysis={analysis} />
    </div>
  )
}

function AnalysisSection({ record, aiEnabled }: { record: RecordDetail; aiEnabled: boolean }) {
  const reanalyze = useReanalyzeRecord()
  const feedback = useSubmitAnalysisFeedback()
  const editTags = useEditAnalysisTags()

  const [tagDrafts, setTagDrafts] = useState<string[]>([])
  const [newTagInput, setNewTagInput] = useState('')
  const [tagsError, setTagsError] = useState<string | null>(null)
  const [tagsFeedbackError, setTagsFeedbackError] = useState<string | null>(null)

  if (record.aiStatus === 'PENDING') {
    return (
      <div className="flex flex-col gap-2.5">
        <AiLabel>정리하는 중이에요</AiLabel>
        <div className="h-3.5 w-[70%] animate-pulse rounded-full bg-rest" />
      </div>
    )
  }

  if (record.aiStatus === 'FAILED') {
    return (
      <div className="flex flex-col gap-3">
        <Note variant="sand">지금은 AI 정리를 할 수 없었어요. 기록은 그대로 남아 있어요.</Note>
        {aiEnabled ? (
          <button
            type="button"
            disabled={reanalyze.isPending}
            onClick={() => reanalyze.mutate(record.id)}
            className="inline-flex min-h-11 w-fit items-center gap-1.5 text-sm font-medium text-base-ink underline underline-offset-4"
          >
            <RefreshIcon width={16} height={16} />
            다시 시도하기
          </button>
        ) : (
          <Link to="/settings/ai" className="inline-flex min-h-11 w-fit items-center text-sm font-medium text-base-ink underline underline-offset-4">
            설정에서 AI 정리 켜기
          </Link>
        )}
        {reanalyze.isError ? (
          <Note variant="sand">
            {reanalyze.error instanceof ApiError ? reanalyze.error.message : '잠시 후 다시 시도해 주세요'}
          </Note>
        ) : null}
      </div>
    )
  }

  if (record.aiStatus === 'SKIPPED') {
    return (
      <Note variant="calm">
        <span>
          AI 정리가 꺼져 있어요.{' '}
          <Link to="/settings/ai" className="font-medium text-base-ink underline underline-offset-4">
            설정에서 켤 수 있어요
          </Link>
        </span>
      </Note>
    )
  }

  if (record.aiStatus === 'NONE' || !record.analysis) {
    return <Note variant="calm">이 기록은 아직 정리되지 않았어요.</Note>
  }

  const analysis = record.analysis

  function openTagsEdit() {
    setTagDrafts(analysis.tags)
    setTagsError(null)
    setNewTagInput('')
  }

  function removeTagDraft(index: number) {
    setTagDrafts((prev) => prev.filter((_, i) => i !== index))
  }

  function addTagDraft() {
    const trimmed = newTagInput.trim()
    if (!trimmed) return
    if (trimmed.length > TAG_MAX_LENGTH) {
      setTagsError(`태그는 ${TAG_MAX_LENGTH}자 이하여야 해요`)
      return
    }
    if (tagDrafts.includes(trimmed)) {
      setNewTagInput('')
      return
    }
    if (tagDrafts.length >= TAGS_MAX_COUNT) {
      setTagsError(`태그는 최대 ${TAGS_MAX_COUNT}개예요`)
      return
    }
    setTagDrafts((prev) => [...prev, trimmed])
    setNewTagInput('')
    setTagsError(null)
  }

  function saveTags(markSaved: () => void) {
    if (tagDrafts.length === 0) {
      setTagsError('태그를 1개 이상 남겨 주세요')
      return
    }
    editTags.mutate(
      { id: record.id, tags: tagDrafts },
      {
        onSuccess: () => markSaved(),
        onError: (mutationError) =>
          setTagsError(mutationError instanceof ApiError ? mutationError.message : '고치지 못했어요'),
      },
    )
  }

  function handleConfirm() {
    if (feedback.isPending) return
    setTagsFeedbackError(null)
    feedback.mutate(
      { id: record.id, payload: { tags: 'CONFIRMED' } },
      {
        onError: (mutationError) =>
          setTagsFeedbackError(mutationError instanceof ApiError ? mutationError.message : '잠시 후 다시 시도해 주세요'),
      },
    )
  }

  function handleDispute() {
    if (feedback.isPending) return
    setTagsFeedbackError(null)
    feedback.mutate(
      { id: record.id, payload: { tags: 'DISPUTED' } },
      {
        onError: (mutationError) =>
          setTagsFeedbackError(mutationError instanceof ApiError ? mutationError.message : '잠시 후 다시 시도해 주세요'),
      },
    )
  }

  return (
    <div className="flex flex-col gap-3">
      <ChangeBlock record={record} analysis={analysis} />

      <AiLabel>AI 정리</AiLabel>

      {analysis.tags.length > 0 ? (
        <div className="flex flex-wrap gap-2">
          {analysis.tags.map((tag) => (
            <Tag key={tag} variant="topic">
              {tag}
            </Tag>
          ))}
        </div>
      ) : null}

      <FeedbackRow
        question="태그가 맞나요?"
        variant="text"
        status={
          analysis.tagsFeedback === 'CONFIRMED'
            ? 'positive'
            : analysis.tagsFeedback === 'DISPUTED'
              ? 'negative'
              : analysis.tagsFeedback === 'EDITED'
                ? 'edited'
                : null
        }
        pending={feedback.isPending}
        onPositive={handleConfirm}
        onNegative={handleDispute}
        onBeforeOpenEdit={openTagsEdit}
        editTriggerLabel="태그 고치기"
        editedMessage="내가 고친 태그예요"
        editedActionLabel="다시 고치기"
        errorMessage={tagsFeedbackError}
        editArea={({ markSaved, cancel }) => (
          <div className="flex flex-col gap-3 rounded-2xl bg-surface p-[18px]">
            {analysis.tagsFeedback === 'DISPUTED' ? <p className="text-sm font-medium text-ink">어떻게 고칠까요?</p> : null}
            <div className="flex flex-wrap gap-2">
              {tagDrafts.map((tag, index) => (
                <button
                  key={tag}
                  type="button"
                  onClick={() => removeTagDraft(index)}
                  aria-label={`${tag} 태그 지우기`}
                  className="inline-flex min-h-11 items-center gap-1.5 rounded-lg border border-line-2 bg-white px-4 text-sm font-medium text-ink"
                >
                  {tag}
                  <XIcon width={14} height={14} />
                </button>
              ))}
              {tagDrafts.length < TAGS_MAX_COUNT ? (
                <div className="flex items-center gap-1.5">
                  <input
                    value={newTagInput}
                    onChange={(event) => setNewTagInput(event.target.value)}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter') {
                        event.preventDefault()
                        addTagDraft()
                      }
                    }}
                    placeholder="태그 추가"
                    maxLength={TAG_MAX_LENGTH}
                    autoFocus
                    className="h-11 w-28 rounded-lg border border-input bg-white px-3 text-sm text-ink outline-none"
                  />
                  <button
                    type="button"
                    onClick={addTagDraft}
                    aria-label="태그 추가"
                    className="inline-flex h-11 w-11 items-center justify-center rounded-lg border border-line-2 bg-white text-ink"
                  >
                    <PlusIcon width={16} height={16} />
                  </button>
                </div>
              ) : null}
            </div>
            {tagsError ? <p className="text-[13px] text-[#6B3F0F]">{tagsError}</p> : null}
            <p className="text-sm text-ink-2">고친 내용은 이 기록의 태그에만 반영돼요. 쓰신 글은 바뀌지 않아요.</p>
            <div className="flex gap-2">
              <Button type="button" size="sm" disabled={editTags.isPending} onClick={() => saveTags(markSaved)}>
                저장
              </Button>
              <Button type="button" variant="secondary" size="sm" onClick={cancel}>
                취소
              </Button>
            </div>
          </div>
        )}
      />

      {analysis.relatedRecords.length > 0 ? (
        <div className="flex flex-col gap-2 pt-2">
          <h2 className="text-card-title">이어서 볼 기록</h2>
          <div className="flex flex-col gap-2">
            {analysis.relatedRecords.map((related) => (
              <EvidenceChip key={related.id} record={related} block />
            ))}
          </div>
        </div>
      ) : null}
    </div>
  )
}
