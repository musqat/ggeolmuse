import { describe, it, expect } from 'vitest'
import { changedOptions, conditionalHow, dcaHow, detailParts, maBandHow, maCrossHow, sellText, simpleHow } from './summary'

const NB = ' '
const won = { byShares: false, amount: '100000', shares: '1' }
const oneShare = (perRound?: number) => ({ byShares: true, amount: '', shares: '1', perRound })

describe('요약 둘째 줄', () => {
  it('단순: 시작일에 금액 매수, 주식 수면 추정 금액과 기준', () => {
    expect(simpleHow({ byShares: false, amount: '1000000', shares: '1' })).toBe('시작일에 100만원 매수')
    expect(simpleHow({ byShares: true, amount: '', shares: '3', perRound: 1012500 })).toBe(
      `시작일에 3주 매수 ≈ 101만${NB}2,500원(시작일 값 기준)`
    )
    expect(simpleHow({ byShares: true, amount: '', shares: '3' })).toBe('시작일에 3주 매수')
  })

  it('시작일 환율을 못 받아 기본 환율로 센 추정은 기준에 기본 환율을 적는다', () => {
    expect(simpleHow({ byShares: true, amount: '', shares: '3', perRound: 1012500, defaultFx: true })).toBe(
      `시작일에 3주 매수 ≈ 101만${NB}2,500원(시작일 값 · 기본 환율 기준)`
    )
    expect(dcaHow({ unit: { ...oneShare(337500), defaultFx: true }, cadenceLabel: '매주 수요일', rounds: 52, daily: false, budget: '' })).toBe(
      '매주 수요일 1주 · 52회 ≈ 1,755만원(시작일 값 · 기본 환율 기준)'
    )
  })

  it('적립식: 주기 · 회당 · 회차 · 총액, 매일은 약', () => {
    expect(dcaHow({ unit: { ...won, amount: '300000' }, cadenceLabel: '매주 첫 거래일', rounds: 52, daily: false, budget: '' })).toBe(
      '매주 첫 거래일 30만원 · 52회 ≈ 1,560만원'
    )
    expect(dcaHow({ unit: won, cadenceLabel: '거래일마다', rounds: 250, daily: true, budget: '' })).toBe(
      '거래일마다 10만원 · 약 250회 ≈ 2,500만원'
    )
  })

  it('적립식 주식 수: 예산이 모자라면 산 회차와 나머지 미매수, 시세를 아직 모르면 회차만', () => {
    expect(dcaHow({ unit: oneShare(337500), cadenceLabel: '매주 첫 거래일', rounds: 52, daily: false, budget: '5000000' })).toBe(
      `매주 첫 거래일 1주 · 52회 중 14회 ≈ 472만${NB}5,000원(시작일 값 기준) · 나머지 미매수`
    )
    expect(dcaHow({ unit: oneShare(337500), cadenceLabel: '매주 첫 거래일', rounds: 52, daily: false, budget: '' })).toBe(
      '매주 첫 거래일 1주 · 52회 ≈ 1,755만원(시작일 값 기준)'
    )
    expect(dcaHow({ unit: oneShare(), cadenceLabel: '매주 첫 거래일', rounds: 52, daily: false, budget: '5000000' })).toBe(
      '매주 첫 거래일 1주 · 52회'
    )
  })

  describe('조건부', () => {
    const base = {
      percent: '5',
      direction: 'DROP' as const,
      unit: won,
      budget: '1000000',
      maxPurchases: '',
      weekdays: 260,
      takeProfit: '',
      stopLoss: '',
    }

    it('예산 · 최대 횟수 가운데 먼저 닿는 쪽으로 횟수와 총액', () => {
      expect(conditionalHow(base)).toBe('전날보다 5% 내릴 때마다 10만원 · 예산으로 최대 10회 ≈ 100만원')
      expect(conditionalHow({ ...base, maxPurchases: '5' })).toBe('전날보다 5% 내릴 때마다 10만원 · 최대 5회 ≈ 50만원(예산 100만원)')
      expect(conditionalHow({ ...base, maxPurchases: '20' })).toBe(
        '전날보다 5% 내릴 때마다 10만원 · 예산으로 최대 10회 ≈ 100만원(최대 횟수 20)'
      )
      expect(conditionalHow({ ...base, budget: '', maxPurchases: '3' })).toBe('전날보다 5% 내릴 때마다 10만원 · 최대 3회 ≈ 30만원')
    })

    it('둘 다 비면 거래일 수로 많아야 약 N회, 예산이 한 번 살 돈보다 적으면 예산만', () => {
      expect(conditionalHow({ ...base, budget: '' })).toBe('전날보다 5% 내릴 때마다 10만원 · 횟수 제한 없음 · 많아야 약 260회 ≈ 2,600만원')
      expect(conditionalHow({ ...base, budget: '50000' })).toBe('전날보다 5% 내릴 때마다 10만원 · 예산 5만원')
    })

    it('방향 동사 · 익절 · 손절을 넣고 주식 수는 추정 기준, 시세를 아직 모르면 한도만', () => {
      expect(conditionalHow({ ...base, direction: 'RISE', takeProfit: '20', stopLoss: '10' })).toBe(
        '전날보다 5% 오를 때마다 10만원 · 예산으로 최대 10회 ≈ 100만원 · +20% 익절 · -10% 손절(평균 단가 기준 전량)'
      )
      expect(conditionalHow({ ...base, unit: oneShare(250000) })).toBe(
        '전날보다 5% 내릴 때마다 1주 · 예산으로 최대 4회 ≈ 100만원(시작일 값 기준)'
      )
      expect(conditionalHow({ ...base, unit: oneShare() })).toBe('전날보다 5% 내릴 때마다 1주 · 예산 100만원')
      expect(conditionalHow({ ...base, unit: oneShare(), maxPurchases: '3' })).toBe('전날보다 5% 내릴 때마다 1주 · 최대 3회')
    })
  })

  it('이동평균: 원금과 두 기간의 교차 규칙, 빈 기간은 ?', () => {
    expect(maCrossHow({ amount: '1000000', short: '20', long: '60' })).toBe(
      '원금 100만원 · 20일 선이 60일 선을 넘으면 가진 돈 전부로 사고, 아래로 내려가면 전부 팜'
    )
    expect(maCrossHow({ amount: '500000', short: '5', long: ' ' })).toBe(
      '원금 50만원 · 5일 선이 ?일 선을 넘으면 가진 돈 전부로 사고, 아래로 내려가면 전부 팜'
    )
  })

  it('이동평균 밴드: 원금과 평균 기간 · 위 · 아래 비율의 규칙, 빈 칸은 ?', () => {
    expect(maBandHow({ amount: '1000000', window: '200', upper: '2.5', lower: '2.5' })).toBe(
      '원금 100만원 · 200일선 +2.5% 위로 오르면 가진 돈 전부로 사고, -2.5% 아래로 내려가면 전부 팜'
    )
    expect(maBandHow({ amount: '500000', window: '', upper: '3', lower: ' ' })).toBe(
      '원금 50만원 · ?일선 +3% 위로 오르면 가진 돈 전부로 사고, -?% 아래로 내려가면 전부 팜'
    )
  })

  it('매도 조건은 적은 쪽만, 둘 다 비면 빈 글', () => {
    expect(sellText('20', '')).toBe('+20% 익절(평균 단가 기준 전량)')
    expect(sellText('', '')).toBe('')
  })

  it('바꾼 설정: 기본값과 다른 옵션만, 재투자는 막혔으면 뺌', () => {
    const base = {
      periodEnd: 'HOLD' as const,
      tradingFeeRate: '0',
      dividendTax: true,
      capitalGainsTax: true,
      reinvestDividends: false,
      fxMode: 'auto' as const,
    }
    expect(changedOptions(base, false)).toEqual([])
    const changed = {
      periodEnd: 'SELL_AT_END' as const,
      tradingFeeRate: '0.1',
      dividendTax: false,
      capitalGainsTax: false,
      reinvestDividends: true,
      fxMode: 'manual' as const,
    }
    expect(changedOptions(changed, false)).toEqual([
      '마지막 거래일에 팔기',
      '배당세 끔',
      '양도세 끔',
      '배당 재투자',
      '수수료 0.1%',
      '환율 직접 입력',
    ])
    expect(changedOptions(changed, true)).toEqual(['마지막 거래일에 팔기', '배당세 끔', '양도세 끔', '수수료 0.1%', '환율 직접 입력'])
  })

  it('접힌 세부 설정: 기본값도 값으로 쓰고 바꾼 것만 표시, 수수료 빈 칸은 0%', () => {
    const base = {
      periodEnd: 'HOLD' as const,
      tradingFeeRate: '',
      dividendTax: true,
      capitalGainsTax: true,
      reinvestDividends: true,
      fxMode: 'auto' as const,
    }

    expect(detailParts(base, false)).toEqual([
      { text: '끝까지 보유', changed: false },
      { text: '배당세 15%', changed: false },
      { text: '양도세 22%', changed: false },
      { text: '배당 재투자', changed: true },
      { text: '수수료 0%', changed: false },
      { text: '환율 자동', changed: false },
    ])
    expect(detailParts(base, true)[3]).toEqual({ text: '배당 현금', changed: false })
  })
})
