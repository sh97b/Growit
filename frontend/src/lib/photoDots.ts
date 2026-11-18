/*
 * 사진을 도트 그림 격자로 바꾸는 순수 함수들(AI 없음). 브라우저에서만 쓰고 서버에는 결과 격자만 보낸다.
 * 같은 입력이면 항상 같은 결과가 나오도록 무작위를 쓰지 않고, 동점은 늘 y, x 순으로 가른다.
 *
 * 흐름: 가장자리 색을 배경으로 보고 피사체를 가른다 → 피사체 칸 수가 N에 가장 가까운 격자 크기를 찾는다 →
 * 경계 칸을 더하거나 빼서 정확히 N칸 → 피사체 색만으로 칸 색을 정하고 K색으로 줄인다 → 경계 칸은 한 단계 어둡게.
 * 배경을 못 가르면(피사체 5% 미만·85% 초과) 경계가 뚜렷한 칸을 남기고, 그것도 어려우면 모서리부터 덜어 낸다.
 */

export const MAX_GRID_SIZE = 40

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

export type PhotoOptions = {
  /** 배경을 빼고 피사체 모양만 그린다. 끄면 사진 전체를 격자로 바꾼다. */
  removeBackground: boolean
}

/** 칸 수에 따라 색 수를 자동으로 정한다(적은 칸에는 적은 색이 더 또렷하다). */
export function colorCountFor(goalDays: number): number {
  if (goalDays <= 30) return 4
  if (goalDays <= 100) return 5
  return 6
}

type Rgb = [number, number, number]
type Lab = [number, number, number]

const RASTER_LONG_SIDE = 96
const SUBJECT_MIN_RATIO = 0.05
const SUBJECT_MAX_RATIO = 0.85
const KMEANS_ROUNDS = 12

/**
 * 자른 비율(가로/세로)에 맞는 격자 크기를 고른다. W×H가 칸 수 N 이상이면서 N에 가장 가깝고(초과분이 적고),
 * 비율이 자른 사진과 비슷한 것을 고른다.
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

const hex2 = (value: number) => Math.max(0, Math.min(255, Math.round(value))).toString(16).padStart(2, '0')
const toHex = (color: Rgb) => `#${hex2(color[0])}${hex2(color[1])}${hex2(color[2])}`.toUpperCase()
const luminance = (color: Rgb) => 0.2126 * color[0] + 0.7152 * color[1] + 0.0722 * color[2]

function srgbToLab(color: Rgb): Lab {
  const lin = (value: number) => {
    const v = value / 255
    return v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4
  }
  const r = lin(color[0])
  const g = lin(color[1])
  const b = lin(color[2])
  const x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047
  const y = 0.2126729 * r + 0.7151522 * g + 0.072175 * b
  const z = (0.0193339 * r + 0.119192 * g + 0.9503041 * b) / 1.08883
  const f = (t: number) => (t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16 / 116)
  return [116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z))]
}

const labDistance = (a: Lab, b: Lab) => Math.hypot(a[0] - b[0], a[1] - b[1], a[2] - b[2])

/** 분석용으로 자른 영역을 긴 변 96칸 안팎의 작은 격자(픽셀)로 줄인 것. */
type Raster = { w: number; h: number; rgb: Rgb[]; lab: Lab[] }

function buildRaster(image: SourceImage, crop: CropRect): Raster {
  const scale = Math.min(1, RASTER_LONG_SIDE / Math.max(crop.w, crop.h))
  const w = Math.max(1, Math.round(crop.w * scale))
  const h = Math.max(1, Math.round(crop.h * scale))
  const rgb: Rgb[] = []
  for (let j = 0; j < h; j += 1) {
    for (let i = 0; i < w; i += 1) {
      rgb.push(
        blockAverage(
          image,
          crop.x + (i * crop.w) / w,
          crop.y + (j * crop.h) / h,
          crop.x + ((i + 1) * crop.w) / w,
          crop.y + ((j + 1) * crop.h) / h,
        ),
      )
    }
  }
  return { w, h, rgb, lab: rgb.map(srgbToLab) }
}

const medianOf = (values: number[]) => [...values].sort((a, b) => a - b)[Math.floor(values.length / 2)]

