import { describe, it, expect } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CapitalGainsTaxFold } from './CapitalGainsTaxFold'

const tax = {
  years: [
    { year: 2023, realizedGainKrw: -500000, deductionKrw: 0, taxKrw: 0 },
    { year: 2024, realizedGainKrw: 4000000, deductionKrw: 2500000, taxKrw: 330000 },
  ],
  totalTaxKrw: 330000,
  afterTaxValueKrw: 9670000,
  afterTaxReturnPercent: 20.88,
}

const value = { totalAssetKrw: 10000000, currentValueKrw: 6000000 }
const open = () => userEvent.click(screen.getByText('예상 양도세'))
const row = (name: RegExp) => screen.getByRole('row', { name })

describe('CapitalGainsTaxFold', () => {
  it('닫힌 채 제목 옆에 양도세 합과 세후 수익률을 보인다', () => {
    render(<CapitalGainsTaxFold tax={tax} value={value} returnBasis="누적 매수액 기준" />)

    expect(screen.getByText('예상 양도세').closest('details')).not.toHaveAttribute('open')
    expect(screen.getByText('· ₩330,000 · 세후 수익률 +20.88%')).toBeInTheDocument()
  })

  it('끝까지 보유는 평가 가치에서 양도세를 뺀 세후 평가와 연도별 실현 이익 · 공제 · 세금을 보인다', async () => {
    render(<CapitalGainsTaxFold tax={tax} value={value} returnBasis="누적 매수액 기준" />)
    await open()

    expect(screen.getByText('평가 가치').nextSibling).toHaveTextContent('₩10,000,000')
    expect(screen.getByText('양도세 합').nextSibling).toHaveTextContent('-₩330,000')
    expect(screen.getByText('세후 평가').nextSibling).toHaveTextContent('₩9,670,000')
    expect(screen.getByText('세후 수익률 · 누적 매수액 기준').nextSibling).toHaveTextContent('+20.88%')
    expect(screen.queryByText('현금(양도세 뺌)')).toBeNull()
    expect(within(row(/2023년/)).getAllByRole('cell').map((cell) => cell.textContent)).toEqual(['-₩500,000', '₩0', '₩0'])
    expect(within(row(/2024년/)).getAllByRole('cell').map((cell) => cell.textContent)).toEqual(['+₩4,000,000', '₩2,500,000', '₩330,000'])
  })

  it('마지막 거래일에 팔았으면 현금(양도세 뺌) · 주식 · 총 평가로 나눠 보인다', async () => {
    render(
      <CapitalGainsTaxFold
        tax={tax}
        value={{ totalAssetKrw: 10000000, currentValueKrw: 0 }}
        sellAtEnd
        returnBasis="투자금 기준"
      />,
    )
    await open()

    expect(screen.getByText('현금(양도세 뺌)').nextSibling).toHaveTextContent('₩9,670,000')
    expect(screen.getByText('주식').nextSibling).toHaveTextContent('₩0')
    expect(screen.getByText('총 평가').nextSibling).toHaveTextContent('₩9,670,000')
    expect(screen.queryByText('세후 평가')).toBeNull()
  })

  it('판 거래가 없으면 연도 표 대신 양도세 없음 안내를 보인다', async () => {
    render(
      <CapitalGainsTaxFold
        tax={{ years: [], totalTaxKrw: 0, afterTaxValueKrw: 10000000, afterTaxReturnPercent: 25 }}
        value={value}
        returnBasis="투자금 기준"
      />,
    )
    await open()

    expect(screen.getByText('판 거래가 없어 양도세 없음')).toBeInTheDocument()
    expect(screen.queryByRole('table')).toBeNull()
  })
})
