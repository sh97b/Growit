import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Button } from '../components/Button'
import { Loading } from '../components/Loading'
import { Note } from '../components/Note'
import { Tag } from '../components/Tag'
import { ChevronLeftIcon, ChevronRightIcon, PencilIcon, TrashIcon } from '../components/icons'
import { useHobbies } from '../hooks/useHobbies'
import { useDeleteRecord, useRecord } from '../hooks/useRecords'
import { ApiError } from '../lib/api'
import { formatMonthDay } from '../lib/date'
import { FEELINGS } from '../lib/recordForm'

export function RecordDetailPage() {
  const { id } = useParams()
  const recordId = Number(id)
  const navigate = useNavigate()
  const { data: record, isLoading, isError, error } = useRecord(recordId)
  const { data: hobbies } = useHobbies(true)
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
    <div className="flex flex-col gap-6 lg:max-w-[760px]">
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
