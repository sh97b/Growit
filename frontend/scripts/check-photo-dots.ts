// 사진 → 도트 변환 함수 확인. 실행: npm run check:photo
import assert from 'node:assert/strict'
import {
  colorCountFor,
  cropRectFor,
  MAX_GRID_SIZE,
  photoToDotGrid,
  type PhotoGrid,
  type SourceImage,
} from '../src/lib/photoDots.ts'

function gradientImage(width: number, height: number): SourceImage {
  const data = new Uint8ClampedArray(width * height * 4)
  for (let y = 0; y < height; y += 1) {
    for (let x = 0; x < width; x += 1) {
      const i = (y * width + x) * 4
      data[i] = Math.round((x / width) * 255)
      data[i + 1] = Math.round((y / height) * 255)
      data[i + 2] = (x * 7 + y * 13) % 256
      data[i + 3] = x % 11 === 0 ? 128 : 255 // 반투명 픽셀도 섞는다
    }
  }
  return { width, height, data }
}

/** 단색 배경 위에 색이 변하는 타원 피사체. insideAt은 한 점이 피사체 안인지 알려 준다. */
function subjectImage(width: number, height: number, background: [number, number, number]) {
  const data = new Uint8ClampedArray(width * height * 4)
  const cx = width / 2
  const cy = height / 2
  const rx = width * 0.3
  const ry = height * 0.36
  const insideAt = (x: number, y: number) => ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1
  for (let y = 0; y < height; y += 1) {
    for (let x = 0; x < width; x += 1) {
      const i = (y * width + x) * 4
      const inside = insideAt(x + 0.5, y + 0.5)
      data[i] = inside ? 40 + Math.round((x / width) * 180) : background[0]
      data[i + 1] = inside ? 60 + Math.round((y / height) * 120) : background[1]
      data[i + 2] = inside ? 150 - Math.round((x / width) * 90) : background[2]
      data[i + 3] = 255
    }
  }
  return { image: { width, height, data } as SourceImage, insideAt }
}

