// @vitest-environment node
import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { SRC, sourceFiles, srcPath } from './sourceFiles'

// 쓰지 않는 등락 · 매수 토큰 클래스(등락 · 손익은 gain · loss 하나로)
const UNUSED_TOKEN = /\b(?:text|bg|border|ring|fill|stroke)-(?:up|down|buy)\b|var\(--(?:up|down|buy)\)/

// 그 토큰을 쓰는 파일:줄
const unusedTokenUsages = (): string[] =>
  sourceFiles(/\.(tsx?|css)$/).flatMap((file) =>
    readFileSync(file, 'utf8')
      .split('\n')
      .flatMap((line, i) => (UNUSED_TOKEN.test(line) ? [`${srcPath(file)}:${i + 1}`] : []))
  )

// index.css 의 테마마다 정한 토큰 RGB
const tokenRgbs = (name: string): number[][] =>
  [...readFileSync(join(SRC, 'index.css'), 'utf8').matchAll(new RegExp(`--${name}:\\s*(\\d+)\\s+(\\d+)\\s+(\\d+);`, 'g'))]
    .map((m) => m.slice(1, 4).map(Number))

describe('등락 색', () => {
  it('등락 · 손익 색은 gain · loss 토큰만 쓴다', () => {
    expect(unusedTokenUsages()).toEqual([])
  })

  it('두 테마 모두 오름 · 수익은 빨강, 내림 · 손실은 파랑이다', () => {
    const gains = tokenRgbs('gain')
    const losses = tokenRgbs('loss')

    expect(gains).toHaveLength(2)
    expect(losses).toHaveLength(2)
    gains.forEach(([r, g, b]) => expect(r).toBeGreaterThan(Math.max(g, b)))
    losses.forEach(([r, g, b]) => expect(b).toBeGreaterThan(Math.max(r, g)))
  })
})
