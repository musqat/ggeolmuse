import { describe, it, expect } from 'vitest'
import { isSeparateSignal, signalName, signalSymbolOf } from './signalSymbol'

describe('신호 종목', () => {
  it('요청에는 고른 지수나 다른 종목(대문자)을 싣고, 같게 · 빈 칸 · 매매 종목과 같은 종목은 싣지 않는다', () => {
    expect(signalSymbolOf('^GSPC', '', 'SPY')).toBe('^GSPC')
    expect(signalSymbolOf('^NDX', 'MSFT', 'QQQ')).toBe('^NDX')
    expect(signalSymbolOf('other', ' msft ', 'AAPL')).toBe('MSFT')
    expect(signalSymbolOf('same', 'MSFT', 'AAPL')).toBeUndefined()
    expect(signalSymbolOf('other', ' ', 'AAPL')).toBeUndefined()
    expect(signalSymbolOf('other', 'aapl', 'AAPL')).toBeUndefined()
  })

  it('이름은 지수면 이름(기호), 종목이면 기호 그대로', () => {
    expect(signalName('^GSPC')).toBe('S&P 500(^GSPC)')
    expect(signalName('^NDX')).toBe('나스닥 100(^NDX)')
    expect(signalName('MSFT')).toBe('MSFT')
  })

  it('결과의 신호 종목이 사고판 종목과 다를 때만 참이고 대소문자는 가리지 않는다', () => {
    expect(isSeparateSignal('^GSPC', 'SPY')).toBe(true)
    expect(isSeparateSignal('spy', 'SPY')).toBe(false)
    expect(isSeparateSignal(undefined, 'SPY')).toBe(false)
    expect(isSeparateSignal('^GSPC', undefined)).toBe(false)
  })
})