/** 가장자리 한 줄(링)의 색 중앙값을 배경색으로 본다. */
function backgroundLab(raster: Raster): Lab {
  const ring = Math.max(1, Math.round(0.05 * Math.min(raster.w, raster.h)))
  const ls: number[] = []
  const as: number[] = []
  const bs: number[] = []
  for (let j = 0; j < raster.h; j += 1) {
    for (let i = 0; i < raster.w; i += 1) {
      if (i < ring || i >= raster.w - ring || j < ring || j >= raster.h - ring) {
        const lab = raster.lab[j * raster.w + i]
        ls.push(lab[0])
        as.push(lab[1])
        bs.push(lab[2])
      }
    }
  }
  return [medianOf(ls), medianOf(as), medianOf(bs)]
}

/** 배경과의 색 거리 분포에서 Otsu 방법으로 피사체/배경 경계값을 구한다. 거의 단색이면 null. */
function otsuThreshold(distances: number[]): number | null {
  const maxDistance = Math.max(...distances)
  if (maxDistance < 3) return null
  const bins = 64
  const width = maxDistance / bins
  const hist = new Array<number>(bins).fill(0)
  for (const distance of distances) hist[Math.min(bins - 1, Math.floor(distance / width))] += 1
  const total = distances.length
  let sumAll = 0
  for (let i = 0; i < bins; i += 1) sumAll += i * hist[i]
  let weightBackground = 0
  let sumBackground = 0
  let bestVariance = -1
  let bestBin = 1
  for (let t = 1; t < bins; t += 1) {
    weightBackground += hist[t - 1]
    sumBackground += (t - 1) * hist[t - 1]
    const weightSubject = total - weightBackground
    if (weightBackground === 0 || weightSubject === 0) continue
    const meanBackground = sumBackground / weightBackground
    const meanSubject = (sumAll - sumBackground) / weightSubject
    const variance = weightBackground * weightSubject * (meanBackground - meanSubject) ** 2
    if (variance > bestVariance + 1e-9) {
      bestVariance = variance
      bestBin = t
    }
  }
  return Math.max(6, bestBin * width)
}

/** 픽셀마다 이웃과의 색 차이(경계 세기). */
function edgeStrength(raster: Raster): number[] {
  const at = (i: number, j: number) =>
    raster.lab[Math.max(0, Math.min(raster.h - 1, j)) * raster.w + Math.max(0, Math.min(raster.w - 1, i))]
  const edge: number[] = []
  for (let j = 0; j < raster.h; j += 1) {
    for (let i = 0; i < raster.w; i += 1) {
      edge.push(Math.hypot(labDistance(at(i + 1, j), at(i - 1, j)) / 2, labDistance(at(i, j + 1), at(i, j - 1)) / 2))
    }
  }
  return edge
}

type GridStats = { width: number; height: number; ratio: number[]; edge: number[]; ranges: [number, number, number, number][] }

/** 격자 칸마다 분석 픽셀 영역을 정하고, 그 안의 피사체 비율(mask가 있을 때)과 평균 경계 세기를 구한다. */
function gridStats(raster: Raster, mask: boolean[] | null, edge: number[], width: number, height: number): GridStats {
  const ratio: number[] = []
  const edgeMean: number[] = []
  const ranges: [number, number, number, number][] = []
  for (let y = 0; y < height; y += 1) {
    for (let x = 0; x < width; x += 1) {
      const i0 = Math.min(raster.w - 1, Math.floor((x * raster.w) / width))
      const i1 = Math.min(raster.w, Math.max(i0 + 1, Math.ceil(((x + 1) * raster.w) / width)))
      const j0 = Math.min(raster.h - 1, Math.floor((y * raster.h) / height))
      const j1 = Math.min(raster.h, Math.max(j0 + 1, Math.ceil(((y + 1) * raster.h) / height)))
      let subject = 0
      let edgeSum = 0
      let count = 0
      for (let j = j0; j < j1; j += 1) {
        for (let i = i0; i < i1; i += 1) {
          const index = j * raster.w + i
          if (mask && mask[index]) subject += 1
          edgeSum += edge[index]
          count += 1
        }
      }
      ratio.push(mask ? subject / count : 0)
      edgeMean.push(edgeSum / count)
      ranges.push([i0, i1, j0, j1])
    }
  }
  return { width, height, ratio, edge: edgeMean, ranges }
}

