import { describe, it, expect } from 'vitest'
import {
  amountIssue,
  budgetIssue,
  countErrors,
  firstIssue,
  maxIssue,
  monthsIssue,
  percentIssue,
  sellIssue,
  symbolIssue,
  symbolsIssue,
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

  it('금액: 비면 칸 이름 + 비어 있음, 0 이하면 0 보다 크게, 주식 수는 1 이상 정수', () => {
    expect(amountIssue({ byShares: false, amount: '300000', shares: '1', label: '금액' })).toBeNull()
    expect(amountIssue({ byShares: false, amount: '', shares: '1', label: '회당 금액' })).toEqual(error('회당 금액 비어 있음'))
    expect(amountIssue({ byShares: false, amount: '0', shares: '1', label: '금액' })).toEqual(error('0 보다 크게'))
    expect(amountIssue({ byShares: true, amount: '', shares: '1.5', label: '금액' })).toEqual(error('1 이상 정수'))
    expect(amountIssue({ byShares: true, amount: '', shares: '2', label: '금액' })).toBeNull()
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

  it('최대 횟수: 비면 제한 없음, 적으면 1 이상 정수', () => {
    expect(maxIssue('')).toBeNull()
    expect(maxIssue('3')).toBeNull()
    expect(maxIssue('0')).toEqual(error('1 이상 정수'))
    expect(maxIssue('2.5')).toEqual(error('1 이상 정수'))
  })

  it('오류 수는 경고를 빼고 센다', () => {
    expect(countErrors([error('a'), warning('b'), null, undefined, error('c')] as never)).toBe(2)
  })
})
