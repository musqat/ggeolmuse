import { describe, it, expect } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import type { StrategyTransaction } from '../../../services/api'
import { HarvestTrades } from './HarvestTrades'
import { expectRowCards } from '../../../test/rowCards'

const transactions: StrategyTransaction[] = [
  { date: '2021-12-29', actualDate: '2021-12-30', trigger: '공제 채우기 매도', shares: -38.46153846, price: 150, amount: 0, fxRate: 1300, proceeds: 5769.23, realizedProfit: 1923.08 },
  { date: '2021-12-29', trigger: '공제 채우기 재매수', shares: 38.46153846, price: 150, amount: 0, fxRate: 1300, cashUsed: 5769.23 },
]

describe('HarvestTrades', () => {
  it('줄마다 체결일 · 이유 · 수량 · 가격 · 금액 · 실현 손익을 보이고 재매수 줄의 손익은 - 로 둠', () => {
    render(<HarvestTrades transactions={transactions} />)

    const rows = within(screen.getByRole('table')).getAllByRole('row').slice(1)
    expect(rows.map((row) => within(row).getAllByRole('cell').map((cell) => cell.textContent))).toEqual([
      ['2021-12-30', '공제 채우기 매도', '38.461538주', '$150.00', '$5,769.23', '+$1,923.08'],
      ['2021-12-29', '공제 채우기 재매수', '38.461538주', '$150.00', '$5,769.23', '-'],
    ])
  })

  it('휴대폰 너비에서는 줄마다 카드로 보이고 둘째 칸부터 머리줄 이름을 칸 이름으로 둠', () => {
    render(<HarvestTrades transactions={transactions} />)

    expectRowCards(screen.getByRole('table'))
  })
})