/** 긴 변에 놓을 칸 수(scale)와 자른 비율로 격자 크기를 정한다. */
function gridSizeForScale(scale: number, aspect: number): { width: number; height: number } {
  if (aspect >= 1) return { width: scale, height: Math.max(1, Math.min(MAX_GRID_SIZE, Math.round(scale / aspect))) }
  return { width: Math.max(1, Math.min(MAX_GRID_SIZE, Math.round(scale * aspect))), height: scale }
}

const countSubject = (stats: GridStats) => stats.ratio.filter((value) => value >= 0.5).length

function neighbors4(x: number, y: number): [number, number][] {
  return [[x - 1, y], [x + 1, y], [x, y - 1], [x, y + 1]]
}

/**
 * 피사체 칸 수가 N에 가장 가까운 격자 크기를 찾고(이분 탐색), 모자라면 경계 중요도가 높은 칸을 더하고 남으면 낮은 칸을
 * 빼서 정확히 N칸으로 맞춘다. 반환값은 고른 칸(y*width+x 표시)과 그 격자.
 */
function selectSubjectCells(
  raster: Raster,
  mask: boolean[],
  edge: number[],
  goalDays: number,
  aspect: number,
): { stats: GridStats; selected: boolean[] } {
  const statsFor = (scale: number) => {
    const size = gridSizeForScale(scale, aspect)
    return gridStats(raster, mask, edge, size.width, size.height)
  }
  let low = 1
  let high = MAX_GRID_SIZE
  while (low < high) {
    const mid = Math.floor((low + high) / 2)
    if (countSubject(statsFor(mid)) >= goalDays) high = mid
    else low = mid + 1
  }
  let scale = low
  if (low > 1 && Math.abs(countSubject(statsFor(low - 1)) - goalDays) < Math.abs(countSubject(statsFor(low)) - goalDays)) {
    scale = low - 1
  }
  while (scale < MAX_GRID_SIZE) {
    const size = gridSizeForScale(scale, aspect)
    if (size.width * size.height >= goalDays) break
    scale += 1
  }
  const stats = statsFor(scale)
  const { width, height } = stats
  const maxEdge = Math.max(...stats.edge, 1e-9)
  const importance = stats.ratio.map((ratio, index) => ratio + (0.5 * stats.edge[index]) / maxEdge)
  const selected = stats.ratio.map((ratio) => ratio >= 0.5)
  let count = selected.filter(Boolean).length
  const isSelected = (x: number, y: number) => x >= 0 && y >= 0 && x < width && y < height && selected[y * width + x]

  while (count < goalDays) {
    let best = -1
    let bestAdjacent = false
    for (let y = 0; y < height; y += 1) {
      for (let x = 0; x < width; x += 1) {
        const index = y * width + x
        if (selected[index]) continue
        const adjacent = [-1, 0, 1].some((dy) => [-1, 0, 1].some((dx) => (dx !== 0 || dy !== 0) && isSelected(x + dx, y + dy)))
        if (best === -1 || (adjacent && !bestAdjacent) || (adjacent === bestAdjacent && importance[index] > importance[best] + 1e-12)) {
          best = index
          bestAdjacent = adjacent
        }
      }
    }
    selected[best] = true
    count += 1
  }
  while (count > goalDays) {
    let best = -1
    for (let y = 0; y < height; y += 1) {
      for (let x = 0; x < width; x += 1) {
        const index = y * width + x
        if (!selected[index]) continue
        if (!neighbors4(x, y).some(([nx, ny]) => !isSelected(nx, ny))) continue
        if (best === -1 || importance[index] < importance[best] - 1e-12) best = index
      }
    }
    selected[best] = false
    count -= 1
  }
  return { stats, selected }
}

/** 밝기 2~98% 구간을 0~255로 늘려 대비를 자동으로 높인다(밝기 차이가 너무 작으면 그대로 둔다). */
function stretchContrast(colors: Rgb[]): Rgb[] {
  if (colors.length === 0) return colors
  const sorted = colors.map(luminance).sort((a, b) => a - b)
  const low = sorted[Math.floor(0.02 * (sorted.length - 1))]
  const high = sorted[Math.ceil(0.98 * (sorted.length - 1))]
  if (high - low < 16) return colors
  const map = (value: number) => Math.max(0, Math.min(255, ((value - low) / (high - low)) * 255))
  return colors.map((color) => [map(color[0]), map(color[1]), map(color[2])])
}

