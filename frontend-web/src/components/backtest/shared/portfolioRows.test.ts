import { describe, it, expect } from 'vitest'
import {
  amountShares,
  chosenStrategy,
  hasDcaRow,
  isAllDca,
  isWeightSumOk,
  moveSaleOrder,
  newRow,
  removeRow,
  rowAmounts,
  rowStrategy,
  rowSymbol,
  rowsSellText,
  rowsSubject,
  saleOrder,
  saleRanks,
  savedRows,
  weightSum,
  type PortfolioRow,
  type RowStrategy,
} from './portfolioRows'

const SIGNAL_DEFAULTS = { strategy: '' as RowStrategy, shortWindow: '20', longWindow: '60', bandWindow: '200', upper: '2.5', lower: '2.5' }

const row = (symbol: string, amount = '', weight = '', takeProfit = '', stopLoss = ''): PortfolioRow => ({
  symbol,
  amount,
  weight,
  takeProfit,
  stopLoss,
  ...SIGNAL_DEFAULTS,
  taxSaleOrder: 1,
})

const withStrategy = (strategy: RowStrategy, symbol = 'SPY'): PortfolioRow => ({ ...row(symbol, '1'), strategy })

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

  it('새 줄은 익절 · 손절이 비고 전략은 방식대로, 이동평균 · 밴드 칸은 20 · 60일 선 · 200일선 ±2.5%', () => {
    expect(newRow()).toEqual({
      symbol: '',
      amount: '',
      weight: '',
      takeProfit: '',
      stopLoss: '',
      strategy: '',
      shortWindow: '20',
      longWindow: '60',
      bandWindow: '200',
      upper: '2.5',
      lower: '2.5',
      taxSaleOrder: 1,
    })
    expect(newRow('SPY', '10000000', '100')).toEqual(row('SPY', '10000000', '100'))
    expect(newRow('', '', '', 3).taxSaleOrder).toBe(3)
  })

  describe('판매 차례', () => {
    const ordered = (orders: number[]) => orders.map((order, i) => ({ ...row(`S${i}`), taxSaleOrder: order }))

    it('먼저 팔 줄부터 줄 번호를 내고, 차례가 같으면 줄 차례대로', () => {
      expect(saleOrder(ordered([2, 3, 1]))).toEqual([2, 0, 1])
      expect(saleOrder(ordered([2, 2, 1]))).toEqual([2, 0, 1])
    })

    it('줄마다 판매 차례는 1 ~ 줄 수, 차례가 같으면 앞 줄이 먼저', () => {
      expect(saleRanks(ordered([3, 1, 2]))).toEqual([3, 1, 2])
      expect(saleRanks(ordered([2, 2, 1]))).toEqual([2, 3, 1])
    })

    it('차례 하나를 위 · 아래 차례와 바꾸고 모든 줄 차례를 1부터 다시 매기며, 끝을 넘으면 그대로 둔다', () => {
      expect(moveSaleOrder(ordered([1, 2, 3]), 1, -1).map((r) => r.taxSaleOrder)).toEqual([2, 1, 3])
      expect(moveSaleOrder(ordered([3, 1, 2]), 0, 1).map((r) => r.taxSaleOrder)).toEqual([3, 2, 1])
      expect(moveSaleOrder(ordered([2, 2, 1]), 0, -1).map((r) => r.taxSaleOrder)).toEqual([2, 3, 1])
      expect(moveSaleOrder(ordered([1, 2]), 1, 1).map((r) => r.taxSaleOrder)).toEqual([1, 2])
    })

    it('줄을 지우면 남은 줄 차례를 앞뒤 그대로 1부터 다시 매긴다', () => {
      expect(removeRow(ordered([3, 1, 2]), 2).map((r) => r.taxSaleOrder)).toEqual([2, 1])
      expect(removeRow(ordered([1, 2, 3]), 0).map((r) => r.taxSaleOrder)).toEqual([1, 2])
      expect(removeRow(ordered([2, 2, 1]), 2).map((r) => r.taxSaleOrder)).toEqual([1, 2])
    })
  })

  it('줄 전략은 고른 전략, 방식대로인 줄은 방식(한 번에 · 적립식)', () => {
    expect(rowStrategy(withStrategy(''), 'LUMP_SUM')).toBe('LUMP_SUM')
    expect(rowStrategy(withStrategy(''), 'DCA')).toBe('DCA')
    expect(rowStrategy(withStrategy('DCA'), 'LUMP_SUM')).toBe('DCA')
    expect(rowStrategy(withStrategy('LUMP_SUM'), 'DCA')).toBe('LUMP_SUM')
    expect(rowStrategy(withStrategy('MA_CROSS'), 'DCA')).toBe('MA_CROSS')
    expect(rowStrategy(withStrategy('MA_BAND'), 'LUMP_SUM')).toBe('MA_BAND')
  })

  it('방식과 같은 전략을 고르면 방식대로(빈 값), 다른 전략은 그 전략으로 둔다', () => {
    expect(chosenStrategy('LUMP_SUM', 'LUMP_SUM')).toBe('')
    expect(chosenStrategy('DCA', 'DCA')).toBe('')
    expect(chosenStrategy('DCA', 'LUMP_SUM')).toBe('DCA')
    expect(chosenStrategy('LUMP_SUM', 'DCA')).toBe('LUMP_SUM')
    expect(chosenStrategy('MA_CROSS', 'LUMP_SUM')).toBe('MA_CROSS')
    expect(chosenStrategy('MA_BAND', 'DCA')).toBe('MA_BAND')
  })

  it('적립식 줄이 하나라도 있는지 · 모든 줄이 적립식인지는 방식대로인 줄을 방식으로 본다', () => {
    const rows = [withStrategy(''), withStrategy('MA_CROSS', 'QQQ')]

    expect(hasDcaRow(rows, 'LUMP_SUM')).toBe(false)
    expect(hasDcaRow(rows, 'DCA')).toBe(true)
    expect(hasDcaRow([withStrategy('DCA'), withStrategy('', 'QQQ')], 'LUMP_SUM')).toBe(true)
    expect(isAllDca([withStrategy(''), withStrategy('', 'QQQ')], 'DCA')).toBe(true)
    expect(isAllDca([withStrategy('DCA'), withStrategy('', 'QQQ')], 'DCA')).toBe(true)
    expect(isAllDca(rows, 'DCA')).toBe(false)
    expect(isAllDca([withStrategy('DCA')], 'LUMP_SUM')).toBe(true)
    expect(isAllDca([withStrategy('LUMP_SUM')], 'DCA')).toBe(false)
  })

  it('직전 설정의 줄은 종목 · 금액 · 비율 글 칸 셋인 것만 10개까지 되살리고, 익절 · 손절은 글이 아니면 빈 칸으로', () => {
    const saved = [
      row('SPY', '1', '10', '30', '10'),
      { symbol: 'QQQ', amount: 1, weight: '' },
      'IAU',
      null,
      { symbol: 'TLT', amount: '2', weight: '', takeProfit: 5 },
      ...Array.from({ length: 12 }, (_, i) => row(`S${i}`, '1', '1')),
    ]

    const rows = savedRows(saved)
    expect(rows).toHaveLength(10)
    expect(rows[0]).toEqual(row('SPY', '1', '10', '30', '10'))
    expect(rows[1]).toEqual({ ...row('TLT', '2', ''), taxSaleOrder: 2 })
    expect(rows[2]).toEqual(row('S0', '1', '1'))
  })

  it('직전 설정의 줄 판매 차례는 정수면 그대로, 아니면 줄 차례', () => {
    const saved = [
      { ...row('SPY', '1'), taxSaleOrder: 2 },
      { symbol: 'QQQ', amount: '1', weight: '', taxSaleOrder: '1' },
      { symbol: 'IAU', amount: '1', weight: '', taxSaleOrder: 1.5 },
    ]

    expect(savedRows(saved).map((r) => r.taxSaleOrder)).toEqual([2, 2, 3])
  })

  it('직전 설정의 줄 전략은 고를 수 있는 값만(아니면 방식대로), 이동평균 · 밴드 칸은 글이 아니면 기본값으로', () => {
    const saved = [
      { ...row('SPY', '1'), strategy: 'MA_CROSS', shortWindow: '5', longWindow: '30' },
      { ...row('QQQ', '1'), strategy: 'MA_BAND', bandWindow: '120', upper: '1', lower: '0' },
      { ...row('IAU', '1'), strategy: 'CONDITIONAL', shortWindow: 5 },
      { symbol: 'TLT', amount: '1', weight: '', strategy: 'DCA' },
    ]

    expect(savedRows(saved)).toEqual([
      { ...row('SPY', '1'), strategy: 'MA_CROSS', shortWindow: '5', longWindow: '30' },
      { ...row('QQQ', '1'), strategy: 'MA_BAND', bandWindow: '120', upper: '1', lower: '0' },
      row('IAU', '1'),
      { ...row('TLT', '1'), strategy: 'DCA', taxSaleOrder: 4 },
    ])
  })

  it('요약 익절 · 손절 글은 적은 줄 수와 적은 쪽만, 하나도 없으면 빈 글', () => {
    expect(rowsSellText([row('SPY'), row('QQQ')])).toBe('')
    expect(rowsSellText([row('SPY', '', '', ' ', ' ')])).toBe('')
    expect(rowsSellText([row('SPY', '', '', '30', '10'), row('QQQ'), row('IAU', '', '', '', '5')])).toBe(
      '2개 종목에 익절 · 손절(평균 단가 기준 전량)'
    )
    expect(rowsSellText([row('SPY', '', '', '30'), row('QQQ', '', '', '20')])).toBe('2개 종목에 익절(평균 단가 기준 전량)')
    expect(rowsSellText([row('SPY', '', '', '', '10')])).toBe('1개 종목에 손절(평균 단가 기준 전량)')
  })

  it('요약 종목은 빈 줄을 빼고 셋까지, 나머지는 외 N, 하나도 없으면 종목', () => {
    expect(rowsSubject([row('SPY'), row(''), row('QQQ')])).toBe('SPY, QQQ')
    expect(rowsSubject([row('SPY'), row('QQQ'), row('IAU'), row('TLT'), row('GLD')])).toBe('SPY, QQQ, IAU 외 2')
    expect(rowsSubject([row(' ')])).toBe('종목')
  })
})
