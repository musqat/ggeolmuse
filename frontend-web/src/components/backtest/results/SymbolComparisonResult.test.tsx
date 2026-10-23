import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { SymbolComparisonResult } from './SymbolComparisonResult'

vi.mock('./CompareSymbolsChartMemoized', () => ({
  CompareSymbolsChartMemoized: () => null,
}))

const item = (symbol: string, totalReturnKrw: number, totalReturnPercent: number) => ({
  code: symbol,
  name: symbol,
  purchaseDate: '2023-01-03',
  currentDate: '2024-01-03',
  investmentAmount: 1000000,
  currentValueKrw: 1000000 + totalReturnKrw,
  totalReturnKrw,
  totalReturnPercent,
  shares: 10,
  purchasePrice: 100,
  currentPrice: 110,
  purchaseFxRate: 1300,
  currentFxRate: 1350,
})

const result = {
  mode: 'compare-symbols',
  bestPerformer: { name: 'AAPL', code: 'AAPL', totalReturnPercent: 12 },
  items: [item('AAPL', 120000, 12), item('MSFT', 80000, 8)],
}

const renderResult = (value: typeof result) =>
  render(<SymbolComparisonResult result={value} comparePurchaseDate="2023-01-03" compareSaleDate="" />)

describe('SymbolComparisonResult', () => {
  it('비교한 종목을 모두 보여준다', () => {
    renderResult(result)

    expect(screen.getAllByText('AAPL').length).toBeGreaterThan(0)
    expect(screen.getAllByText('MSFT').length).toBeGreaterThan(0)
  })

  it('보유일은 매수일부터 기준일까지 센다', () => {
    renderResult(result)

    expect(screen.getAllByText('365일')).toHaveLength(2)
  })

  it('보유 주식은 소수 둘째 자리까지 보여준다', () => {
    renderResult({ ...result, items: [{ ...item('AAPL', 120000, 12), shares: 6.0992 }] })

    expect(screen.getByText('6.10주')).toBeInTheDocument()
  })

  it('현재 가치는 배당 현금까지 넣은 총자산이고 주식 · 현금으로 나눠 보여준다', () => {
    const withCash = { ...item('AAPL', 1845657, 184.57), currentValueKrw: 2815184, additionalData: { totalAssetKrw: 2845657 } }
    renderResult({ ...result, items: [withCash] })

    expect(screen.getByText('₩2,845,657')).toBeInTheDocument()
    expect(screen.getByText('주식 ₩2,815,184 · 현금 ₩30,473')).toBeInTheDocument()
  })

  it('고른 시작일이 휴장일이라 다른 날 샀으면 한 번 알린다', () => {
    render(<SymbolComparisonResult result={result} comparePurchaseDate="2023-01-01" compareSaleDate="" />)

    expect(screen.getAllByText('선택한 시작일(2023-01-01)은 휴장일이라 다음 거래일(2023-01-03) 종가로 샀습니다.')).toHaveLength(1)
  })

  it('환율은 소수 둘째 자리까지 보여준다', () => {
    renderResult({ ...result, items: [{ ...item('AAPL', 120000, 12), purchaseFxRate: 1261.87, currentFxRate: 1325.5 }] })

    expect(screen.getByText('₩1,261.87')).toBeInTheDocument()
    expect(screen.getByText('₩1,325.50')).toBeInTheDocument()
  })
})

describe('SymbolComparisonResult 값이 없을 때', () => {
  it('총 수익이 비어 있으면 - 와 중립 배경을 쓴다', () => {
    const missing = { ...result, items: [{ ...item('AAPL', 0, 0), totalReturnKrw: undefined }] }
    render(<SymbolComparisonResult result={missing} comparePurchaseDate="2023-01-03" compareSaleDate="" />)

    const label = screen.getAllByText('총 수익')[0]
    const box = label.parentElement
    expect(box).toHaveClass('bg-brand-bg')
    expect(box).toHaveTextContent('-')
  })

  it('매수일이 없으면 보유일은 - 로 둔다', () => {
    const missing = { ...result, items: [{ ...item('AAPL', 0, 0), purchaseDate: undefined }] }
    render(<SymbolComparisonResult result={missing} comparePurchaseDate="2023-01-03" compareSaleDate="" />)

    expect(screen.getByText('보유일').nextElementSibling).toHaveTextContent('-')
  })
})

describe('SymbolComparisonResult 최적 타이밍', () => {
  it('항목의 최적 매수 · 매도일과 그날 종가를 보여준다', () => {
    const optimal = { optimalBuyDate: '2023-01-05', optimalBuyPrice: 125.02, optimalSellDate: '2026-07-28', optimalSellPrice: 340.08 }
    renderResult({ ...result, items: [{ ...item('AAPL', 120000, 12), ...optimal }] })

    expect(screen.getByText('2023-01-05')).toBeInTheDocument()
    expect(screen.getByText('$125.02')).toBeInTheDocument()
    expect(screen.getByText('2026-07-28')).toBeInTheDocument()
    expect(screen.getByText('$340.08')).toBeInTheDocument()
  })

  it('최적 시점이 없으면 최적 타이밍 칸이 없다', () => {
    renderResult({ ...result, items: [item('AAPL', 120000, 12)] })

    expect(screen.queryByText('최적 타이밍')).not.toBeInTheDocument()
  })
})
