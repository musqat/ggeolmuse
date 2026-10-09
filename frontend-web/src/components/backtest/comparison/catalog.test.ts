import { describe, it, expect } from 'vitest'
import { COMPARISON_STRATEGIES, STRATEGY_NAMES, strategyByType } from './catalog'

const common = { startDate: '2023-01-01', investment: '1000000' }

describe('비교 전략 카탈로그', () => {
  it('버튼 순서와 이름', () => {
    expect(COMPARISON_STRATEGIES.map((s) => s.type)).toEqual(['SIMPLE', 'DCA', 'CONDITIONAL_PURCHASE'])
    expect(STRATEGY_NAMES).toEqual({ SIMPLE: '단순 매수', DCA: '적립식', CONDITIONAL_PURCHASE: '조건부 매수' })
  })

  it('고를 때 채우는 기본값', () => {
    expect(strategyByType('SIMPLE').defaultParams).toEqual({})
    expect(strategyByType('DCA').defaultParams).toEqual({
      monthlyAmount: '100000',
      purchaseDay: '15',
      investmentInterval: '1',
    })
    expect(strategyByType('CONDITIONAL_PURCHASE').defaultParams).toEqual({ dropPercentage: '5' })
  })

  it('단순 매수는 공통 시작일에 산다', () => {
    expect(strategyByType('SIMPLE').toRequest({}, common)).toEqual({
      strategyType: 'SIMPLE',
      name: 'SIMPLE',
      purchaseDate: '2023-01-01',
    })
  })

  it('적립식은 빈 값을 기본값으로 채우고 공통 투자금을 한도로 보낸다', () => {
    expect(strategyByType('DCA').toRequest({}, common)).toEqual({
      strategyType: 'DCA',
      name: 'DCA',
      monthlyAmount: 100000,
      purchaseDay: 15,
      investmentInterval: 1,
      totalInvestmentLimit: 1000000,
    })
  })

  it('조건부 매수는 하락률을 비율로 바꾸고 총 투자금이 없으면 공통 투자금을 쓴다', () => {
    expect(strategyByType('CONDITIONAL_PURCHASE').toRequest({ dropPercentage: '7' }, common)).toEqual({
      strategyType: 'CONDITIONAL_PURCHASE',
      name: 'CONDITIONAL_PURCHASE',
      totalInvestment: 1000000,
      dropPercentage: 0.07,
    })
  })

  it('모달 저장 검사', () => {
    expect(strategyByType('SIMPLE').checkOnSave({})).toBeNull()
    expect(strategyByType('DCA').checkOnSave({ monthlyAmount: '100000' })).toBe(
      '적립식: 월 투자금과 매수일을 입력해주세요.'
    )
    expect(strategyByType('DCA').checkOnSave({ monthlyAmount: '1', purchaseDay: '15' })).toBeNull()
    expect(strategyByType('CONDITIONAL_PURCHASE').checkOnSave({ dropPercentage: '' })).toBe(
      '조건부 매수: 하락률을 입력해주세요.'
    )
  })

  it('실행 전 검사', () => {
    expect(strategyByType('SIMPLE').checkOnRun({}, common)).toBeNull()
    expect(strategyByType('DCA').checkOnRun({}, common)).toBeNull()
    expect(strategyByType('DCA').checkOnRun({ monthlyAmount: '-1' }, common)).toBe(
      '적립식: 월 투자금이 유효하지 않습니다.'
    )
    expect(strategyByType('DCA').checkOnRun({ purchaseDay: '32' }, common)).toBe(
      '적립식: 매수일이 유효하지 않습니다 (1-31).'
    )
    expect(strategyByType('CONDITIONAL_PURCHASE').checkOnRun({}, { ...common, investment: '' })).toBe(
      '조건부 매수: 총 투자금이 유효하지 않습니다.'
    )
    expect(strategyByType('CONDITIONAL_PURCHASE').checkOnRun({ dropPercentage: '-1' }, common)).toBe(
      '조건부 매수: 하락률이 유효하지 않습니다.'
    )
  })
})
