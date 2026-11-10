import { describe, it, expect } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CapitalGainsTaxFold } from './CapitalGainsTaxFold'

const tax = {
  years: [
    { year: 2023, realizedGainKrw: -500000, deductionKrw: 0, taxKrw: 0, dueDate: '2024-05-01', paidKrw: 0 },
    { year: 2024, realizedGainKrw: 4000000, deductionKrw: 2500000, taxKrw: 330000, dueDate: '2025-05-01', paidKrw: 330000 },
    { year: 2025, realizedGainKrw: 3000000, deductionKrw: 2500000, taxKrw: 110000, dueDate: null, paidKrw: 0 },
  ],
  totalTaxKrw: 440000,
  paidTaxKrw: 330000,
  unpaidTaxKrw: 110000,
  cashKrw: 1200000,
  stockKrw: 8470000,
  afterTaxValueKrw: 9560000,
  afterTaxReturnPercent: 19.5,
}

const open = () => userEvent.click(screen.getByText('예상 양도세'))
const row = (name: RegExp) => screen.getByRole('row', { name })
const cells = (name: RegExp) => within(row(name)).getAllByRole('cell').map((cell) => cell.textContent)

describe('CapitalGainsTaxFold', () => {
  it('닫힌 채 제목 옆에 양도세 합 · 낸 양도세 · 세후 수익률을 보인다', () => {
    render(<CapitalGainsTaxFold tax={tax} returnBasis="누적 매수액 기준" />)

    expect(screen.getByText('예상 양도세').closest('details')).not.toHaveAttribute('open')
    expect(screen.getByText('· ₩440,000 · 낸 ₩330,000 · 세후 수익률 +19.50%')).toBeInTheDocument()
  })

  it('세후에 낸 양도세를 뺀 현금 · 주식 · 남은 양도세 · 세후 총 평가 · 세후 수익률을 보인다', async () => {
    render(<CapitalGainsTaxFold tax={tax} returnBasis="누적 매수액 기준" />)
    await open()

    expect(screen.getByText('현금(낸 양도세 뺌)').nextSibling).toHaveTextContent('₩1,200,000')
    expect(screen.getByText('주식').nextSibling).toHaveTextContent('₩8,470,000')
    expect(screen.getByText('남은 양도세').nextSibling).toHaveTextContent('-₩110,000')
    expect(screen.getByText('세후 총 평가').nextSibling).toHaveTextContent('₩9,560,000')
    expect(screen.getByText('세후 수익률 · 누적 매수액 기준').nextSibling).toHaveTextContent('+19.50%')
  })

  it('남은 양도세가 0 이면 그 줄이 없다', async () => {
    render(
      <CapitalGainsTaxFold
        tax={{ ...tax, years: tax.years.slice(0, 2), totalTaxKrw: 330000, unpaidTaxKrw: 0, afterTaxValueKrw: 9670000 }}
        returnBasis="투자금 기준"
      />,
    )
    await open()

    expect(screen.queryByText('남은 양도세')).toBeNull()
    expect(screen.getByText('세후 총 평가').nextSibling).toHaveTextContent('₩9,670,000')
  })

  it('연도별 표는 실현 이익 · 공제 · 양도세 · 낸 날 · 낸 금액이고 기간 안에 내지 않은 해는 기간 뒤, 세금이 0 인 해는 낸 날을 - 로 보인다', async () => {
    render(<CapitalGainsTaxFold tax={tax} returnBasis="누적 매수액 기준" />)
    await open()

    expect(screen.getAllByRole('columnheader').map((header) => header.textContent)).toEqual([
      '연도',
      '실현 이익',
      '공제',
      '양도세',
      '낸 날',
      '낸 금액',
    ])
    expect(cells(/2023년/)).toEqual(['-₩500,000', '₩0', '₩0', '-', '₩0'])
    expect(cells(/2024년/)).toEqual(['+₩4,000,000', '₩2,500,000', '₩330,000', '2025-05-01', '₩330,000'])
    expect(cells(/2025년/)).toEqual(['+₩3,000,000', '₩2,500,000', '₩110,000', '기간 뒤', '₩0'])
    expect(
      screen.getByText('판 해마다 원화 실현 이익에서 250만원까지 공제하고 22% · 다음 해 5월 첫 거래일에 현금에서 냄(모자라면 다음 매도 뒤)'),
    ).toBeInTheDocument()
  })

  it('연도별 표는 칸 안에서만 가로 스크롤한다', async () => {
    render(<CapitalGainsTaxFold tax={tax} returnBasis="누적 매수액 기준" />)
    await open()

    expect(screen.getByRole('table').parentElement).toHaveClass('overflow-x-auto')
  })

  it('판 거래가 없으면 연도 표 대신 양도세 없음 안내를 보인다', async () => {
    render(
      <CapitalGainsTaxFold
        tax={{
          years: [],
          totalTaxKrw: 0,
          paidTaxKrw: 0,
          unpaidTaxKrw: 0,
          cashKrw: 0,
          stockKrw: 10000000,
          afterTaxValueKrw: 10000000,
          afterTaxReturnPercent: 25,
        }}
        returnBasis="투자금 기준"
      />,
    )
    await open()

    expect(screen.getByText('판 거래가 없어 양도세 없음')).toBeInTheDocument()
    expect(screen.queryByRole('table')).toBeNull()
  })
})
