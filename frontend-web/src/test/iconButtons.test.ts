// @vitest-environment node
import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import ts from 'typescript'
import { sourceFiles, srcPath } from './sourceFiles'

const ICON = /<[A-Z][A-Za-z0-9]*\b[^>]*\/>/g
const NAME_ATTRS = ['aria-label', 'aria-labelledby', 'title']

// 아이콘 요소 하나 또는 아이콘끼리 고르는 조건식
const isIconChild = (child: ts.JsxChild) => {
  if (ts.isJsxSelfClosingElement(child)) return /^[A-Z]/.test(child.tagName.getText())
  if (!ts.isJsxExpression(child) || !child.expression) return false
  const text = child.getText()
  const rest = text.replace(ICON, '')
  return /<[A-Z]/.test(text) && /^[\s{}()?:&|!.\w]*$/.test(rest) && !/['"`]/.test(rest)
}

// 아이콘만 있고 이름이 없는 button 의 파일:줄
const unnamedIconButtons = (): string[] =>
  sourceFiles(/\.tsx$/).flatMap((file) => {
    const source = ts.createSourceFile(file, readFileSync(file, 'utf8'), ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
    const found: string[] = []
    const visit = (node: ts.Node) => {
      if (ts.isJsxElement(node) && node.openingElement.tagName.getText() === 'button') {
        const named = node.openingElement.attributes.properties.some(
          (attr) => ts.isJsxAttribute(attr) && NAME_ATTRS.includes(attr.name.getText())
        )
        const children = node.children.filter((child) => !(ts.isJsxText(child) && child.getText().trim() === ''))
        if (!named && children.length > 0 && children.every(isIconChild)) {
          const line = source.getLineAndCharacterOfPosition(node.getStart()).line + 1
          found.push(`${srcPath(file)}:${line}`)
        }
      }
      ts.forEachChild(node, visit)
    }
    visit(source)
    return found
  })

describe('아이콘 버튼', () => {
  it('아이콘만 있는 버튼은 모두 이름이 있다', () => {
    expect(unnamedIconButtons()).toEqual([])
  })
})
