import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, it, expect } from 'vitest'
import { SRC } from './sourceFiles'

const html = readFileSync(resolve(SRC, '../index.html'), 'utf8')

describe('index.html', () => {
  it('문서 언어가 한국어이고 검색 결과에 보일 설명이 있다', () => {
    expect(html).toMatch(/<html lang="ko">/)
    expect(html).toMatch(/<meta name="description" content="[^"]{10,}" \/>/)
  })
})
