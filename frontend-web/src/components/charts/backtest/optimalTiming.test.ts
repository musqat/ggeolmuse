import { describe, it, expect } from 'vitest'
import { calculateOptimalPoints } from './optimalTiming'

/** 차트 한 행을 만든다. 평가액은 주가에 비례한다고 본다. */
function row(date: string, price: number, shares = 3000) {
  return { date, AAPL_price: price, AAPL_portfolio: price * shares }
}

const AAPL = [{ symbol: 'AAPL' }]

describe('calculateOptimalPoints', () => {
  it('매도일이 매수일보다 앞서는 답을 내지 않는다', () => {
    // 고점이 먼저 오고 그 뒤로 내려가는 구간.
    // 전 구간 최저(8/12)와 최고(7/28)를 각각 뽑으면 8월에 사서 7월에 파는 답이 나온다.
    const data = [
      row('2026-07-02', 308.36),
      row('2026-07-28', 339.79),
      row('2026-08-12', 302.25),
      row('2026-08-26', 312.84),
    ]

    const { AAPL: point } = calculateOptimalPoints(AAPL, data)

    expect(point.buyDate).toBe('2026-07-02')
    expect(point.sellDate).toBe('2026-07-28')
    expect(point.sellDate >= point.buyDate).toBe(true)
    expect(point.minPrice).toBe(308.36)
  })

  it('저점이 먼저 오면 저점 매수 고점 매도를 그대로 고른다', () => {
    const data = [
      row('2025-01-02', 248.62),
      row('2025-04-08', 171.37),
      row('2026-07-28', 339.79),
    ]

    const { AAPL: point } = calculateOptimalPoints(AAPL, data)

    expect(point.buyDate).toBe('2025-04-08')
    expect(point.sellDate).toBe('2026-07-28')
  })

  it('내내 하락하면 매수일과 매도일이 같다', () => {
    // 살 이유가 없는 구간이다. 손실을 최적이라고 부르지 않는다.
    const data = [
      row('2026-01-02', 300),
      row('2026-02-02', 250),
      row('2026-03-02', 200),
    ]

    const { AAPL: point } = calculateOptimalPoints(AAPL, data)

    expect(point.buyDate).toBe('2026-01-02')
    expect(point.sellDate).toBe('2026-01-02')
  })

  it('종목마다 따로 계산한다', () => {
    const data = [
      { date: '2026-01-02', A_price: 100, A_portfolio: 100, B_price: 50, B_portfolio: 50 },
      { date: '2026-02-02', A_price: 80, A_portfolio: 80, B_price: 90, B_portfolio: 90 },
      { date: '2026-03-02', A_price: 120, A_portfolio: 120, B_price: 60, B_portfolio: 60 },
    ]

    const result = calculateOptimalPoints([{ symbol: 'A' }, { symbol: 'B' }], data)

    // A 는 2월 저점 -> 3월 고점
    expect(result.A.buyDate).toBe('2026-02-02')
    expect(result.A.sellDate).toBe('2026-03-02')
    // B 는 1월 저점 -> 2월 고점
    expect(result.B.buyDate).toBe('2026-01-02')
    expect(result.B.sellDate).toBe('2026-02-02')
  })

  it('가격이 없는 날은 건너뛴다', () => {
    // 종목마다 상장일이 달라 앞쪽 날짜에 값이 비는 경우가 있다.
    const data = [
      { date: '2026-01-02', AAPL_price: 0, AAPL_portfolio: 0 },
      row('2026-02-02', 200),
      row('2026-03-02', 260),
    ]

    const { AAPL: point } = calculateOptimalPoints(AAPL, data)

    expect(point.buyDate).toBe('2026-02-02')
    expect(point.sellDate).toBe('2026-03-02')
  })

  it('데이터가 없으면 빈 값을 준다', () => {
    const { AAPL: point } = calculateOptimalPoints(AAPL, [])

    expect(point.buyDate).toBe('')
    expect(point.sellDate).toBe('')
  })
})
