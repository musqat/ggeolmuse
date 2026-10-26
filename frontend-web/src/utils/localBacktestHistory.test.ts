import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { getLocalBacktestHistory, saveLocalBacktestHistory } from './localBacktestHistory'

const KEY = 'ggeolmuse_backtest_history'

beforeEach(() => localStorage.clear())
afterEach(() => vi.restoreAllMocks())

describe('로컬 백테스트 기록', () => {
  it('저장한 것이 없으면 빈 목록이다', () => {
    expect(getLocalBacktestHistory()).toEqual([])
  })

  it('새 기록을 맨 앞에 두고 요청 값은 JSON 문자열로 저장한다', () => {
    saveLocalBacktestHistory('STRATEGY_SIMULATION', { symbol: 'AAPL' }, 'auto')
    saveLocalBacktestHistory('INVESTMENT_ANALYSIS', { symbol: 'MSFT' }, 'manual')

    const [latest, first] = getLocalBacktestHistory()

    expect(latest).toMatchObject({ userId: 'local', backtestType: 'INVESTMENT_ANALYSIS', fxRateMode: 'manual' })
    expect(JSON.parse(latest.requestParams)).toEqual({ symbol: 'MSFT' })
    expect(latest.backtestId).toMatch(/^local_\d+_/)
    expect(first).toMatchObject({ backtestType: 'STRATEGY_SIMULATION', fxRateMode: 'auto' })
  })

  it('50개를 넘으면 오래된 것부터 버린다', () => {
    for (let i = 0; i < 52; i++) saveLocalBacktestHistory('STRATEGY_SIMULATION', { i }, 'auto')

    const history = getLocalBacktestHistory()

    expect(history).toHaveLength(50)
    expect(JSON.parse(history[0].requestParams)).toEqual({ i: 51 })
    expect(JSON.parse(history[49].requestParams)).toEqual({ i: 2 })
  })

  it('저장된 값이 깨졌으면 빈 목록이다', () => {
    localStorage.setItem(KEY, '{깨짐')

    expect(getLocalBacktestHistory()).toEqual([])
  })

  it('저장소에 쓰지 못해도 오류를 내지 않는다', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('가득 참')
    })

    expect(() => saveLocalBacktestHistory('STRATEGY_SIMULATION', {}, 'auto')).not.toThrow()
  })
})
