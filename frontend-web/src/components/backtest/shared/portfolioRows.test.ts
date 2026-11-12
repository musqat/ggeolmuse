import { describe, it, expect } from 'vitest'
import { amountShares, isWeightSumOk, rowAmounts, rowSymbol, rowsSubject, savedRows, weightSum, type PortfolioRow } from './portfolioRows'

const row = (symbol: string, amount = '', weight = ''): PortfolioRow => ({ symbol, amount, weight })

describe('포트폴리오 종목 줄', () => {
  it('종목은 앞뒤 공백을 빼고 대문자로 본다', () => {
    expect(rowSymbol(row(' spy '))).toBe('SPY')
  })

  it('줄 금액: 금액 방식은 칸 값, 비율 방식은 총액 × 비율 ÷ 100 을 원 단위로, 못 읽는 칸은 0', () => {
    const rows = [row('SPY', '6000000', '33.335'), row('QQQ', '', '66.665'), row('IAU', 'abc', '')]

    expect(rowAmounts(rows, 'AMOUNT', '')).toEqual([6000000, 0, 0])
    expect(rowAmounts(rows, 'WEIGHT', '1000000')).toEqual([333350, 666650, 0])
    expect(rowAmounts(rows, 'WEIGHT', '')).toEqual([0, 0, 0])
  })

  it('금액 몫: 줄 금액 ÷ 합 × 100, 합이 0 이면 모두 0', () => {
    expect(amountShares([row('SPY', '6000000'), row('QQQ', '3000000'), row('IAU', '1000000')])).toEqual([60, 30, 10])
    expect(amountShares([row('SPY', ''), row('QQQ', '0')])).toEqual([0, 0])
  })

  it('비율 합은 넣은 값 그대로 더하고, 100 에서 0.01 까지는 맞는 합으로 본다', () => {
    expect(weightSum([row('SPY', '', '33.33'), row('QQQ', '', '33.33'), row('IAU', '', '33.33')])).toBe(99.99)
    expect(isWeightSumOk([row('SPY', '', '33.33'), row('QQQ', '', '33.33'), row('IAU', '', '33.33')])).toBe(true)
    expect(isWeightSumOk([row('SPY', '', '33.335'), row('QQQ', '', '33.335'), row('IAU', '', '33.335')])).toBe(true)
    expect(isWeightSumOk([row('SPY', '', '50.0149'), row('QQQ', '', '50.0049')])).toBe(false)
    expect(isWeightSumOk([row('SPY', '', '60'), row('QQQ', '', '39.98')])).toBe(false)
    expect(isWeightSumOk([row('SPY', '', '60'), row('QQQ', '', '')])).toBe(false)
    expect(isWeightSumOk([row('SPY', '', '100')])).toBe(true)
  })

  it('직전 설정의 줄은 글 칸 셋인 것만 10개까지 되살린다', () => {
    const saved = [
      row('SPY', '1', '10'),
      { symbol: 'QQQ', amount: 1, weight: '' },
      'IAU',
      null,
      ...Array.from({ length: 12 }, (_, i) => row(`S${i}`, '1', '1')),
    ]

    const rows = savedRows(saved)
    expect(rows).toHaveLength(10)
    expect(rows[0]).toEqual(row('SPY', '1', '10'))
    expect(rows[1]).toEqual(row('S0', '1', '1'))
  })

  it('요약 종목은 빈 줄을 빼고 셋까지, 나머지는 외 N, 하나도 없으면 종목', () => {
    expect(rowsSubject([row('SPY'), row(''), row('QQQ')])).toBe('SPY, QQQ')
    expect(rowsSubject([row('SPY'), row('QQQ'), row('IAU'), row('TLT'), row('GLD')])).toBe('SPY, QQQ, IAU 외 2')
    expect(rowsSubject([row(' ')])).toBe('종목')
  })
})
