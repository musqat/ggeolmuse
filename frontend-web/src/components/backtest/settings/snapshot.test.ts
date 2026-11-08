import { describe, it, expect, vi, afterEach } from 'vitest'
import { LAST_SETTINGS_KEY, oneOf, parseSnapshot, pickValues, readLastSettings, saveLastSettings, type SettingsSnapshot } from './snapshot'

const snapshot: SettingsSnapshot = {
  v: 1,
  mode: 'dca',
  symbol: 'MSFT',
  period: { start: '2025-10-08', end: '', agoCount: '1', agoUnit: 'YEAR', agoOn: true },
  options: { dividendTax: false },
  modes: { dca: { amount: '200000' } },
}

afterEach(() => {
  localStorage.clear()
  vi.restoreAllMocks()
})

describe('저장한 설정 읽기', () => {
  it('기본값에 있는 칸만, 같은 모양일 때만 덮어쓰고 안쪽 칸도 같은 규칙', () => {
    const defaults = { amount: '100000', on: false, pick: { date: '', start: '' }, list: ['AAPL'] }

    expect(pickValues(defaults, { amount: '5', on: 'yes', pick: { date: '2026-01-05' }, list: ['MSFT'], extra: 1 })).toEqual({
      amount: '5',
      on: false,
      pick: { date: '2026-01-05', start: '' },
      list: ['MSFT'],
    })
    expect(pickValues(defaults, null)).toEqual(defaults)
    expect(pickValues(defaults, { list: 'MSFT' }).list).toEqual(['AAPL'])
  })

  it('고를 수 있는 값이 아니면 기본값', () => {
    expect(oneOf('WEEKLY', ['DAILY', 'WEEKLY'] as const, 'DAILY')).toBe('WEEKLY')
    expect(oneOf('HOURLY', ['DAILY', 'WEEKLY'] as const, 'DAILY')).toBe('DAILY')
    expect(oneOf(3, ['DAILY', 'WEEKLY'] as const, 'DAILY')).toBe('DAILY')
  })

  it('저장 버전이 다르거나 JSON 이 아니면 쓸 수 없음', () => {
    expect(parseSnapshot(JSON.stringify(snapshot))).toEqual(snapshot)
    expect(parseSnapshot(JSON.stringify({ ...snapshot, v: 2 }))).toBeNull()
    expect(parseSnapshot('{')).toBeNull()
    expect(parseSnapshot(null)).toBeNull()
    expect(parseSnapshot(JSON.stringify({ v: 1 }))).toEqual({ v: 1, mode: '', symbol: '', period: undefined, options: undefined, modes: {} })
  })

  it('직전 설정은 브라우저 저장소 한 칸에 두고, 저장소가 막히면 저장 · 읽기를 건너뜀', () => {
    saveLastSettings(snapshot)
    expect(localStorage.getItem(LAST_SETTINGS_KEY)).toBe(JSON.stringify(snapshot))
    expect(readLastSettings()).toBe(JSON.stringify(snapshot))

    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    expect(readLastSettings()).toBeNull()
    expect(() => saveLastSettings(snapshot)).not.toThrow()
  })
})
