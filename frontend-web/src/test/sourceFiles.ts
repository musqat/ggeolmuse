import { readdirSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

export const SRC = resolve(dirname(fileURLToPath(import.meta.url)), '..')

// src 아래 이름이 맞는 파일(테스트 제외)
export const sourceFiles = (name: RegExp, dir = SRC): string[] =>
  readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) return sourceFiles(name, path)
    return name.test(entry.name) && !entry.name.includes('.test.') ? [path] : []
  })

// src 기준 경로(슬래시)
export const srcPath = (file: string) => relative(SRC, file).replace(/\\/g, '/')
