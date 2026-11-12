import { describe, it, expect } from 'vitest'
import {
  amountIssue,
  bandIssues,
  budgetIssue,
  countErrors,
  feeIssue,
  firstIssue,
  fxGapNotice,
  fxRateIssue,
  maxIssue,
  monthsIssue,
  partialTakeProfitIssues,
  percentIssue,
  portfolioRowIssues,
  portfolioRowMessageList,
  sellIssue,
  signalIssue,
  symbolIssue,
  symbolsIssue,
  trailingIssues,
  windowIssues,
  wonIssue,
} from './checks'

const error = (text: string) => ({ kind: 'error', text })
const warning = (text: string) => ({ kind: 'warning', text })

describe('설정 칸 검사', () => {
  it('종목: 비었거나 목록에 없으면 오류, 목록이 아직 없으면 목록은 보지 않음', () => {
    expect(symbolIssue('AAPL', ['AAPL', 'MSFT'])).toBeNull()
    expect(symbolIssue('', ['AAPL'])).toEqual(error('종목 비어 있음'))
    expect(symbolIssue('ZZZZ', ['AAPL'])).toEqual(error('목록에서 고르기'))
    expect(symbolIssue('ZZZZ', [])).toBeNull()
    expect(symbolsIssue(['AAPL'])).toEqual(error('종목을 둘 이상'))
    expect(symbolsIssue(['AAPL', 'MSFT'])).toBeNull()
  })

  it('신호 종목: 다른 종목일 때만 비었거나 목록에 없으면 오류', () => {
    expect(signalIssue('same', '', ['AAPL'])).toBeNull()
    expect(signalIssue('^GSPC', '', ['AAPL'])).toBeNull()
    expect(signalIssue('other', ' ', ['AAPL'])).toEqual(error('신호 종목 비어 있음'))
    expect(signalIssue('other', 'ZZZZ', ['AAPL'])).toEqual(error('목록에서 고르기'))
    expect(signalIssue('other', 'msft', ['AAPL', 'MSFT'])).toBeNull()
  })

  it('금액: 비면 칸 이름 + 비어 있음, 0 이하면 0 보다 크게, 주식 수는 1 ~ 1,000,000 정수이고 넘으면 상한 안내', () => {
    expect(amountIssue({ byShares: false, amount: '300000', shares: '1', label: '금액' })).toBeNull()
    expect(amountIssue({ byShares: false, amount: '', shares: '1', label: '회당 금액' })).toEqual(error('회당 금액 비어 있음'))
    expect(amountIssue({ byShares: false, amount: '0', shares: '1', label: '금액' })).toEqual(error('0 보다 크게'))
    expect(amountIssue({ byShares: true, amount: '', shares: '1.5', label: '금액' })).toEqual(error('1 이상 정수'))
    expect(amountIssue({ byShares: true, amount: '', shares: '2', label: '금액' })).toBeNull()
    expect(amountIssue({ byShares: true, amount: '', shares: '1000000', label: '금액' })).toBeNull()
    expect(amountIssue({ byShares: true, amount: '', shares: '1000001', label: '금액' })).toEqual(
      error('한 번에 1,000,000주까지 계산 · 더 크면 금액으로')
    )
  })

  it('원 단위 금액: 금액 검사에 더해 원 미만이 있으면 원 단위로', () => {
    expect(wonIssue('10000000', '총액')).toBeNull()
    expect(wonIssue('10000000.0', '총액')).toBeNull()
    expect(wonIssue('', '회당 총액')).toEqual(error('회당 총액 비어 있음'))
    expect(wonIssue('0', '총액')).toEqual(error('0 보다 크게'))
    expect(wonIssue('10000000.5', '총액')).toEqual(error('원 단위로'))
  })

  it('예산: 비면 제한 없음, 0 이하면 오류, 한 번 살 돈보다 적으면 금액은 오류 · 주식 수는 추정이라 경고', () => {
    expect(budgetIssue({ budget: '', perRound: 100000, byShares: false })).toBeNull()
    expect(budgetIssue({ budget: '0', perRound: 100000, byShares: false })).toEqual(error('0 보다 크게'))
    expect(budgetIssue({ budget: '50000', perRound: 100000, byShares: false })).toEqual(error('한 번 살 돈보다 적음'))
    expect(budgetIssue({ budget: '300000', perRound: 337500, byShares: true })).toEqual(
      warning('한 번 살 돈(≈ 33만 7,500원)보다 적음')
    )
    expect(budgetIssue({ budget: '300000', perRound: undefined, byShares: true })).toBeNull()
  })

  it('개월 수는 N개월마다에서만 1 ~ 12', () => {
    expect(monthsIssue('MONTHS', '0')).toEqual(error('개월은 1 ~ 12'))
    expect(monthsIssue('MONTHS', '13')).toEqual(error('개월은 1 ~ 12'))
    expect(monthsIssue('MONTHS', '6')).toBeNull()
    expect(monthsIssue('MONTHLY', '')).toBeNull()
  })

  it('첫 매수일: 끝 날짜 뒤면 오류, 매달 29일 이후면 없는 달 규칙 경고', () => {
    expect(firstIssue({ first: '2026-12-01', end: '2026-11-08', cadence: 'WEEKLY', monthDay: 1 })).toEqual(
      error('첫 매수일이 끝 날짜 뒤')
    )
    expect(firstIssue({ first: '2026-01-31', end: '2026-11-08', cadence: 'MONTHS', monthDay: 31 })).toEqual(
      warning('31일이 없는 달은 그 달 마지막 거래일')
    )
    expect(firstIssue({ first: '2026-01-31', end: '2026-11-08', cadence: 'WEEKLY', monthDay: 31 })).toBeNull()
    expect(firstIssue({ first: '2026-01-28', end: '2026-11-08', cadence: 'MONTHLY', monthDay: 28 })).toBeNull()
  })

  it('조건 %: 비면 % 비어 있음, 0 이하 · 100 넘으면 0 ~ 100 사이', () => {
    expect(percentIssue('5')).toBeNull()
    expect(percentIssue('')).toEqual(error('% 비어 있음'))
    expect(percentIssue('0')).toEqual(error('0 ~ 100 사이'))
    expect(percentIssue('101')).toEqual(error('0 ~ 100 사이'))
  })

  it('매도 조건: 적은 값은 0 보다 크게, 손절은 100 아래', () => {
    expect(sellIssue('', '')).toBeNull()
    expect(sellIssue('20', '10')).toBeNull()
    expect(sellIssue('0', '')).toEqual(error('0 보다 크게'))
    expect(sellIssue('', '100')).toEqual(error('손절은 100 아래'))
  })

  it('트레일링 스탑: 둘 다 비면 끔, 하락은 0 초과 100 미만, 팔 비율은 하락과 함께 0 초과 100 이하', () => {
    expect(trailingIssues('', '')).toEqual({ stop: null, sell: null })
    expect(trailingIssues('10', '')).toEqual({ stop: null, sell: null })
    expect(trailingIssues('99.9', '100')).toEqual({ stop: null, sell: null })
    expect(trailingIssues('0', '')).toEqual({ stop: error('0 초과 100 미만'), sell: null })
    expect(trailingIssues('100', '50')).toEqual({ stop: error('0 초과 100 미만'), sell: null })
    expect(trailingIssues('10', '0')).toEqual({ stop: null, sell: error('0 초과 100 이하') })
    expect(trailingIssues('10', '100.1')).toEqual({ stop: null, sell: error('0 초과 100 이하') })
    expect(trailingIssues('', '50')).toEqual({ stop: null, sell: error('하락 비율과 함께') })
  })

  it('부분 익절: 단계마다 수익률은 0 초과로 앞 단계보다 크게, 팔 비율은 0 초과 100 이하, 빈 칸은 오류', () => {
    const step = (gain: string, sell: string) => ({ gain, sell })
    const ok = { gain: null, sell: null }

    expect(partialTakeProfitIssues([])).toEqual([])
    expect(partialTakeProfitIssues([step('20', '25'), step('50', '100')])).toEqual([ok, ok])
    expect(partialTakeProfitIssues([step('', '')])).toEqual([
      { gain: error('1단계 수익률 비어 있음'), sell: error('1단계 팔 비율 비어 있음') },
    ])
    expect(partialTakeProfitIssues([step('0', '0'), step('10', '100.1')])).toEqual([
      { gain: error('1단계 수익률 0 초과'), sell: error('1단계 팔 비율 0 초과 100 이하') },
      { gain: null, sell: error('2단계 팔 비율 0 초과 100 이하') },
    ])
    expect(partialTakeProfitIssues([step('20', '25'), step('20', '25'), step('', '25'), step('15', '25')])).toEqual([
      ok,
      { gain: error('2단계 수익률 앞 단계보다 크게'), sell: null },
      { gain: error('3단계 수익률 비어 있음'), sell: null },
      { gain: error('4단계 수익률 앞 단계보다 크게'), sell: null },
    ])
  })

  describe('포트폴리오 줄', () => {
    const row = (symbol: string, amount: string, weight: string, takeProfit = '', stopLoss = '') => ({
      symbol,
      amount,
      weight,
      takeProfit,
      stopLoss,
      strategy: '' as const,
      shortWindow: '20',
      longWindow: '60',
      bandWindow: '200',
      upper: '2.5',
      lower: '2.5',
    })
    const noSell = { takeProfit: null, stopLoss: null }
    const noSignal = { shortWindow: null, longWindow: null, bandWindow: null, upper: null, lower: null }
    const ok = { symbol: null, value: null, ...noSell, ...noSignal }
    const list = ['SPY', 'QQQ', 'IAU', 'TLT']

    it('종목은 비었거나 목록에 없거나 앞 줄과 같으면, 금액 방식은 금액 · 비율 방식은 0 초과 100 이하 비율이 아니면 그 줄 오류', () => {
      expect(portfolioRowIssues([row('SPY', '6000000', ''), row('qqq', '4000000', '')], 'AMOUNT', list)).toEqual([ok, ok])
      expect(portfolioRowIssues([row('', '', '50'), row('ZZZ', '0', '50'), row(' spy ', '1', '')], 'AMOUNT', list)).toEqual([
        { symbol: error('1번 줄 종목 비어 있음'), value: error('1번 줄 금액 비어 있음'), ...noSell, ...noSignal },
        { symbol: error('2번 줄 종목 목록에서 고르기'), value: error('2번 줄 금액 0 보다 크게'), ...noSell, ...noSignal },
        ok,
      ])
      expect(portfolioRowIssues([row('SPY', '', '60'), row('QQQ', '', ''), row(' spy ', '', '100.5'), row('QQQ', '', '0')], 'WEIGHT', list)).toEqual([
        ok,
        { symbol: null, value: error('2번 줄 비율 비어 있음'), ...noSell, ...noSignal },
        { symbol: error('3번 줄 종목이 앞 줄과 같음'), value: error('3번 줄 비율 0 초과 100 이하'), ...noSell, ...noSignal },
        { symbol: error('4번 줄 종목이 앞 줄과 같음'), value: error('4번 줄 비율 0 초과 100 이하'), ...noSell, ...noSignal },
      ])
      expect(portfolioRowIssues([row('ZZZ', '1', '')], 'AMOUNT', [])).toEqual([ok])
    })

    it('금액 방식 줄 금액에 원 미만이 있으면 원 단위로, 소수 자리가 0 뿐이면 통과', () => {
      expect(portfolioRowIssues([row('SPY', '1000000.5', ''), row('QQQ', '1000000.0', '')], 'AMOUNT', list)).toEqual([
        { ...ok, value: error('1번 줄 금액 원 단위로') },
        ok,
      ])
    })

    it('익절 · 손절은 비면 안 함, 0 이하나 못 읽는 글이면 0 보다 크게, 손절이 100 이상이면 100 아래(금액 · 비율 방식 같음)', () => {
      const rows = [row('SPY', '1', '50', '30', '10'), row('QQQ', '1', '50', '0', ''), row('IAU', '1', '50', '', '100'), row('TLT', '1', '50', '.', '0')]
      const expected = [
        ok,
        { ...ok, takeProfit: error('2번 줄 익절 0 보다 크게') },
        { ...ok, stopLoss: error('3번 줄 손절 100 아래') },
        { ...ok, takeProfit: error('4번 줄 익절 0 보다 크게'), stopLoss: error('4번 줄 손절 0 보다 크게') },
      ]

      expect(portfolioRowIssues(rows, 'AMOUNT', list)).toEqual(expected)
      expect(portfolioRowIssues(rows, 'WEIGHT', list)).toEqual(expected)
      expect(portfolioRowIssues([row('SPY', '1', '', '250', '99.9')], 'AMOUNT', list)).toEqual([ok])
    })

    it('이동평균 줄은 두 기간이 2 ~ 250 정수이고 짧은 기간이 긴 기간보다 짧은지, 밴드 줄은 평균 기간과 위 · 아래 비율을 본다', () => {
      const cross = (shortWindow: string, longWindow: string) => ({ ...row('SPY', '1', ''), strategy: 'MA_CROSS' as const, shortWindow, longWindow })
      const band = (bandWindow: string, upper: string, lower: string) => ({ ...row('QQQ', '1', ''), strategy: 'MA_BAND' as const, bandWindow, upper, lower })

      expect(portfolioRowIssues([cross('20', '60'), band('200', '2.5', '0')], 'AMOUNT', list)).toEqual([ok, ok])
      expect(portfolioRowIssues([cross('1', '251'), band('', '', '100')], 'AMOUNT', list)).toEqual([
        { ...ok, shortWindow: error('1번 줄 짧은 기간 2 ~ 250 사이 정수'), longWindow: error('1번 줄 긴 기간 2 ~ 250 사이 정수') },
        {
          ...ok,
          bandWindow: error('2번 줄 밴드 기간 2 ~ 250 사이 정수'),
          upper: error('2번 줄 위 비율 비어 있음'),
          lower: error('2번 줄 아래 비율 0 이상 100 미만'),
        },
      ])
      expect(portfolioRowIssues([cross('60', '60'), band('2.5', '-1', '2.5')], 'WEIGHT', list)).toEqual([
        { ...ok, value: error('1번 줄 비율 비어 있음'), shortWindow: error('1번 줄 짧은 기간 긴 기간보다 짧게') },
        { ...ok, value: error('2번 줄 비율 비어 있음'), bandWindow: error('2번 줄 밴드 기간 2 ~ 250 사이 정수'), upper: error('2번 줄 위 비율 0 이상 100 미만') },
      ])
    })

    it('한 번에 · 적립식 줄은 남아 있는 이동평균 · 밴드 칸을 보지 않는다', () => {
      const idle = { shortWindow: '1', longWindow: '', bandWindow: 'x', upper: '', lower: '100' }

      expect(
        portfolioRowIssues(
          [
            { ...row('SPY', '1', ''), ...idle },
            { ...row('QQQ', '1', ''), ...idle, strategy: 'DCA' as const },
            { ...row('IAU', '1', ''), ...idle, strategy: 'LUMP_SUM' as const },
          ],
          'AMOUNT',
          list,
        ),
      ).toEqual([ok, ok, ok])
    })

    it('줄 칸 오류는 종목 · 금액 · 익절 · 손절 · 이동평균 · 밴드 칸 차례로', () => {
      const typed = (text: string) => ({ kind: 'error' as const, text })
      const messages = {
        symbol: typed('a'),
        value: typed('b'),
        takeProfit: typed('c'),
        stopLoss: typed('d'),
        shortWindow: typed('e'),
        longWindow: typed('f'),
        bandWindow: typed('g'),
        upper: typed('h'),
        lower: typed('i'),
      }

      expect(portfolioRowMessageList(messages).map((message) => message?.text)).toEqual(['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i'])
    })
  })

  it('최대 횟수: 비면 제한 없음, 적으면 1 이상 정수', () => {
    expect(maxIssue('')).toBeNull()
    expect(maxIssue('3')).toBeNull()
    expect(maxIssue('0')).toEqual(error('1 이상 정수'))
    expect(maxIssue('2.5')).toEqual(error('1 이상 정수'))
  })

  it('이동평균 기간: 칸마다 2 ~ 250 정수, 짧은 기간이 긴 기간 이상이면 짧은 칸 오류', () => {
    expect(windowIssues('20', '60')).toEqual({ short: null, long: null })
    expect(windowIssues('2', '250')).toEqual({ short: null, long: null })
    expect(windowIssues('', '60')).toEqual({ short: error('2 ~ 250 사이 정수'), long: null })
    expect(windowIssues('1', '251')).toEqual({ short: error('2 ~ 250 사이 정수'), long: error('2 ~ 250 사이 정수') })
    expect(windowIssues('20', '20.5')).toEqual({ short: null, long: error('2 ~ 250 사이 정수') })
    expect(windowIssues('60', '60')).toEqual({ short: error('긴 기간보다 짧게'), long: null })
    expect(windowIssues('90', '60')).toEqual({ short: error('긴 기간보다 짧게'), long: null })
  })

  it('밴드: 평균 기간은 2 ~ 250 정수, 위 · 아래 비율은 비면 비어 있음 · 0 이상 100 미만', () => {
    expect(bandIssues('200', '2.5', '2.5')).toEqual({ window: null, upper: null, lower: null })
    expect(bandIssues('2', '0', '99.9')).toEqual({ window: null, upper: null, lower: null })
    expect(bandIssues('1', '', '100')).toEqual({
      window: error('2 ~ 250 사이 정수'),
      upper: error('% 비어 있음'),
      lower: error('0 이상 100 미만'),
    })
    expect(bandIssues('251', '.', '2.5')).toEqual({ window: error('2 ~ 250 사이 정수'), upper: error('0 이상 100 미만'), lower: null })
  })

  it('수수료: 비면 0, 숫자가 아니면 숫자로, 100 이상이면 100 아래', () => {
    expect(feeIssue('')).toBeNull()
    expect(feeIssue('0.25')).toBeNull()
    expect(feeIssue('.')).toEqual(error('숫자로'))
    expect(feeIssue('100')).toEqual(error('100 아래'))
  })

  it('직접 입력 환율: 비면 시장 환율, 적으면 0 보다 크게', () => {
    expect(fxRateIssue('')).toBeNull()
    expect(fxRateIssue('1350')).toBeNull()
    expect(fxRateIssue('0')).toEqual(error('0 보다 크게'))
  })

  it('2014년 전부터면 기본 환율 안내, 매수 환율을 직접 적었으면 안내 없음', () => {
    const auto = { fxMode: 'auto' as const, manualPurchaseFxRate: '' }
    const notice = warning('2014년 이전은 환율 기록이 없어 그 기간은 기본 환율로 계산')

    expect(fxGapNotice('2013-12-31', auto)).toEqual(notice)
    expect(fxGapNotice('2014-01-01', auto)).toBeNull()
    expect(fxGapNotice('2013-12-31', { fxMode: 'manual', manualPurchaseFxRate: '' })).toEqual(notice)
    expect(fxGapNotice('2013-12-31', { fxMode: 'manual', manualPurchaseFxRate: '1130' })).toBeNull()
    expect(fxGapNotice('', auto)).toBeNull()
  })

  it('오류 수는 경고를 빼고 센다', () => {
    expect(countErrors([error('a'), warning('b'), null, undefined, error('c')] as never)).toBe(2)
  })
})
