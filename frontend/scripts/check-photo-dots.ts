// 사진 → 도트 변환 함수가 칸 수를 정확히 맞추는지 확인한다. 실행: npm run check:photo
import assert from 'node:assert/strict'
import { cropRectFor, MAX_GRID_SIZE, photoToDotGrid, type SourceImage } from '../src/lib/photoDots.ts'

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

const ratios: [string, number][] = [['1:1', 1], ['4:3', 4 / 3], ['3:4', 3 / 4]]
const goals = [14, 30, 100, 200]
const image = gradientImage(480, 360)
let checked = 0

for (const [label, aspect] of ratios) {
  for (const goalDays of goals) {
    for (const colorCount of [4, 6, 8]) {
      const crop = cropRectFor(image.width, image.height, aspect, 1, 0, 0)
      const grid = photoToDotGrid(image, crop, goalDays, colorCount)
      const name = `${label} / ${goalDays}칸 / ${colorCount}색`
      assert.equal(grid.cells.length, goalDays, `${name}: 칸 수`)
      assert.ok(grid.width >= 1 && grid.width <= MAX_GRID_SIZE && grid.height >= 1 && grid.height <= MAX_GRID_SIZE, `${name}: 크기`)
      assert.ok(grid.palette.length >= 1 && grid.palette.length <= colorCount, `${name}: 색 수 ${grid.palette.length}`)
      assert.ok(grid.palette.every((color) => /^#[0-9A-F]{6}$/.test(color)), `${name}: 색 형식`)
      const seen = new Set<string>()
      for (const cell of grid.cells) {
        assert.ok(cell.x >= 0 && cell.x < grid.width && cell.y >= 0 && cell.y < grid.height, `${name}: 좌표 범위`)
        assert.ok(cell.c >= 0 && cell.c < grid.palette.length, `${name}: 색 번호`)
        assert.ok(!seen.has(`${cell.x},${cell.y}`), `${name}: 좌표 중복`)
        seen.add(`${cell.x},${cell.y}`)
      }
      assert.deepEqual(photoToDotGrid(image, crop, goalDays, colorCount), grid, `${name}: 같은 입력이면 같은 결과`)
      checked += 1
    }
  }
}

// 확대·위치를 바꿔 자른 경우와 한 가지 색뿐인 사진, 아주 작은 사진도 칸 수가 맞아야 한다
for (const goalDays of [14, 200]) {
  const zoomed = cropRectFor(image.width, image.height, 1, 2.5, 1, -1)
  assert.equal(photoToDotGrid(image, zoomed, goalDays, 6).cells.length, goalDays)
  const flat: SourceImage = { width: 20, height: 20, data: new Uint8ClampedArray(20 * 20 * 4).fill(200) }
  const flatGrid = photoToDotGrid(flat, cropRectFor(20, 20, 1, 1, 0, 0), goalDays, 6)
  assert.equal(flatGrid.cells.length, goalDays)
  assert.equal(flatGrid.palette.length, 1)
  const tiny = gradientImage(5, 3)
  assert.equal(photoToDotGrid(tiny, cropRectFor(5, 3, 4 / 3, 1, 0, 0), goalDays, 6).cells.length, goalDays)
  checked += 3
}

console.log(`사진 → 도트 변환 확인 통과 (${checked}가지 조합)`)
