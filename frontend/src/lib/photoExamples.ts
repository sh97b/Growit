/*
 * 사진 도트 도움말에 보여 줄 예시 사진을 코드로 직접 그린다(저작권 걱정이 없고 외부 파일도 필요 없다).
 * 같은 입력이면 늘 같은 그림이다. 도트로 바꾸는 것은 앱이 실제로 쓰는 변환 함수(photoDots.ts)가 한다.
 */
import type { SourceImage } from './photoDots.ts'

const SIZE = 160

function blank(): SourceImage {
  return { width: SIZE, height: SIZE, data: new Uint8ClampedArray(SIZE * SIZE * 4) }
}

function put(image: SourceImage, x: number, y: number, r: number, g: number, b: number) {
  const i = (y * image.width + x) * 4
  image.data[i] = r
  image.data[i + 1] = g
  image.data[i + 2] = b
  image.data[i + 3] = 255
}

/** 이런 사진이 잘 돼요: 단색 배경 위에 단순한 물체 하나(빨간 사과, 색은 배경·빨강·초록 세 가지). */
export function goodExamplePhoto(): SourceImage {
  const image = blank()
  for (let y = 0; y < SIZE; y += 1) {
    for (let x = 0; x < SIZE; x += 1) {
      const inApple = (x - 80) ** 2 + (y - 92) ** 2 <= 50 ** 2
      const inStem = x >= 77 && x <= 83 && y >= 30 && y <= 52
      const inLeaf = (x - 95) ** 2 / 14 ** 2 + (y - 44) ** 2 / 7 ** 2 <= 1
      if (inApple) put(image, x, y, 205, 50, 45)
      else if (inStem || inLeaf) put(image, x, y, 70, 130, 60)
      else put(image, x, y, 214, 232, 247)
    }
  }
  return image
}

/** 이런 사진은 어려워요: 잡음 같은 복잡한 무늬 위에 작은 물체가 여러 개 흩어져 있다. */
export function hardExamplePhoto(): SourceImage {
  const image = blank()
  const dots = [
    [24, 30, 9, [230, 190, 40]], [120, 24, 7, [40, 120, 200]], [60, 70, 8, [200, 60, 140]],
    [132, 76, 9, [60, 170, 90]], [30, 118, 7, [240, 120, 50]], [86, 128, 9, [120, 70, 190]],
    [140, 132, 6, [30, 30, 30]], [96, 40, 6, [250, 250, 250]], [50, 148, 6, [200, 200, 60]],
  ] as const
  for (let y = 0; y < SIZE; y += 1) {
    for (let x = 0; x < SIZE; x += 1) {
      const dot = dots.find(([cx, cy, radius]) => (x - cx) ** 2 + (y - cy) ** 2 <= radius ** 2)
      if (dot) {
        put(image, x, y, dot[3][0], dot[3][1], dot[3][2])
      } else {
        put(image, x, y, (x * 37 + y * 91 + ((x * y) % 53) * 5) % 256, (x * 53 + y * 29 + ((x + y) % 41) * 7) % 256, (x * 17 + y * 71 + ((x * 3 + y) % 37) * 9) % 256)
      }
    }
  }
  return image
}