/** 시작값을 밝기 순 분위수로 고정한 k-평균. 같은 입력이면 늘 같은 팔레트가 나온다. */
function kMeans(colors: Rgb[], k: number): { palette: Rgb[]; assignment: number[] } {
  const count = Math.max(1, Math.min(k, colors.length))
  const order = colors.map((_, index) => index).sort((a, b) => luminance(colors[a]) - luminance(colors[b]) || a - b)
  const centers: Rgb[] = Array.from({ length: count }, (_, i) => [...colors[order[Math.floor(((i + 0.5) * colors.length) / count)]]] as Rgb)
  const assign = () =>
    colors.map((color) => {
      let bestIndex = 0
      let bestDistance = Infinity
      centers.forEach((center, index) => {
        const distance = (center[0] - color[0]) ** 2 + (center[1] - color[1]) ** 2 + (center[2] - color[2]) ** 2
        if (distance < bestDistance) {
          bestIndex = index
          bestDistance = distance
        }
      })
      return bestIndex
    })
  let assignment = assign()
  for (let round = 0; round < KMEANS_ROUNDS; round += 1) {
    const sums: number[][] = centers.map(() => [0, 0, 0, 0])
    colors.forEach((color, index) => {
      const sum = sums[assignment[index]]
      sum[0] += color[0]
      sum[1] += color[1]
      sum[2] += color[2]
      sum[3] += 1
    })
    sums.forEach((sum, index) => {
      if (sum[3] > 0) centers[index] = [sum[0] / sum[3], sum[1] / sum[3], sum[2] / sum[3]]
    })
    assignment = assign()
  }
  return { palette: centers, assignment }
}

type Picked = { x: number; y: number; color: Rgb; outline: boolean }

/** 고른 칸들의 색을 대비 보정하고 K색으로 줄여 최종 격자로 만든다. 윤곽 칸은 팔레트 안에서 한 단계 어두운 색을 쓴다. */
function finish(width: number, height: number, picked: Picked[], goalDays: number): PhotoGrid {
  // 색을 정수로 먼저 맞춘다: 같은 색인데 평균 계산 순서 때문에 소수점 아래만 다른 칸들이 서로 다른 색으로 갈리지 않게
  const stretched = stretchContrast(picked.map((cell) => cell.color.map((value) => Math.round(value)) as Rgb))
  const { palette, assignment } = kMeans(stretched, colorCountFor(goalDays))
  const byDarkness = palette.map((_, index) => index).sort((a, b) => luminance(palette[a]) - luminance(palette[b]) || a - b)
  const darker = new Map<number, number>()
  byDarkness.forEach((paletteIndex, rank) => darker.set(paletteIndex, rank > 0 ? byDarkness[rank - 1] : paletteIndex))
  const finalIndex = picked.map((cell, index) => (cell.outline ? darker.get(assignment[index])! : assignment[index]))
  const used = [...new Set(finalIndex)].sort((a, b) => a - b)
  const remap = new Map(used.map((oldIndex, newIndex) => [oldIndex, newIndex]))
  return {
    width,
    height,
    palette: used.map((oldIndex) => toHex(palette[oldIndex])),
    cells: picked.map((cell, index) => ({ x: cell.x, y: cell.y, c: remap.get(finalIndex[index])! })),
  }
}

const cellColorFromImage = (image: SourceImage, crop: CropRect, width: number, height: number, x: number, y: number): Rgb =>
  blockAverage(
    image,
    crop.x + (x * crop.w) / width,
    crop.y + (y * crop.h) / height,
    crop.x + ((x + 1) * crop.w) / width,
    crop.y + ((y + 1) * crop.h) / height,
  )

/** 사진 전체를 격자로 바꾸고 남는 칸은 중심에서 먼 칸(모서리)부터 뺀다(배경 빼기를 끈 경우와 마지막 대비책). */
function legacyGrid(image: SourceImage, crop: CropRect, goalDays: number): PhotoGrid {
  const { width, height } = chooseGridSize(goalDays, crop.w / crop.h)
  const all: Picked[] = []
  for (let y = 0; y < height; y += 1) {
    for (let x = 0; x < width; x += 1) all.push({ x, y, color: cellColorFromImage(image, crop, width, height, x, y), outline: false })
  }
  const centerX = (width - 1) / 2
  const centerY = (height - 1) / 2
  const removed = new Set(
    [...all]
      .sort(
        (a, b) =>
          (b.x - centerX) ** 2 + (b.y - centerY) ** 2 - ((a.x - centerX) ** 2 + (a.y - centerY) ** 2) || b.y - a.y || b.x - a.x,
      )
      .slice(0, all.length - goalDays)
      .map((cell) => cell.y * width + cell.x),
  )
  return finish(width, height, all.filter((cell) => !removed.has(cell.y * width + cell.x)), goalDays)
}

