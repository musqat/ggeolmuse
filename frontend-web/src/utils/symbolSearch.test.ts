import { describe, it, expect } from 'vitest'
import { searchSymbols } from './symbolSearch'

describe('searchSymbols', () => {
  it('정확히 같은 티커 · 티커 앞부분 · 알파벳 순으로 세운다', () => {
    expect(searchSymbols(['AVGO', 'VZ', 'V'], 'v', 10).map((s) => s.symbol)).toEqual(['V', 'VZ', 'AVGO'])
  })

  it('회사 이름으로도 찾는다', () => {
    expect(searchSymbols(['AAPL', 'MSFT'], 'micro', 10)).toEqual([{ symbol: 'MSFT', name: 'Microsoft Corporation' }])
  })

  it('빈 검색어면 앞에서 limit 개', () => {
    expect(searchSymbols(['AAPL', 'MSFT', 'NVDA'], '  ', 2).map((s) => s.symbol)).toEqual(['AAPL', 'MSFT'])
  })
})
