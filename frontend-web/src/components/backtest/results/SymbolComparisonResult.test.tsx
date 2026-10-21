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

describe('SymbolComparisonResult', () => {
  it('비교한 종목을 모두 보여준다', () => {
    render(
      <SymbolComparisonResult
        result={result}
        comparePurchaseDate="2023-01-03"
        compareSaleDate=""
        symbolOptimalPoints={{}}
        setSymbolOptimalPoints={vi.fn()}
      />
    )

    expect(screen.getAllByText('AAPL').length).toBeGreaterThan(0)
    expect(screen.getAllByText('MSFT').length).toBeGreaterThan(0)
  })
})

describe('SymbolComparisonResult 값이 없을 때', () => {
  it('총 수익이 비어 있으면 - 와 중립 배경을 쓴다', () => {
    const missing = { ...result, items: [{ ...item('AAPL', 0, 0), totalReturnKrw: undefined }] }
    render(
      <SymbolComparisonResult
        result={missing}
        comparePurchaseDate="2023-01-03"
        compareSaleDate=""
        symbolOptimalPoints={{}}
        setSymbolOptimalPoints={vi.fn()}
      />
    )

    const label = screen.getAllByText('총 수익')[0]
    const box = label.parentElement
    expect(box).toHaveClass('bg-brand-bg')
    expect(box).toHaveTextContent('-')
  })
})

describe('SymbolComparisonResult 최적 타이밍', () => {
  const chartPoint = { buyDate: '2023-03-01', sellDate: '2023-11-01', minPrice: 145.5, sellPrice: 190.25 }

  it('서버 값이 없으면 차트가 계산한 매수 · 매도 시점을 서버 값과 같은 칸으로 보여준다', () => {
    const one = { ...result, items: [item('AAPL', 120000, 12)] }
    render(
      <SymbolComparisonResult
        result={one}
        comparePurchaseDate="2023-01-03"
        compareSaleDate=""
        symbolOptimalPoints={{ AAPL: chartPoint }}
        setSymbolOptimalPoints={vi.fn()}
      />
    )

    expect(screen.getByText('2023-03-01')).toBeInTheDocument()
    expect(screen.getByText('$145.50')).toBeInTheDocument()
    expect(screen.getByText('2023-11-01')).toBeInTheDocument()
    expect(screen.getByText('최적 매도가')).toBeInTheDocument()
    expect(screen.getByText('$190.25')).toBeInTheDocument()
    expect(screen.queryByText('최적 평가금액')).not.toBeInTheDocument()
  })

  it('항목 이름이 종목 코드와 달라도 종목 코드로 차트 값을 찾는다', () => {
    const named = { ...result, items: [{ ...item('AAPL', 120000, 12), name: '애플' }] }
    render(
      <SymbolComparisonResult
        result={named}
        comparePurchaseDate="2023-01-03"
        compareSaleDate=""
        symbolOptimalPoints={{ AAPL: chartPoint }}
        setSymbolOptimalPoints={vi.fn()}
      />
    )

    expect(screen.getByText('2023-03-01')).toBeInTheDocument()
  })
})