/** 배경을 못 가를 때: 전체 이미지에서 경계 세기가 높은 칸 N개를 남긴다. 경계가 거의 없으면 null. */
function edgeGrid(image: SourceImage, crop: CropRect, raster: Raster, edge: number[], goalDays: number): PhotoGrid | null {
  const { width, height } = chooseGridSize(goalDays, crop.w / crop.h)
  const stats = gridStats(raster, null, edge, width, height)
  const maxEdge = Math.max(...stats.edge)
  if (maxEdge < 1) return null
  const strong = stats.edge.filter((value) => value >= 0.25 * maxEdge).length
  if (strong < goalDays / 2) return null
  const order = stats.edge
    .map((value, index) => ({ value, index }))
    .sort((a, b) => b.value - a.value || Math.floor(a.index / width) - Math.floor(b.index / width) || (a.index % width) - (b.index % width))
    .slice(0, goalDays)
  const picked: Picked[] = order
    .sort((a, b) => a.index - b.index)
    .map(({ index }) => {
      const x = index % width
      const y = Math.floor(index / width)
      return { x, y, color: cellColorFromImage(image, crop, width, height, x, y), outline: false }
    })
  return finish(width, height, picked, goalDays)
}

/**
 * 자른 영역을 칸 수가 정확히 goalDays인 격자로 바꾼다(W,H ≤ 40).
 * 배경 빼기가 켜져 있으면 피사체 모양만 남기고, 배경을 못 가르면 경계 칸 → 모서리 제거 순서로 대신한다.
 */
export function photoToDotGrid(image: SourceImage, crop: CropRect, goalDays: number, options: PhotoOptions): PhotoGrid {
  if (!options.removeBackground) return legacyGrid(image, crop, goalDays)

  const raster = buildRaster(image, crop)
  const edge = edgeStrength(raster)
  const background = backgroundLab(raster)
  const distances = raster.lab.map((lab) => labDistance(lab, background))
  const threshold = otsuThreshold(distances)
  const mask = threshold === null ? null : distances.map((distance) => distance > threshold)
  const subjectRatio = mask ? mask.filter(Boolean).length / mask.length : 0

  if (!mask || subjectRatio < SUBJECT_MIN_RATIO || subjectRatio > SUBJECT_MAX_RATIO) {
    return edgeGrid(image, crop, raster, edge, goalDays) ?? legacyGrid(image, crop, goalDays)
  }

  const { stats, selected } = selectSubjectCells(raster, mask, edge, goalDays, crop.w / crop.h)
  const { width, height } = stats
  const isSelected = (x: number, y: number) => x >= 0 && y >= 0 && x < width && y < height && selected[y * width + x]
  const picked: Picked[] = []
  for (let y = 0; y < height; y += 1) {
    for (let x = 0; x < width; x += 1) {
      const index = y * width + x
      if (!selected[index]) continue
      const [i0, i1, j0, j1] = stats.ranges[index]
      // 칸 색은 피사체 픽셀만의 평균. 그런 픽셀이 없는 칸(경계 보충으로 더한 칸)은 영역 전체 평균을 쓴다
      let sum: Rgb = [0, 0, 0]
      let count = 0
      let sumAll: Rgb = [0, 0, 0]
      let countAll = 0
      for (let j = j0; j < j1; j += 1) {
        for (let i = i0; i < i1; i += 1) {
          const pixel = raster.rgb[j * raster.w + i]
          sumAll = [sumAll[0] + pixel[0], sumAll[1] + pixel[1], sumAll[2] + pixel[2]]
          countAll += 1
          if (mask[j * raster.w + i]) {
            sum = [sum[0] + pixel[0], sum[1] + pixel[1], sum[2] + pixel[2]]
            count += 1
          }
        }
      }
      const color: Rgb = count > 0 ? [sum[0] / count, sum[1] / count, sum[2] / count] : [sumAll[0] / countAll, sumAll[1] / countAll, sumAll[2] / countAll]
      picked.push({ x, y, color, outline: neighbors4(x, y).some(([nx, ny]) => !isSelected(nx, ny)) })
    }
  }
  return finish(width, height, picked, goalDays)
}
