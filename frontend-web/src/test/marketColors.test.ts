// @vitest-environment node
import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { sourceFiles, srcPath } from './sourceFiles'

// 쓰지 않는 등락 · 매수 토큰 클래스(등락 · 손익은 gain · loss 하나로)
const UNUSED_TOKEN = /\b(?:text|bg|border|ring|fill|stroke)-(?:up|down|buy)\b|var\(--(?:up|down|buy)\)/

// 그 토큰을 쓰는 파일:줄
const unusedTokenUsages = (): string[] =>
  sourceFiles(/\.(tsx?|css)$/).flatMap((file) =>
    readFileSync(file, 'utf8')
      .split('\n')
      .flatMap((line, i) => (UNUSED_TOKEN.test(line) ? [`${srcPath(file)}:${i + 1}`] : []))
  )

describe('등락 색', () => {
  it('등락 · 손익 색은 gain · loss 토큰만 쓴다', () => {
    expect(unusedTokenUsages()).toEqual([])
  })
})