function assertValidGrid(grid: PhotoGrid, goalDays: number, name: string) {
  assert.equal(grid.cells.length, goalDays, `${name}: 칸 수`)
  assert.ok(grid.width >= 1 && grid.width <= MAX_GRID_SIZE && grid.height >= 1 && grid.height <= MAX_GRID_SIZE, `${name}: 크기`)
  assert.ok(grid.palette.length >= 1 && grid.palette.length <= colorCountFor(goalDays), `${name}: 색 수 ${grid.palette.length}`)
  assert.ok(grid.palette.every((color) => /^#[0-9A-F]{6}$/.test(color)), `${name}: 색 형식`)
  const seen = new Set<string>()
  for (const cell of grid.cells) {
    assert.ok(cell.x >= 0 && cell.x < grid.width && cell.y >= 0 && cell.y < grid.height, `${name}: 좌표 범위`)
    assert.ok(cell.c >= 0 && cell.c < grid.palette.length, `${name}: 색 번호`)
    assert.ok(!seen.has(`${cell.x},${cell.y}`), `${name}: 좌표 중복`)
    seen.add(`${cell.x},${cell.y}`)
  }
}

const ratios: [string, number][] = [['1:1', 1], ['4:3', 4 / 3], ['3:4', 3 / 4]]
const goals = [14, 30, 100, 200]
const image = gradientImage(480, 360)
let checked = 0

// 1) 기존 조합: 비율 × 칸 수 × 배경 빼기 켜기/끄기 — 항상 정확히 N칸이고 결정적이어야 한다
for (const [label, aspect] of ratios) {
  for (const goalDays of goals) {
    for (const removeBackground of [true, false]) {
      const crop = cropRectFor(image.width, image.height, aspect, 1, 0, 0)
      const grid = photoToDotGrid(image, crop, goalDays, { removeBackground })
      const name = `${label} / ${goalDays}칸 / 배경 ${removeBackground ? '빼기' : '유지'}`
      assertValidGrid(grid, goalDays, name)
      assert.deepEqual(photoToDotGrid(image, crop, goalDays, { removeBackground }), grid, `${name}: 같은 입력이면 같은 결과`)
      checked += 1
    }
  }
}

// 2) 단색 배경 위 피사체: 정확히 N칸, 피사체 칸의 90% 이상 유지, 배경 칸은 대부분 그리지 않음, 결정적
for (const background of [[240, 240, 240], [20, 30, 60]] as [number, number, number][]) {
  for (const [label, aspect] of ratios) {
    const { image: photo, insideAt } = subjectImage(360, 360, background)
    for (const goalDays of [30, 100, 200]) {
      const crop = cropRectFor(photo.width, photo.height, aspect, 1, 0, 0)
      const grid = photoToDotGrid(photo, crop, goalDays, { removeBackground: true })
      const name = `피사체 ${background.join(',')} / ${label} / ${goalDays}칸`
      assertValidGrid(grid, goalDays, name)
      assert.deepEqual(photoToDotGrid(photo, crop, goalDays, { removeBackground: true }), grid, `${name}: 결정적`)
      // 격자 칸마다 중심이 피사체 안인지로 정답을 만든다(칸 가운데 5점 중 4점 이상 안쪽이면 "피사체 비율이 높은 칸")
      const kept = new Set(grid.cells.map((cell) => `${cell.x},${cell.y}`))
      let strong = 0
      let strongKept = 0
      let keptBackground = 0
      for (let y = 0; y < grid.height; y += 1) {
        for (let x = 0; x < grid.width; x += 1) {
          const px0 = crop.x + (x * crop.w) / grid.width
          const py0 = crop.y + (y * crop.h) / grid.height
          const cw = crop.w / grid.width
          const ch = crop.h / grid.height
          const points: [number, number][] = [[0.5, 0.5], [0.25, 0.25], [0.75, 0.25], [0.25, 0.75], [0.75, 0.75]]
          const inside = points.filter(([u, v]) => insideAt(px0 + u * cw, py0 + v * ch)).length
          if (inside >= 4) {
            strong += 1
            if (kept.has(`${x},${y}`)) strongKept += 1
          }
          if (inside === 0 && kept.has(`${x},${y}`)) keptBackground += 1
        }
      }
      assert.ok(strong > 0, `${name}: 정답 피사체 칸이 있어야 함`)
      assert.ok(strongKept / strong >= 0.9, `${name}: 피사체 칸 유지 ${strongKept}/${strong}`)
      assert.ok(keptBackground <= goalDays * 0.1, `${name}: 배경 칸이 너무 많음 ${keptBackground}`)
      // 배경 빼기를 끄면 같은 사진도 전체를 칠한다(칸 수는 같다)
      assertValidGrid(photoToDotGrid(photo, crop, goalDays, { removeBackground: false }), goalDays, `${name} / 배경 유지`)
      checked += 2
    }
  }
}

// 3) 배경 분리가 안 되는 사진: 한 가지 색, 거의 전체가 피사체, 아주 작은 사진, 확대·이동 — 모두 정확히 N칸
const flat: SourceImage = { width: 40, height: 40, data: new Uint8ClampedArray(40 * 40 * 4).fill(200) }
const allDark: SourceImage = (() => {
  const size = 120
  const data = new Uint8ClampedArray(size * size * 4).fill(255)
  for (let y = 4; y < size - 4; y += 1) {
    for (let x = 4; x < size - 4; x += 1) {
      const i = (y * size + x) * 4
      data[i] = 30 + (x % 40)
      data[i + 1] = 20 + (y % 50)
      data[i + 2] = 40
    }
  }
  return { width: size, height: size, data }
})()
const noisy: SourceImage = (() => {
  const size = 90
  const data = new Uint8ClampedArray(size * size * 4)
  for (let i = 0; i < size * size; i += 1) {
    data[i * 4] = (i * 73) % 256
    data[i * 4 + 1] = (i * 151) % 256
    data[i * 4 + 2] = (i * 37) % 256
    data[i * 4 + 3] = 255
  }
  return { width: size, height: size, data }
})()
const tiny = gradientImage(5, 3)
for (const goalDays of [14, 30, 100, 200]) {
  for (const [name, photo] of [['단색', flat], ['거의 전체가 피사체', allDark], ['잡음', noisy], ['아주 작은 사진', tiny]] as [string, SourceImage][]) {
    for (const [label, aspect] of ratios) {
      const crop = cropRectFor(photo.width, photo.height, aspect, 1, 0, 0)
      const grid = photoToDotGrid(photo, crop, goalDays, { removeBackground: true })
      assertValidGrid(grid, goalDays, `${name} / ${label} / ${goalDays}칸`)
      assert.deepEqual(photoToDotGrid(photo, crop, goalDays, { removeBackground: true }), grid, `${name}: 결정적`)
      checked += 1
    }
  }
  const zoomed = cropRectFor(image.width, image.height, 1, 2.5, 1, -1)
  assertValidGrid(photoToDotGrid(image, zoomed, goalDays, { removeBackground: true }), goalDays, `확대·이동 / ${goalDays}칸`)
  checked += 1
}
assert.equal(photoToDotGrid(flat, cropRectFor(40, 40, 1, 1, 0, 0), 30, { removeBackground: true }).palette.length, 1, '단색 사진은 색 하나')

// 4) 색 수는 칸 수로 자동 결정
assert.deepEqual([14, 30, 31, 100, 101, 200].map(colorCountFor), [4, 4, 5, 5, 6, 6])

console.log(`사진 → 도트 변환 확인 통과 (${checked}가지 조합)`)
