import { describe, it, expect } from 'vitest'
import { priceSummary } from './priceSummary'

const candle = (time: string, open: number, close: number) => ({ time, open, high: close, low: open, close })

describe('priceSummary', () => {
  it('캔들이 없으면 null 이다', () => {
    expect(priceSummary([])).toBeNull()
  })

  it('등락은 그날 시가가 아니라 전일 종가 대비다', () => {
    const summary = priceSummary([candle('2026-10-07', 98, 100), candle('2026-10-08', 105, 102)])

    expect(summary?.last.time).toBe('2026-10-08')
    expect(summary?.change).toBeCloseTo(2)
    expect(summary?.changePercent).toBeCloseTo(2)
  })

  it('캔들이 하나면 등락이 없다', () => {
    const summary = priceSummary([candle('2026-10-08', 105, 102)])

    expect(summary?.last.close).toBe(102)
    expect(summary?.change).toBeNull()
    expect(summary?.changePercent).toBeNull()
  })
})
