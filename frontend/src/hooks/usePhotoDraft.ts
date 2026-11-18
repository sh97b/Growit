import { useMemo, useState } from 'react'
import {
  cropRectFor,
  photoToDotGrid,
  type PhotoGrid,
  type SourceImage,
} from '../lib/photoDots'

const MAX_FILE_BYTES = 10 * 1024 * 1024
/** 화면에서 자르고 칸으로 바꾸는 데는 큰 원본이 필요 없어서, 읽자마자 이 크기로 줄인 사본만 메모리에 둔다. */
const WORK_MAX_SIDE_PX = 480

export const PHOTO_TITLE_MAX = 20

export const RATIOS = [
  { label: '1:1', aspect: 1 },
  { label: '4:3', aspect: 4 / 3 },
  { label: '3:4', aspect: 3 / 4 },
] as const

type Working = { image: SourceImage; canvas: HTMLCanvasElement }

async function decodePhoto(file: File): Promise<Working> {
  let bitmap: ImageBitmap
  try {
    // 스마트폰 사진의 회전 정보(EXIF)를 반영해서 읽는다
    bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' })
  } catch {
    bitmap = await createImageBitmap(file)
  }
  const scale = Math.min(1, WORK_MAX_SIDE_PX / Math.max(bitmap.width, bitmap.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.max(1, Math.round(bitmap.width * scale))
  canvas.height = Math.max(1, Math.round(bitmap.height * scale))
  const context = canvas.getContext('2d', { willReadFrequently: true })
  if (!context) {
    bitmap.close()
    throw new Error('canvas unavailable')
  }
  context.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  bitmap.close()
  const pixels = context.getImageData(0, 0, canvas.width, canvas.height)
  return { image: { width: canvas.width, height: canvas.height, data: pixels.data }, canvas }
}

/**
 * 사진으로 도트 그림 만들기의 상태. 시작 흐름의 단계를 오가도 고른 사진이 사라지지 않게 부모(시작 흐름)가 들고 있는다.
 * 원본 파일은 읽은 뒤 바로 버리고(저장·업로드하지 않음), 줄인 사본과 변환 결과만 이 안에 있다가 흐름이 닫히면 같이 사라진다.
 */
export function usePhotoDraft(goalDays: number | null) {
  const [working, setWorking] = useState<Working | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [ratioIndex, setRatioIndex] = useState(0)
  const [zoom, setZoom] = useState(1)
  const [offsetX, setOffsetX] = useState(0)
  const [offsetY, setOffsetY] = useState(0)
  const [removeBackground, setRemoveBackground] = useState(true)
  const [title, setTitle] = useState('')

  const aspect = RATIOS[ratioIndex].aspect
  const crop = useMemo(
    () => (working ? cropRectFor(working.image.width, working.image.height, aspect, zoom, offsetX, offsetY) : null),
    [working, aspect, zoom, offsetX, offsetY],
  )
  const grid: PhotoGrid | null = useMemo(
    () => (working && crop && goalDays != null ? photoToDotGrid(working.image, crop, goalDays, { removeBackground }) : null),
    [working, crop, goalDays, removeBackground],
  )

  async function loadFile(file: File | undefined) {
    if (!file) return
    setError(null)
    if (!file.type.startsWith('image/')) {
      setError('사진(이미지) 파일만 고를 수 있어요.')
      return
    }
    if (file.size > MAX_FILE_BYTES) {
      setError('10MB 이하의 사진을 골라 주세요.')
      return
    }
    setLoading(true)
    try {
      const decoded = await decodePhoto(file)
      setWorking(decoded)
      setRatioIndex(decoded.image.height > decoded.image.width ? 2 : decoded.image.width > decoded.image.height ? 1 : 0)
      setZoom(1)
      setOffsetX(0)
      setOffsetY(0)
    } catch {
      setError('사진을 읽지 못했어요. 다른 사진으로 다시 해 볼까요?')
    } finally {
      setLoading(false)
    }
  }

  return {
    working, crop, grid, error, loading, aspect,
    ratioIndex, setRatioIndex, zoom, setZoom, offsetX, setOffsetX, offsetY, setOffsetY,
    removeBackground, setRemoveBackground, title, setTitle, loadFile,
    clear: () => setWorking(null),
  }
}

export type PhotoDraft = ReturnType<typeof usePhotoDraft>
