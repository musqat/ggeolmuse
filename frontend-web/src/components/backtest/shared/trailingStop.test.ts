import { describe, it, expect } from 'vitest'
import { hasTrailingStop, toTrailingStop } from './trailingStop'

describe('toTrailingStop', () => {
  it('하락을 비우면 칸을 싣지 않고, 팔 비율을 비우면 하락 비율만 싣는다', () => {
    expect(toTrailingStop('', '')).toStrictEqual({ fields: {} })
    expect(toTrailingStop(' 10 ', '')).toStrictEqual({ fields: { trailingStopRate: 0.1 } })
  })

  it('두 퍼센트를 소수 비율로 바꾸고 나눗셈 끝자리 오차를 버린다', () => {
    expect(toTrailingStop('7.3', '33.3')).toStrictEqual({ fields: { trailingStopRate: 0.073, trailingSellRate: 0.333 } })
    expect(toTrailingStop('99.9', '100')).toStrictEqual({ fields: { trailingStopRate: 0.999, trailingSellRate: 1 } })
  })

  it.each([
    [{ stop: '0', sell: '' }, '고점 대비 하락 비율은 0보다 크고 100보다 작아야 합니다.'],
    [{ stop: '100', sell: '' }, '고점 대비 하락 비율은 0보다 크고 100보다 작아야 합니다.'],
    [{ stop: '.', sell: '' }, '고점 대비 하락 비율은 0보다 크고 100보다 작아야 합니다.'],
    [{ stop: '99.999999999', sell: '' }, '고점 대비 하락 비율은 0보다 크고 100보다 작아야 합니다.'],
    [{ stop: '0.0000000001', sell: '' }, '고점 대비 하락 비율은 0보다 크고 100보다 작아야 합니다.'],
    [{ stop: '10', sell: '0' }, '고점 대비 하락 때 팔 비율은 0보다 크고 100 이하여야 합니다.'],
    [{ stop: '10', sell: '0.0000000001' }, '고점 대비 하락 때 팔 비율은 0보다 크고 100 이하여야 합니다.'],
    [{ stop: '10', sell: '100.5' }, '고점 대비 하락 때 팔 비율은 0보다 크고 100 이하여야 합니다.'],
    [{ stop: '', sell: '50' }, '고점 대비 하락 때 팔 비율은 하락 비율과 함께 입력해주세요.'],
  ])('%o 는 막는다', ({ stop, sell }, error) => {
    expect(toTrailingStop(stop, sell)).toStrictEqual({ error })
  })
})

describe('hasTrailingStop', () => {
  it('하락 칸을 적었을 때만 켠 것으로 본다', () => {
    expect(hasTrailingStop('10')).toBe(true)
    expect(hasTrailingStop(' ')).toBe(false)
    expect(hasTrailingStop(undefined)).toBe(false)
  })
})
