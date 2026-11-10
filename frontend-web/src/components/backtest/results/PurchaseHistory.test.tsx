import { describe, it, expect } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { PurchaseHistory } from './PurchaseHistory'
import { purchaseCount, sellCount } from './resultValues'
import type { SkippedRound, StrategyTransaction } from '../../../services/api'

const bought = (date: string, price: number, actualDate?: string): StrategyTransaction => ({
  date,
  actualDate: actualDate ?? date,
  price,
  shares: 100000 / 1300 / price,
  amount: 100000,
  fxRate: 1300,
  trigger: '월정액',
})

const transactions = [bought('2025-01-15', 236.1), bought('2025-02-15', 244.47, '2025-02-18')]
const skipped: SkippedRound[] = [
  { date: '2024-11-15', reason: 'NO_PRICE' },
  { date: '2024-12-15', reason: 'CALCULATION_ERROR' },
]

describe('PurchaseHistory', () => {
  it('매수 회차도 미매수 회차도 없으면 띄우지 않는다', () => {
    const { container } = render(<PurchaseHistory transactions={[]} skippedRounds={[]} />)

    expect(container).toBeEmptyDOMElement()
  })

  it('연도별 횟수를 보이고, 연도는 접혀 있다', () => {
    render(<PurchaseHistory transactions={transactions} skippedRounds={skipped} />)

    expect(screen.getByText('2024년')).toBeInTheDocument()
    expect(screen.getByText('미매수 2회')).toBeInTheDocument()
    expect(screen.getByText('2025년')).toBeInTheDocument()
    expect(screen.getByText('매수 2회')).toBeInTheDocument()
    expect(screen.getByText('$236.10')).not.toBeVisible()
  })

  it('연도를 펼치면 매수일 · 가격 · 수량 · 투자금을 보이고, 휴장일로 옮긴 날은 계획일을 같이 보인다', async () => {
    render(<PurchaseHistory transactions={transactions} skippedRounds={skipped} />)

    await userEvent.click(screen.getByText('2025년'))

    const row = screen.getByText('2025-01-15').closest('tr') as HTMLElement
    expect(row).toBeVisible()
    expect(within(row).getByText('월정액')).toBeInTheDocument()
    expect(within(row).getByText('$236.10')).toBeInTheDocument()
    expect(within(row).getByText('0.3258주')).toBeInTheDocument()
    expect(within(row).getByText('₩100,000')).toBeInTheDocument()
    expect(screen.getByText('2025-02-18')).toBeVisible()
    expect(screen.getByText('계획 2025-02-15')).toBeVisible()
  })

  it('연도 화살표는 그 연도가 열렸을 때만 돌고, 표의 둘째 칸부터 왼쪽 여백을 둔다', async () => {
    render(<PurchaseHistory transactions={transactions} skippedRounds={skipped} />)
    await userEvent.click(screen.getByText('2025년'))

    const year = screen.getByText('2025년').closest('details') as HTMLElement
    expect(year).toHaveClass('group/year')
    expect(year.querySelector('summary svg')).toHaveClass('group-open/year:rotate-180')
    const row = screen.getByText('2025-01-15').closest('tr') as HTMLElement
    for (const cells of [within(year).getAllByRole('columnheader'), within(row).getAllByRole('cell')]) {
      expect(cells.map((cell) => cell.classList.contains('pl-3'))).toEqual([false, true, true, true, true])
    }
  })

  it('미매수 회차는 같은 표에 이유를 보인다', async () => {
    render(<PurchaseHistory transactions={transactions} skippedRounds={skipped} />)

    await userEvent.click(screen.getByText('2024년'))

    expect(within(screen.getByText('2024-11-15').closest('tr') as HTMLElement).getByText('미매수 · 시세 없음')).toBeVisible()
    expect(within(screen.getByText('2024-12-15').closest('tr') as HTMLElement).getByText('미매수 · 계산 오류')).toBeVisible()
  })

  it('배당 재투자는 매수 횟수에서 빼고 연도 줄에 따로 센다', () => {
    const dividend: StrategyTransaction = { ...bought('2025-03-14', 250), amount: 0, shares: 0.01, trigger: '배당 재투자' }
    render(<PurchaseHistory transactions={[...transactions, dividend]} skippedRounds={[]} />)

    expect(purchaseCount([...transactions, dividend])).toBe(2)
    expect(screen.getByText('매수 2회 · 배당 재투자 1회')).toBeInTheDocument()
  })

  it('매도 줄은 판 수량 · 받은 돈 · 실현 손익을, 판 돈으로 산 줄은 쓴 판 돈을 보이고 연도 줄에 매도를 따로 센다', async () => {
    const sale: StrategyTransaction = {
      ...bought('2025-03-14', 250),
      shares: -0.5,
      amount: 0,
      trigger: '20.0%익절',
      proceeds: 125,
      realizedProfit: 20.5,
    }
    const rebuy: StrategyTransaction = { ...bought('2025-04-01', 200), amount: 0, trigger: '3.0%하락', cashUsed: 74.07 }
    render(<PurchaseHistory transactions={[...transactions, sale, rebuy]} skippedRounds={[]} />)

    expect(purchaseCount([...transactions, sale, rebuy])).toBe(3)
    expect(sellCount([...transactions, sale, rebuy])).toBe(1)
    expect(screen.getByText('매수 3회 · 매도 1회')).toBeInTheDocument()
    await userEvent.click(screen.getByText('2025년'))

    const saleRow = screen.getByText('2025-03-14').closest('tr') as HTMLElement
    expect(within(saleRow).getByText('20.0%익절')).toBeInTheDocument()
    expect(within(saleRow).getByText('0.5000주')).toBeInTheDocument()
    expect(within(saleRow).getByText('$125.00 받음')).toBeInTheDocument()
    expect(within(saleRow).getByText('손익 +$20.50')).toBeInTheDocument()
    const rebuyRow = screen.getByText('2025-04-01').closest('tr') as HTMLElement
    expect(within(rebuyRow).getByText('판 돈 $74.07')).toBeInTheDocument()
    expect(within(rebuyRow).queryByText('₩0')).not.toBeInTheDocument()
  })

  it('양도세 납부 줄은 금액 칸에 낸 달러만 보이고 매수 · 매도 횟수에서 빼고 연도 줄에 따로 센다', async () => {
    const payment: StrategyTransaction = {
      date: '2025-05-01',
      actualDate: '2025-05-01',
      price: 0,
      shares: 0,
      amount: 0,
      fee: 0,
      fxRate: 1330,
      trigger: '양도세 납부',
      taxPaid: 248.12,
    }
    render(<PurchaseHistory transactions={[...transactions, payment]} skippedRounds={[]} />)

    expect(purchaseCount([...transactions, payment])).toBe(2)
    expect(sellCount([...transactions, payment])).toBe(0)
    expect(screen.getByText('매수 2회 · 양도세 납부 1회')).toBeInTheDocument()
    await userEvent.click(screen.getByText('2025년'))

    const paymentRow = screen.getByText('2025-05-01').closest('tr') as HTMLElement
    expect(within(paymentRow).getAllByRole('cell').map((cell) => cell.textContent)).toEqual([
      '2025-05-01',
      '양도세 납부',
      '-',
      '-',
      '-$248.12',
    ])
  })
})
