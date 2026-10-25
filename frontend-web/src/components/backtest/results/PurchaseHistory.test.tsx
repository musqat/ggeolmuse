import { describe, it, expect } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { PurchaseHistory } from './PurchaseHistory'
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

  it('전체 매수 · 미매수 횟수와 연도별 횟수를 보이고, 연도는 접혀 있다', () => {
    render(<PurchaseHistory transactions={transactions} skippedRounds={skipped} />)

    expect(screen.getByText('매수 2회 · 미매수 2회')).toBeInTheDocument()
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

  it('미매수 회차는 같은 표에 이유를 보인다', async () => {
    render(<PurchaseHistory transactions={transactions} skippedRounds={skipped} />)

    await userEvent.click(screen.getByText('2024년'))

    expect(within(screen.getByText('2024-11-15').closest('tr') as HTMLElement).getByText('미매수 · 시세 없음')).toBeVisible()
    expect(within(screen.getByText('2024-12-15').closest('tr') as HTMLElement).getByText('미매수 · 계산 오류')).toBeVisible()
  })
})
