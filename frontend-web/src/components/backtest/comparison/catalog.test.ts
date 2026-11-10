import { describe, it, expect } from 'vitest'
import { COMPARISON_STRATEGIES, STRATEGY_NAMES, strategyByType } from './catalog'

const common = { startDate: '2023-01-01', endDate: '2026-10-08', investment: '1000000' }
const year2023 = { startDate: '2023-01-01', endDate: '2023-12-31', investment: '1000000' }

describe('비교 전략 카탈로그', () => {
  it('버튼 순서와 이름', () => {
    expect(COMPARISON_STRATEGIES.map((s) => s.type)).toEqual(['SIMPLE', 'DCA', 'CONDITIONAL_PURCHASE', 'MA_CROSS'])
    expect(STRATEGY_NAMES).toEqual({ SIMPLE: '단순 매수', DCA: '적립식', CONDITIONAL_PURCHASE: '조건부 매수', MA_CROSS: '이동평균' })
  })

  it('고를 때 채우는 기본값', () => {
    expect(strategyByType('SIMPLE').defaultParams).toEqual({})
    expect(strategyByType('DCA').defaultParams).toEqual({
      amountPerPurchase: '100000',
      cadence: 'MONTHLY',
      months: '3',
      firstPurchaseDate: '',
    })
    expect(strategyByType('CONDITIONAL_PURCHASE').defaultParams).toEqual({
      amountPerPurchase: '100000',
      maxPurchases: '',
      dropPercentage: '5',
      direction: 'DROP',
    })
    expect(strategyByType('MA_CROSS').defaultParams).toEqual({ shortWindow: '20', longWindow: '60' })
  })

  it('이동평균은 두 기간을 숫자로 싣고 원금은 싣지 않는다', () => {
    expect(strategyByType('MA_CROSS').toRequest({}, common)).toEqual({
      strategyType: 'MA_CROSS',
      name: 'MA_CROSS',
      shortWindow: 20,
      longWindow: 60,
    })
    expect(strategyByType('MA_CROSS').toRequest({ shortWindow: '5', longWindow: '120' }, common)).toMatchObject({
      shortWindow: 5,
      longWindow: 120,
    })
  })

  it('이동평균 검사: 기간이 2 ~ 250 정수가 아니거나 짧은 기간이 긴 기간 이상이면 막는다', () => {
    const check = (params: Record<string, string>) => strategyByType('MA_CROSS').check(params, common)
    const message = '이동평균 기간은 2 ~ 250 사이 정수로, 짧은 기간을 긴 기간보다 짧게 입력해주세요.'
    expect(check({})).toBeNull()
    expect(check({ shortWindow: '2', longWindow: '250' })).toBeNull()
    expect(check({ shortWindow: '' })).toBe(message)
    expect(check({ shortWindow: '1' })).toBe(message)
    expect(check({ longWindow: '251' })).toBe(message)
    expect(check({ shortWindow: '60' })).toBe(message)
  })

  it('요약: 이동평균은 두 기간의 교차 규칙', () => {
    expect(strategyByType('MA_CROSS').summary({}, year2023)).toEqual({
      text: '20일 선이 60일 선을 넘으면 원금 전부로 사고, 아래로 내려가면 전부 팜',
    })
    expect(strategyByType('MA_CROSS').summary({ shortWindow: '5', longWindow: '120' }, year2023)).toEqual({
      text: '5일 선이 120일 선을 넘으면 원금 전부로 사고, 아래로 내려가면 전부 팜',
    })
  })

  it('단순 매수는 공통 시작일에 산다', () => {
    expect(strategyByType('SIMPLE').toRequest({}, common)).toEqual({
      strategyType: 'SIMPLE',
      name: 'SIMPLE',
      purchaseDate: '2023-01-01',
    })
  })

  it('적립식은 회당 금액 · 주기 · 첫 매수일의 며칠을 싣고 원금은 싣지 않는다', () => {
    expect(strategyByType('DCA').toRequest({}, common)).toEqual({
      strategyType: 'DCA',
      name: 'DCA',
      amountPerPurchase: 100000,
      frequency: 'MONTHLY',
      purchaseDay: 1,
      investmentInterval: 1,
    })
    expect(
      strategyByType('DCA').toRequest(
        { amountPerPurchase: '200000', cadence: 'MONTHS', months: '6', firstPurchaseDate: '2023-03-31' },
        common
      )
    ).toEqual({
      strategyType: 'DCA',
      name: 'DCA',
      amountPerPurchase: 200000,
      frequency: 'MONTHLY',
      firstPurchaseDate: '2023-03-31',
      purchaseDay: 31,
      investmentInterval: 6,
    })
  })

  it('적립식 매주는 주말 시작일을 다음 월요일 첫 매수일로 싣는다', () => {
    expect(strategyByType('DCA').toRequest({ cadence: 'WEEKLY' }, common)).toEqual({
      strategyType: 'DCA',
      name: 'DCA',
      amountPerPurchase: 100000,
      frequency: 'WEEKLY',
      firstPurchaseDate: '2023-01-02',
    })
  })

  it('적립식은 고른 첫 매수일을 시작일이 바뀌면 같은 며칠의 시작일 뒤 첫 날로 옮기고 며칠은 고른 날 그대로 싣는다', () => {
    expect(
      strategyByType('DCA').toRequest({ firstPurchaseDate: '2022-01-31', firstPickedStart: '2022-01-01' }, common)
    ).toMatchObject({ firstPurchaseDate: '2023-01-31', purchaseDay: 31 })
    expect(
      strategyByType('DCA').summary({ firstPurchaseDate: '2022-04-30', firstPickedStart: '2022-04-01', cadence: 'MONTHS', months: '6' }, year2023)
    ).toEqual({ text: '6개월마다 30일 10만원 · 2회 ≈ 20만원' })
  })

  it('조건부 매수는 회당 금액을 싣고 하락률을 비율로 바꾸며 원금은 싣지 않는다', () => {
    expect(strategyByType('CONDITIONAL_PURCHASE').toRequest({ dropPercentage: '7' }, common)).toEqual({
      strategyType: 'CONDITIONAL_PURCHASE',
      name: 'CONDITIONAL_PURCHASE',
      amountPerPurchase: 100000,
      direction: 'DROP',
      dropPercentage: 0.07,
    })
    expect(
      strategyByType('CONDITIONAL_PURCHASE').toRequest({ amountPerPurchase: '250000', maxPurchases: '3' }, common)
    ).toMatchObject({ amountPerPurchase: 250000, maxPurchases: 3 })
  })

  it('조건부 매수는 고른 방향을 싣고 모르는 값은 하락으로 본다', () => {
    expect(strategyByType('CONDITIONAL_PURCHASE').toRequest({ dropPercentage: '7', direction: 'BOTH' }, common)).toMatchObject({
      direction: 'BOTH',
    })
    expect(strategyByType('CONDITIONAL_PURCHASE').toRequest({ dropPercentage: '7', direction: 'UP' }, common)).toMatchObject({
      direction: 'DROP',
    })
  })

  it('조건부 매수는 익절 · 손절을 비율로 싣고 비우면 싣지 않는다', () => {
    expect(strategyByType('CONDITIONAL_PURCHASE').toRequest({ dropPercentage: '7', takeProfit: '20', stopLoss: '10' }, common))
      .toMatchObject({ takeProfitRate: 0.2, stopLossRate: 0.1 })
    const off = strategyByType('CONDITIONAL_PURCHASE').toRequest({ dropPercentage: '7', takeProfit: '' }, common)
    expect(off).not.toHaveProperty('takeProfitRate')
    expect(off).not.toHaveProperty('stopLossRate')
    expect(off).not.toHaveProperty('maxPurchases')
  })

  it('검사: 없는 칸은 기본값으로 통과하고 비운 칸은 막는다(칩 이름은 부르는 쪽이 앞에 둠)', () => {
    expect(strategyByType('SIMPLE').check({}, common)).toBeNull()
    expect(strategyByType('DCA').check({}, common)).toBeNull()
    expect(strategyByType('CONDITIONAL_PURCHASE').check({}, common)).toBeNull()
    expect(strategyByType('DCA').check({ amountPerPurchase: '' }, common)).toBe('올바른 회당 금액을 입력해주세요.')
    expect(strategyByType('CONDITIONAL_PURCHASE').check({ dropPercentage: '' }, common)).toBe(
      '하락률은 0~100 사이여야 합니다.'
    )
    expect(strategyByType('CONDITIONAL_PURCHASE').check({ dropPercentage: '', direction: 'RISE' }, common)).toBe(
      '상승률은 0~100 사이여야 합니다.'
    )
  })

  it('적립식 검사: 회당 금액이 원금보다 크거나 개월 수가 범위 밖이거나 첫 매수일이 종료일 뒤면 막는다', () => {
    expect(strategyByType('DCA').check({ amountPerPurchase: '-1' }, common)).toBe('올바른 회당 금액을 입력해주세요.')
    expect(strategyByType('DCA').check({ amountPerPurchase: '1000001' }, common)).toBe('회당 금액이 원금보다 큽니다.')
    expect(strategyByType('DCA').check({ cadence: 'MONTHS', months: '13' }, common)).toBe(
      '개월 수는 1~12 사이의 정수로 입력해주세요.'
    )
    expect(strategyByType('DCA').check({ cadence: 'MONTHLY', months: '13' }, common)).toBeNull()
    expect(strategyByType('DCA').check({ firstPurchaseDate: '2022-12-31' }, common)).toBeNull()
    expect(strategyByType('DCA').check({ firstPurchaseDate: '2026-10-09' }, common)).toBe(
      '첫 매수일은 시작일과 종료일 사이로 골라주세요.'
    )
  })

  it('조건부 매수 검사: 회당 금액 · 최대 횟수 · 비율 · 익절 · 손절이 범위 밖이면 막는다', () => {
    const check = (params: Record<string, string>) => strategyByType('CONDITIONAL_PURCHASE').check(params, common)
    expect(check({ amountPerPurchase: '0' })).toBe('올바른 회당 금액을 입력해주세요.')
    expect(check({ amountPerPurchase: '1000001' })).toBe('회당 금액이 원금보다 큽니다.')
    expect(check({ maxPurchases: '0' })).toBe('최대 횟수는 비우거나 1 이상의 정수로 입력해주세요.')
    expect(check({ maxPurchases: '1.5' })).toBe('최대 횟수는 비우거나 1 이상의 정수로 입력해주세요.')
    expect(check({ dropPercentage: '101' })).toBe('하락률은 0~100 사이여야 합니다.')
    expect(check({ stopLoss: '100' })).toBe('손절 비율은 0보다 크고 100보다 작아야 합니다.')
    expect(check({ takeProfit: '0' })).toBe('익절 비율은 0보다 커야 합니다.')
  })

  it('요약: 단순은 시작일에 원금, 적립식은 주기 · 회당 금액 · 회차 · 총액', () => {
    expect(strategyByType('SIMPLE').summary({}, year2023)).toEqual({ text: '시작일에 100만원 매수' })
    expect(strategyByType('DCA').summary({ amountPerPurchase: '50000' }, year2023)).toEqual({
      text: '매달 1일 5만원 · 12회 ≈ 60만원',
    })
    expect(strategyByType('DCA').summary({ cadence: 'DAILY', amountPerPurchase: '1000' }, year2023)).toEqual({
      text: '거래일마다 1,000원 · 약 260회 ≈ 26만원',
    })
  })

  it('요약: 원금이 회차보다 모자란 적립식은 사는 회차와 미매수 회차를 나눠 보인다', () => {
    expect(strategyByType('DCA').summary({}, year2023)).toEqual({
      text: '매달 1일 10만원 · 12회 중 10회 ≈ 100만원, 나머지 2회는 원금이 모자라 미매수',
      short: true,
    })
    expect(strategyByType('DCA').summary({ amountPerPurchase: '300000' }, year2023)).toEqual({
      text: '매달 1일 30만원 · 12회 중 3회 ≈ 90만원, 나머지 9회는 원금이 모자라 미매수',
      short: true,
    })
  })

  it('요약: 조건부 매수는 조건 · 회당 금액 · 원금이나 최대 횟수로 정한 횟수 · 익절 · 손절', () => {
    expect(strategyByType('CONDITIONAL_PURCHASE').summary({}, year2023)).toEqual({
      text: '전날보다 5% 내릴 때마다 10만원 · 원금으로 최대 10회',
    })
    expect(strategyByType('CONDITIONAL_PURCHASE').summary({ maxPurchases: '20' }, year2023)).toEqual({
      text: '전날보다 5% 내릴 때마다 10만원 · 원금으로 최대 10회',
    })
    expect(
      strategyByType('CONDITIONAL_PURCHASE').summary(
        { direction: 'BOTH', maxPurchases: '3', takeProfit: '20', stopLoss: '10' },
        year2023
      )
    ).toEqual({ text: '전날보다 5% 움직일 때마다 10만원 · 최대 3회 · +20% 익절 · -10% 손절(평균 단가 기준 전량)' })
  })
})
