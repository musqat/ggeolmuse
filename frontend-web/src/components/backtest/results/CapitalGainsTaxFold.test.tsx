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

  it('연도별 표는 실현 이익 · 공제 · 양도세 · 납부일 · 낸 금액이고 납부일이 기간 뒤인 해는 기간 뒤, 세금이 0 인 해는 납부일을 - 로 보인다', async () => {
    render(<CapitalGainsTaxFold tax={tax} returnBasis="누적 매수액 기준" />)
    await open()

    expect(screen.getAllByRole('columnheader').map((header) => header.textContent)).toEqual([
      '연도',
      '실현 이익',
      '공제',
      '양도세',
      '납부일',
      '낸 금액',
    ])
    expect(cells(/2023년/)).toEqual(['-₩500,000', '₩0', '₩0', '-', '₩0'])
    expect(cells(/2024년/)).toEqual(['+₩4,000,000', '₩2,500,000', '₩330,000', '2025-05-01', '₩330,000'])
    expect(cells(/2025년/)).toEqual(['+₩3,000,000', '₩2,500,000', '₩110,000', '기간 뒤', '₩0'])
    expect(
      screen.getByText('결제된 해마다 원화 실현 이익에서 250만원까지 공제하고 22%(연말 마지막 거래일 무렵 판 몫은 다음 해) · 다음 해 5월 첫 거래일에 현금에서 냄(모자라면 현금이 생길 때)'),
    ).toBeInTheDocument()
  })

  it('현금이 모자랄 때 팔 수 있는 결과는 설명 줄에 정한 대로 주식을 팔거나 현금이 생길 때 낸다고 적는다', async () => {
    render(<CapitalGainsTaxFold tax={tax} returnBasis="누적 매수액 기준" sellsForTax />)
    await open()

    expect(
      screen.getByText('결제된 해마다 원화 실현 이익에서 250만원까지 공제하고 22%(연말 마지막 거래일 무렵 판 몫은 다음 해) · 다음 해 5월 첫 거래일에 현금에서 냄(모자라면 정한 대로 주식을 팔거나 현금이 생길 때)'),
    ).toBeInTheDocument()
  })

  it('납부일이 기간 안인데 다 내지 못한 해는 낸 금액 아래에 남은 세금을 보인다', async () => {
    const unpaid = [
      { year: 2024, realizedGainKrw: 4000000, deductionKrw: 2500000, taxKrw: 330000, dueDate: '2025-05-01', paidKrw: 220000 },
      { year: 2025, realizedGainKrw: 3000000, deductionKrw: 2500000, taxKrw: 110000, dueDate: '2026-05-01', paidKrw: 0 },
    ]
    render(<CapitalGainsTaxFold tax={{ ...tax, years: unpaid, paidTaxKrw: 220000, unpaidTaxKrw: 220000 }} returnBasis="누적 매수액 기준" />)
    await open()

    expect(cells(/2024년/)).toEqual(['+₩4,000,000', '₩2,500,000', '₩330,000', '2025-05-01', '₩220,000₩110,000 남음'])
    expect(cells(/2025년/)).toEqual(['+₩3,000,000', '₩2,500,000', '₩110,000', '2026-05-01', '₩0₩110,000 남음'])
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

  it('연말 정리 비교가 있으면 닫힌 채 제목 아래 작은 한 줄로 기간 끝에 한 번에 판 양도세 · 연말 정리 양도세 · 차이를 보인다', () => {
    const compared = (savedKrw: number) => ({
      ...tax,
      harvestComparison: { sellAtEndTaxKrw: 4026000, harvestTaxKrw: 4026000 - savedKrw, savedKrw },
    })
    const { rerender } = render(<CapitalGainsTaxFold tax={compared(1650000)} returnBasis="투자금 기준" />)

    const line = screen.getByText('기간 끝에 한 번에 팔았을 때 양도세 ₩4,026,000 · 연말 정리로 ₩2,376,000 · ₩1,650,000 아낌')
    expect(line).toHaveClass('text-xs')
    expect(line.closest('summary')).not.toBeNull()
    rerender(<CapitalGainsTaxFold tax={compared(-12000)} returnBasis="투자금 기준" />)
    expect(screen.getByText('기간 끝에 한 번에 팔았을 때 양도세 ₩4,026,000 · 연말 정리로 ₩4,038,000 · ₩12,000 더 냄')).toBeInTheDocument()
    rerender(<CapitalGainsTaxFold tax={compared(0)} returnBasis="투자금 기준" />)
    expect(screen.getByText('기간 끝에 한 번에 팔았을 때 양도세 ₩4,026,000 · 연말 정리로 ₩4,026,000 · 차이 없음')).toBeInTheDocument()
  })

  it('연말 정리 비교가 없으면 그 줄이 없다', () => {
    render(<CapitalGainsTaxFold tax={{ ...tax, harvestComparison: null }} returnBasis="투자금 기준" />)

    expect(screen.queryByText(/기간 끝에 한 번에 팔았을 때/)).toBeNull()
  })
})
