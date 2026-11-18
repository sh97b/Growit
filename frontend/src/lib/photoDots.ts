/*
 * 사진을 도트 그림 격자로 바꾸는 순수 함수들. 브라우저에서만 쓰고 서버에는 결과 격자만 보낸다.
 * 같은 입력이면 항상 같은 결과가 나오도록 무작위·정렬 불안정성을 쓰지 않는다.
 */

export const MAX_GRID_SIZE = 40
export const MIN_COLORS = 4
export const MAX_COLORS = 8
export const DEFAULT_COLORS = 6

export type PhotoCell = { x: number; y: number; c: number }

export type PhotoGrid = {
  width: number
  height: number
  palette: string[]
  cells: PhotoCell[]
}

/** 원본 픽셀 좌표계의 자른 영역. */
export type CropRect = { x: number; y: number; w: number; h: number }

export type SourceImage = { width: number; height: number; data: Uint8ClampedArray }

/**
 * 자른 비율(가로/세로)에 맞는 격자 크기를 고른다. W×H가 칸 수 N 이상이면서 N에 가장 가깝고(초과분이 적고),
 * 비율이 자른 사진과 비슷한 것을 고른다. 초과분은 {@link photoToDotGrid}가 모서리부터 덜어 낸다.
 */
export function chooseGridSize(goalDays: number, aspect: number): { width: number; height: number } {
  let best: { width: number; height: number; cost: number } | null = null
  for (let width = 1; width <= MAX_GRID_SIZE; width += 1) {
    for (let height = 1; height <= MAX_GRID_SIZE; height += 1) {
      const area = width * height
      if (area < goalDays) continue
      const cost = (area - goalDays) / goalDays + 2 * Math.abs(Math.log(width / height / aspect))
      if (best === null || cost < best.cost - 1e-12) best = { width, height, cost }
    }
  }
  return { width: best!.width, height: best!.height }
}

/** 이미지 전체에서 비율(aspect)대로 자르되, zoom(1 이상)만큼 확대하고 offset(-1~1)만큼 가운데에서 옮긴다. */
export function cropRectFor(
  imageWidth: number,
  imageHeight: number,
  aspect: number,
  zoom: number,
  offsetX: number,
  offsetY: number,
): CropRect {
  let w: number
  let h: number
  if (imageWidth / imageHeight > aspect) {
    h = imageHeight
    w = h * aspect
  } else {
    w = imageWidth
    h = w / aspect
  }
  w /= Math.max(1, zoom)
  h /= Math.max(1, zoom)
  const clamp = (value: number) => Math.max(-1, Math.min(1, value))
  const x = imageWidth / 2 + clamp(offsetX) * ((imageWidth - w) / 2) - w / 2
  const y = imageHeight / 2 + clamp(offsetY) * ((imageHeight - h) / 2) - h / 2
  return { x, y, w, h }
}

type Rgb = [number, number, number]

function blockAverage(image: SourceImage, x0: number, y0: number, x1: number, y1: number): Rgb {
  const startX = Math.max(0, Math.min(image.width - 1, Math.floor(x0)))
  const startY = Math.max(0, Math.min(image.height - 1, Math.floor(y0)))
  const endX = Math.max(startX + 1, Math.min(image.width, Math.ceil(x1)))
  const endY = Math.max(startY + 1, Math.min(image.height, Math.ceil(y1)))
  let r = 0
  let g = 0
  let b = 0
  let count = 0
  for (let y = startY; y < endY; y += 1) {
    for (let x = startX; x < endX; x += 1) {
      const i = (y * image.width + x) * 4
      const alpha = image.data[i + 3] / 255
      // 투명한 부분은 흰 종이 위에 놓은 것처럼 섞는다
      r += image.data[i] * alpha + 255 * (1 - alpha)
      g += image.data[i + 1] * alpha + 255 * (1 - alpha)
      b += image.data[i + 2] * alpha + 255 * (1 - alpha)
      count += 1
    }
  }
  return [r / count, g / count, b / count]
}

const hex = (value: number) => Math.max(0, Math.min(255, Math.round(value))).toString(16).padStart(2, '0')
const toHex = (color: Rgb) => `#${hex(color[0])}${hex(color[1])}${hex(color[2])}`.toUpperCase()

