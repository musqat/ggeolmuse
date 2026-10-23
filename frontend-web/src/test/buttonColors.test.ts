// @vitest-environment node
import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { sourceFiles, srcPath } from './sourceFiles'

const SOLID_COLOR = /bg-(red|green|blue|purple)-\d{3}/
const PALETTE = 'styles/buttons.ts'

// 버튼 색 모음 밖에서 바탕 기본 색을 쓴 파일:줄
const solidColorsOutsidePalette = (): string[] =>
  sourceFiles(/\.tsx?$/)
    .filter((file) => srcPath(file) !== PALETTE)
    .flatMap((file) =>
      readFileSync(file, 'utf8')
        .split('\n')
        .flatMap((line, i) => (SOLID_COLOR.test(line) ? [`${srcPath(file)}:${i + 1}`] : []))
    )

describe('버튼 색', () => {
  it('버튼 바탕 기본 색은 버튼 색 모음에만 있다', () => {
    expect(solidColorsOutsidePalette()).toEqual([])
  })
})
