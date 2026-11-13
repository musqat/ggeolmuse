import { describe, it, expect } from 'vitest'
import {
  changedOptions,
  conditionalHow,
  dcaHow,
  detailParts,
  maBandAbout,
  maBandHow,
  maCrossAbout,
  maCrossHow,
  partialTakeProfitText,
  portfolioHow,
  sellText,
  simpleHow,
  trailingText,
} from './summary'

const NB = ' '
const won = { byShares: false, amount: '100000', shares: '1' }
const oneShare = (perRound?: number) => ({ byShares: true, amount: '', shares: '1', perRound })

// 포트폴리오 줄 전략과 금액(원, 적립식은 회당)
const lump = (amount: number) => ({ strategy: 'LUMP_SUM' as const, amount })
const dca = (amount: number) => ({ strategy: 'DCA' as const, amount })
const cross = (amount: number) => ({ strategy: 'MA_CROSS' as const, amount })
const band = (amount: number) => ({ strategy: 'MA_BAND' as const, amount })
const schedule = { cadenceLabel: '매달 8일', rounds: 12, daily: false }

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

  it('포트폴리오: 종목 수와 한 번에 넣는 합계, 적립식은 주기 · 회당 합계 · 회차 · 총액', () => {
    const base = { lines: [lump(6000000), lump(4000000)], ...schedule, rebalance: 'NONE' as const }

    expect(portfolioHow(base)).toBe('종목 2개 · 한 번에 1,000만원')
    expect(portfolioHow({ ...base, lines: [dca(500000), dca(300000), dca(200000)] })).toBe('종목 3개 · 매달 8일 100만원 · 12회 ≈ 1,200만원')
    expect(portfolioHow({ ...base, lines: [dca(60000), dca(40000)], cadenceLabel: '거래일마다', rounds: 250, daily: true })).toBe(
      '종목 2개 · 거래일마다 10만원 · 약 250회 ≈ 2,500만원'
    )
  })

  it('포트폴리오 줄 전략이 섞이면 한 번에 · 적립식 · 이동평균 교차 · 밴드마다 줄 수와 금액, 이동평균 교차 · 밴드는 쉼표로 이은 이름과 첫 매수 신호에 넣는 합계', () => {
    expect(portfolioHow({ lines: [lump(6000000), dca(1000000), cross(3000000), band(2000000)], ...schedule, rebalance: 'NONE' })).toBe(
      '종목 4개 · 한 번에 1개 600만원 · 적립식 1개 매달 8일 100만원 · 12회 ≈ 1,200만원 · 이동평균 교차, 밴드 2개 첫 매수 신호에 500만원'
    )
    expect(portfolioHow({ lines: [lump(6000000), lump(1000000), cross(3000000)], ...schedule, rebalance: 'NONE' })).toBe(
      '종목 3개 · 한 번에 2개 700만원 · 이동평균 교차 1개 첫 매수 신호에 300만원'
    )
    expect(portfolioHow({ lines: [band(10000000)], ...schedule, rebalance: 'NONE' })).toBe('종목 1개 · 밴드 1개 첫 매수 신호에 1,000만원')
  })

  it('포트폴리오 리밸런싱: 고른 주기마다 처음 비중으로 다시 나눈다는 글을 뒤에', () => {
    const base = { lines: [lump(6000000), lump(4000000)], ...schedule }

    expect(portfolioHow({ ...base, rebalance: 'YEARLY' })).toBe('종목 2개 · 한 번에 1,000만원 · 매년 처음 비중으로 리밸런싱')
    expect(portfolioHow({ ...base, rebalance: 'QUARTERLY' })).toBe('종목 2개 · 한 번에 1,000만원 · 분기마다 처음 비중으로 리밸런싱')
    expect(portfolioHow({ ...base, lines: [dca(600000), dca(400000)], rebalance: 'MONTHLY' })).toBe(
      '종목 2개 · 매달 8일 100만원 · 12회 ≈ 1,200만원 · 매달 처음 비중으로 리밸런싱'
    )
  })

  it('포트폴리오 리밸런싱은 이동평균 교차 · 밴드 줄을 빼고 나눈다고 적고, 모든 줄이 이동평균 교차 · 밴드면 적지 않음', () => {
    expect(portfolioHow({ lines: [lump(6000000), cross(4000000)], ...schedule, rebalance: 'YEARLY' })).toBe(
      '종목 2개 · 한 번에 1개 600만원 · 이동평균 교차 1개 첫 매수 신호에 400만원 · 매년 처음 비중으로 리밸런싱(이동평균 교차 · 밴드 빼고)'
    )
    expect(portfolioHow({ lines: [cross(6000000), band(4000000)], ...schedule, rebalance: 'YEARLY' })).toBe(
      '종목 2개 · 이동평균 교차, 밴드 2개 첫 매수 신호에 1,000만원'
    )
  })

  it('포트폴리오 익절 · 손절: 줄 글을 매수 글 뒤 · 리밸런싱 앞에, 빈 글이면 넣지 않음', () => {
    const base = { lines: [lump(6000000), lump(4000000)], ...schedule }
    const sell = '1개 종목에 익절 · 손절(평균 단가 기준 전량)'

    expect(portfolioHow({ ...base, rebalance: 'NONE', sell })).toBe(`종목 2개 · 한 번에 1,000만원 · ${sell}`)
    expect(portfolioHow({ ...base, rebalance: 'YEARLY', sell })).toBe(`종목 2개 · 한 번에 1,000만원 · ${sell} · 매년 처음 비중으로 리밸런싱`)
    expect(portfolioHow({ ...base, rebalance: 'NONE', sell: '' })).toBe('종목 2개 · 한 번에 1,000만원')
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

  it('이동평균 교차: 원금 · 따로 고른 신호 종목과 두 기간의 교차 규칙, 빈 기간은 ?', () => {
    expect(maCrossHow({ amount: '1000000', short: '20', long: '60' })).toBe(
      '원금 100만원 · 20일 선이 60일 선을 넘으면 가진 돈 전부로 사고, 아래로 내려가면 전부 팜'
    )
    expect(maCrossHow({ amount: '500000', short: '5', long: ' ' })).toBe(
      '원금 50만원 · 5일 선이 ?일 선을 넘으면 가진 돈 전부로 사고, 아래로 내려가면 전부 팜'
    )
    expect(maCrossHow({ amount: '1000000', short: '20', long: '60', signal: '^GSPC' })).toBe(
      '원금 100만원 · 신호 ^GSPC · 20일 선이 60일 선을 넘으면 가진 돈 전부로 사고, 아래로 내려가면 전부 팜'
    )
  })

  it('이동평균 밴드: 원금 · 따로 고른 신호 종목과 평균 기간 · 위 · 아래 비율의 규칙, 빈 칸은 ?', () => {
    expect(maBandHow({ amount: '1000000', window: '200', upper: '2.5', lower: '2.5' })).toBe(
      '원금 100만원 · 200일선 +2.5% 위로 오르면 가진 돈 전부로 사고, -2.5% 아래로 내려가면 전부 팜'
    )
    expect(maBandHow({ amount: '500000', window: '', upper: '3', lower: ' ' })).toBe(
      '원금 50만원 · ?일선 +3% 위로 오르면 가진 돈 전부로 사고, -?% 아래로 내려가면 전부 팜'
    )
    expect(maBandHow({ amount: '1000000', window: '200', upper: '2.5', lower: '2.5', signal: 'MSFT' })).toBe(
      '원금 100만원 · 신호 MSFT · 200일선 +2.5% 위로 오르면 가진 돈 전부로 사고, -2.5% 아래로 내려가면 전부 팜'
    )
  })

  it('이동평균 교차 · 밴드 모드 설명은 칸의 기간 · 비율을 넣고 빈 칸은 ? 로 둔다', () => {
    expect(maCrossAbout('20', '60')).toBe('최근 20일 평균 가격이 60일 평균보다 높아지면 매수, 낮아지면 매도')
    expect(maCrossAbout(' ', '')).toBe('최근 ?일 평균 가격이 ?일 평균보다 높아지면 매수, 낮아지면 매도')
    expect(maBandAbout('200', '2.5', '3')).toBe('주가가 200일 평균 가격보다 2.5% 넘게 오르면 매수, 3% 넘게 내려가면 매도')
    expect(maBandAbout('', ' ', '')).toBe('주가가 ?일 평균 가격보다 ?% 넘게 오르면 매수, ?% 넘게 내려가면 매도')
  })

  it('매도 조건은 적은 쪽만, 둘 다 비면 빈 글', () => {
    expect(sellText('20', '')).toBe('+20% 익절(평균 단가 기준 전량)')
    expect(sellText('', '')).toBe('')
  })

  it('트레일링 스탑은 하락을 비우면 빈 글, 팔 비율이 비거나 100 이면 전량 · 아니면 보유의 그 비율씩', () => {
    expect(trailingText('', '50')).toBe('')
    expect(trailingText('10', '')).toBe('고점 대비 하락 매도 -10%(전량)')
    expect(trailingText('10', '100')).toBe('고점 대비 하락 매도 -10%(전량)')
    expect(trailingText(' 7.5 ', '50')).toBe('고점 대비 하락 매도 -7.5%(보유의 50%씩)')
  })

  it('조건부 · 이동평균 교차 · 밴드 요약은 매도 규칙 뒤에 트레일링 스탑 글을 잇는다', () => {
    const trailing = '고점 대비 하락 매도 -10%(전량)'

    expect(
      conditionalHow({
        percent: '5',
        direction: 'DROP',
        unit: won,
        budget: '1000000',
        maxPurchases: '',
        weekdays: 260,
        takeProfit: '20',
        stopLoss: '',
        trailing,
      })
    ).toBe(`전날보다 5% 내릴 때마다 10만원 · 예산으로 최대 10회 ≈ 100만원 · +20% 익절(평균 단가 기준 전량) · ${trailing}`)
    expect(maCrossHow({ amount: '1000000', short: '20', long: '60', trailing })).toBe(
      `원금 100만원 · 20일 선이 60일 선을 넘으면 가진 돈 전부로 사고, 아래로 내려가면 전부 팜 · ${trailing}`
    )
    expect(maBandHow({ amount: '1000000', window: '200', upper: '2.5', lower: '2.5', signal: '^GSPC', trailing })).toBe(
      `원금 100만원 · 신호 ^GSPC · 200일선 +2.5% 위로 오르면 가진 돈 전부로 사고, -2.5% 아래로 내려가면 전부 팜 · ${trailing}`
    )
  })

  it('부분 익절은 단계가 없으면 빈 글, 단계마다 수익률에 보유의 팔 비율(100 이상은 전량, 빈 칸은 ?)을 평균 단가 기준으로 적는다', () => {
    expect(partialTakeProfitText([])).toBe('')
    expect(partialTakeProfitText([{ gain: ' 20 ', sell: '25' }])).toBe('부분 익절 +20%에 보유의 25%(평균 단가 기준)')
    expect(
      partialTakeProfitText([
        { gain: '20', sell: '25' },
        { gain: '50', sell: '100' },
        { gain: '', sell: '' },
      ])
    ).toBe('부분 익절 +20%에 보유의 25%, +50%에 전량, +?%에 보유의 ?%(평균 단가 기준)')
  })

  it('조건부 · 이동평균 교차 · 밴드 요약은 트레일링 스탑 뒤에 부분 익절 글을 잇는다', () => {
    const trailing = '고점 대비 하락 매도 -10%(전량)'
    const partial = '부분 익절 +20%에 보유의 25%(평균 단가 기준)'

    expect(
      conditionalHow({
        percent: '5',
        direction: 'DROP',
        unit: won,
        budget: '1000000',
        maxPurchases: '',
        weekdays: 260,
        takeProfit: '',
        stopLoss: '',
        trailing,
        partial,
      })
    ).toBe(`전날보다 5% 내릴 때마다 10만원 · 예산으로 최대 10회 ≈ 100만원 · ${trailing} · ${partial}`)
    expect(maCrossHow({ amount: '1000000', short: '20', long: '60', partial })).toBe(
      `원금 100만원 · 20일 선이 60일 선을 넘으면 가진 돈 전부로 사고, 아래로 내려가면 전부 팜 · ${partial}`
    )
    expect(maBandHow({ amount: '1000000', window: '200', upper: '2.5', lower: '2.5', trailing, partial })).toBe(
      `원금 100만원 · 200일선 +2.5% 위로 오르면 가진 돈 전부로 사고, -2.5% 아래로 내려가면 전부 팜 · ${trailing} · ${partial}`
    )
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

  it('양도세를 켜고 현금이 모자랄 때 팔기를 고르면 양도세 글 뒤에 바꾼 것으로 적고, 양도세를 끄면 적지 않는다', () => {
    const base = {
      periodEnd: 'HOLD' as const,
      tradingFeeRate: '0',
      dividendTax: true,
      capitalGainsTax: true,
      reinvestDividends: false,
      fxMode: 'auto' as const,
    }

    expect(detailParts(base, false, '모자라면 팔아 냄').slice(2, 4)).toEqual([
      { text: '양도세 22%', changed: false },
      { text: '모자라면 팔아 냄', changed: true },
    ])
    expect(detailParts(base, false, '')).toHaveLength(6)
    expect(detailParts({ ...base, capitalGainsTax: false }, false, '모자라면 팔아 냄').map((part) => part.text)).not.toContain(
      '모자라면 팔아 냄'
    )
    expect(changedOptions(base, false, '모자라면 손실 종목부터 팖')).toEqual(['모자라면 손실 종목부터 팖'])
  })

  it('연말 정리를 고를 수 있는 모드에서 양도세 · 연말 정리를 켜면 양도세 글 뒤에 바꾼 것으로 적고, 못 고르는 모드나 양도세를 끄면 적지 않는다', () => {
    const base = {
      periodEnd: 'HOLD' as const,
      tradingFeeRate: '0',
      dividendTax: true,
      capitalGainsTax: true,
      reinvestDividends: false,
      fxMode: 'auto' as const,
      yearEndHarvest: true,
    }
    expect(detailParts(base, false, '모자라면 팔아 냄', true).slice(2, 5)).toEqual([
      { text: '양도세 22%', changed: false },
      { text: '모자라면 팔아 냄', changed: true },
      { text: '연말 정리', changed: true },
    ])
    expect(changedOptions(base, false, '', true)).toEqual(['연말 정리'])
    expect(changedOptions(base, false, '', false)).toEqual([])
    expect(changedOptions({ ...base, capitalGainsTax: false }, false, '', true)).toEqual(['양도세 끔'])
    expect(changedOptions({ ...base, yearEndHarvest: false }, false, '', true)).toEqual([])
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