/** 중앙값 분할(median cut)로 colors를 최대 count개 대표색으로 줄인다. 정렬 기준에 인덱스를 넣어 결과가 늘 같다. */
function quantize(colors: Rgb[], count: number): { palette: Rgb[]; assignment: number[] } {
  let boxes: number[][] = [colors.map((_, index) => index)]
  const rangeOf = (box: number[]) => {
    let best = { channel: 0, range: 0 }
    for (let channel = 0; channel < 3; channel += 1) {
      let min = 255
      let max = 0
      for (const index of box) {
        min = Math.min(min, colors[index][channel])
        max = Math.max(max, colors[index][channel])
      }
      if (max - min > best.range) best = { channel, range: max - min }
    }
    return best
  }
  while (boxes.length < count) {
    let pick = -1
    let pickScore = 0
    boxes.forEach((box, boxIndex) => {
      if (box.length < 2) return
      const score = rangeOf(box).range * Math.sqrt(box.length)
      if (score > pickScore) {
        pick = boxIndex
        pickScore = score
      }
    })
    if (pick === -1) break
    const box = boxes[pick]
    const { channel } = rangeOf(box)
    const sorted = [...box].sort((a, b) => colors[a][channel] - colors[b][channel] || a - b)
    const half = Math.floor(sorted.length / 2)
    boxes = [...boxes.slice(0, pick), sorted.slice(0, half), sorted.slice(half), ...boxes.slice(pick + 1)]
  }
  const palette: Rgb[] = boxes.map((box) => {
    const sum: Rgb = [0, 0, 0]
    for (const index of box) for (let channel = 0; channel < 3; channel += 1) sum[channel] += colors[index][channel]
    return [sum[0] / box.length, sum[1] / box.length, sum[2] / box.length]
  })
  const assignment = colors.map((color) => {
    let bestIndex = 0
    let bestDistance = Infinity
    palette.forEach((entry, index) => {
      const distance = (entry[0] - color[0]) ** 2 + (entry[1] - color[1]) ** 2 + (entry[2] - color[2]) ** 2
      if (distance < bestDistance) {
        bestIndex = index
        bestDistance = distance
      }
    })
    return bestIndex
  })
  return { palette, assignment }
}

/**
 * 자른 영역을 칸 수가 정확히 goalDays인 격자로 바꾼다.
 * 1) chooseGridSize로 W×H(≥N)를 고르고 칸마다 블록 평균 색을 구한다.
 * 2) 남는 칸은 중심에서 먼 칸(모서리)부터 뺀다. 3) 색은 최대 colorCount개로 줄인다.
 */
export function photoToDotGrid(image: SourceImage, crop: CropRect, goalDays: number, colorCount: number): PhotoGrid {
  const { width, height } = chooseGridSize(goalDays, crop.w / crop.h)
  const all: { x: number; y: number; color: Rgb }[] = []
  for (let y = 0; y < height; y += 1) {
    for (let x = 0; x < width; x += 1) {
      const x0 = crop.x + (x * crop.w) / width
      const x1 = crop.x + ((x + 1) * crop.w) / width
      const y0 = crop.y + (y * crop.h) / height
      const y1 = crop.y + ((y + 1) * crop.h) / height
      all.push({ x, y, color: blockAverage(image, x0, y0, x1, y1) })
    }
  }
  const excess = all.length - goalDays
  const centerX = (width - 1) / 2
  const centerY = (height - 1) / 2
  const removed = new Set(
    [...all]
      .sort(
        (a, b) =>
          (b.x - centerX) ** 2 + (b.y - centerY) ** 2 - ((a.x - centerX) ** 2 + (a.y - centerY) ** 2) ||
          b.y - a.y ||
          b.x - a.x,
      )
      .slice(0, excess)
      .map((cell) => cell.y * width + cell.x),
  )
  const kept = all.filter((cell) => !removed.has(cell.y * width + cell.x))
  const { palette, assignment } = quantize(
    kept.map((cell) => cell.color),
    Math.max(1, Math.min(MAX_COLORS, colorCount)),
  )
  const used = [...new Set(assignment)].sort((a, b) => a - b)
  const remap = new Map(used.map((oldIndex, newIndex) => [oldIndex, newIndex]))
  return {
    width,
    height,
    palette: used.map((oldIndex) => toHex(palette[oldIndex])),
    cells: kept.map((cell, index) => ({ x: cell.x, y: cell.y, c: remap.get(assignment[index])! })),
  }
}
