import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { RecordSavedPanel } from '../components/RecordSavedPanel'
import { Loading } from '../components/Loading'
import { useHobbies } from '../hooks/useHobbies'
import { useRecord } from '../hooks/useRecords'
import { ApiError } from '../lib/api'
import { fetchRecords } from '../lib/records'

export function RecordSavedPage() {
  const { id } = useParams()
  const recordId = Number(id)
  const navigate = useNavigate()
  const { data: record, isLoading, isError, error } = useRecord(recordId)
  const { data: hobbies } = useHobbies(true)
  const totalQuery = useQuery({ queryKey: ['records', 'total'], queryFn: () => fetchRecords({ size: 1 }) })

  if (isLoading || totalQuery.isLoading) {
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
  const isFirstRecord = (totalQuery.data?.totalCount ?? 0) === 1

  return (
    <RecordSavedPanel
      record={record}
      hobbyName={hobbyName}
      isFirstRecord={isFirstRecord}
      onHome={() => navigate('/')}
      onViewDetail={() => navigate(`/records/${record.id}`)}
      onEdit={() => navigate(`/records/${record.id}/edit`)}
    />
  )
}
