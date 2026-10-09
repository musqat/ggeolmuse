import { describe, it, expect, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { TradeHistoryList } from './TradeHistoryList'
import type { TransactionHistoryItem } from '../../../services/api'

const items = [
  { type: 'BUY', tradeId: 1, accountId: 1, symbol: 'AAPL', quantity: 2, price: 227.52, totalAmount: 455.49, fee: 0.45, date: '2025-01-02', executedAt: '2025-01-02T10:00:00', status: 'COMPLETED' },
  { type: 'DIVIDEND', tradeId: 1, symbol: 'AAPL', totalAmount: 0.44, grossAmount: 0.52, taxAmount: 0.08, dividendPerShare: 0.26, shares: 2, date: '2026-08-14', executedAt: '2026-08-15T00:00:00' },
  { type: 'SELL', tradeId: 2, accountId: 2, symbol: 'NVDA', quantity: 3, price: 182.4, totalAmount: 546.65, fee: 0.55, date: '2026-10-08', executedAt: '2026-10-09T15:12:00', status: 'COMPLETED' },
  { type: 'BUY', tradeId: 3, accountId: 1, symbol: 'MSFT', quantity: 1, price: 512.1, totalAmount: 512.61, fee: 0.51, date: '2026-08-14', executedAt: '2026-08-20T10:03:00', status: 'CANCELLED', cancelReason: '잔액 부족' },
] as TransactionHistoryItem[]

const accounts = [
  { accountId: 1, accountName: '기본 계좌' },
  { accountId: 2, accountName: '두번째' },
]

const renderList = () =>
  render(<TradeHistoryList transactions={items} accounts={accounts} onRefresh={vi.fn()} refreshing={false} />)

describe('TradeHistoryList', () => {
  it('거래일 최신순으로 날짜마다 묶고 매도도 그 사이에 넣는다', () => {
    renderList()

    const headers = screen.getAllByTestId('history-day').map((el) => el.textContent)
    expect(headers).toEqual(['2026. 10. 08. (목)', '2026. 08. 14. (금)', '2025. 01. 02. (목)'])
  })

  it('나간 돈은 -, 들어온 돈은 + 를 붙이고 배당에는 어느 매수분인지 보인다', () => {
    renderList()

    expect(screen.getByText('+$546.65')).toBeInTheDocument()
    expect(screen.getByText('-$455.49')).toBeInTheDocument()
    // 요약 칸에도 배당 합계 +$0.44 가 있어 배당 줄 안에서 찾는다
    const dividendRow = screen.getAllByTestId('history-row').find((row) => row.dataset.type === 'DIVIDEND')!
    expect(within(dividendRow).getByText('+$0.44')).toBeInTheDocument()
    expect(screen.getByText('2025.01.02 매수분')).toBeInTheDocument()
    // 상세 줄은 조각 span 으로 나뉘어 p 전체 글자로 본다
    expect(
      screen.getByText((_, el) => el?.tagName === 'P' && el.textContent === '3주 × $182.40 · 수수료 $0.55')
    ).toBeInTheDocument()
    expect(screen.getByText('주문 10/09 15:12')).toBeInTheDocument()
  })

  it('요약은 취소된 거래를 빼고 센다', () => {
    renderList()
    const summary = screen.getByTestId('history-summary')

    expect(within(summary).getByText('$455.49')).toBeInTheDocument()
    expect(within(summary).getByText('취소 1건 제외')).toBeInTheDocument()
  })

  it('유형 필터를 누르면 그 유형만 남는다', async () => {
    renderList()

    await userEvent.click(screen.getByRole('button', { name: '매도' }))

    expect(screen.getAllByTestId('history-row')).toHaveLength(1)
    expect(screen.getByText('NVDA')).toBeInTheDocument()
  })

  it('계좌를 고르면 그 계좌 매수에서 나온 배당도 남는다', async () => {
    renderList()

    await userEvent.selectOptions(screen.getByLabelText('계좌'), '1')

    const rows = screen.getAllByTestId('history-row').map((el) => el.dataset.type)
    // 08-14 은 주문 시각이 늦은 MSFT 매수가 배당보다 위
    expect(rows).toEqual(['BUY', 'DIVIDEND', 'BUY'])
  })
})
