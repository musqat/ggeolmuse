import { describe, it, expect } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { PortfolioTransaction } from '../../../../services/api'
import { PortfolioTrades } from './PortfolioTrades'

const transactions: PortfolioTransaction[] = [
  { symbol: 'SPY', reason: 'BUY', date: '2020-01-02', actualDate: '2020-01-02', trigger: '한 번에 매수', shares: 20.12345678, price: 324.87, amount: 6000000, fxRate: 1200, fee: 0 },
  { symbol: 'QQQ', reason: 'BUY', date: '2020-01-02', trigger: '한 번에 매수', shares: 10, price: 300, amount: 3000000, fxRate: 1200, cashUsed: 500 },
  { symbol: 'SPY', reason: 'DIVIDEND_REINVESTMENT', date: '2020-03-20', actualDate: '2020-03-23', trigger: '배당 재투자', shares: 0.1, price: 250, amount: 0, fxRate: 1250, cashUsed: 25 },
  { reason: 'TAX_PAYMENT', date: '2024-05-01', actualDate: '2024-05-01', trigger: '양도세 납부', shares: 0, price: 0, amount: 0, fxRate: 1400, taxPaid: 812.34 },
  { symbol: 'SPY', reason: 'PERIOD_END_SALE', date: '2024-12-31', actualDate: '2024-12-31', trigger: '기간 끝 매도', shares: -20.22345678, price: 590, amount: 0, fxRate: 1450, proceeds: 11900.5, realizedProfit: 6875.5 },
  { symbol: 'QQQ', reason: 'PERIOD_END_SALE', date: '2024-12-31', actualDate: '2024-12-31', trigger: '기간 끝 매도', shares: -10, price: 299, amount: 0, fxRate: 1450, proceeds: 2990, realizedProfit: -10 },
]

const rows = () => within(screen.getByRole('table')).getAllByRole('row').slice(1)
const cells = (row: HTMLElement) => within(row).getAllByRole('cell').map((cell) => cell.textContent)
const options = (name: string) =>
  within(screen.getByRole('combobox', { name })).getAllByRole('option').map((option) => option.textContent)

describe('PortfolioTrades', () => {
  it('줄마다 날짜 · 종목 · 이유(거래 글) · 수량 · 금액 · 실현 손익을 거래일 순서로 보이고, 양도세 납부는 수량 없이 낸 달러를 뺀다', () => {
    render(<PortfolioTrades transactions={transactions} symbols={['SPY', 'QQQ']} />)

    expect(rows().map(cells)).toEqual([
      ['2020-01-02', 'SPY', '한 번에 매수', '20.123457주', '$5,000.00', '-'],
      ['2020-01-02', 'QQQ', '한 번에 매수', '10주', '$3,000.00', '-'],
      ['2020-03-23계획 2020-03-20', 'SPY', '배당 재투자', '0.1주', '$25.00', '-'],
      ['2024-05-01', '-', '양도세 납부', '-', '-$812.34', '-'],
      ['2024-12-31', 'SPY', '기간 끝 매도', '20.223457주', '$11,900.50', '+$6,875.50'],
      ['2024-12-31', 'QQQ', '기간 끝 매도', '10주', '$2,990.00', '-$10.00'],
    ])
    expect(within(rows()[4]).getByText('+$6,875.50')).toHaveClass('text-gain')
    expect(within(rows()[5]).getByText('-$10.00')).toHaveClass('text-loss')
    expect(screen.getByText('6건')).toBeInTheDocument()
  })

  it('고를 것은 거래에 있는 종목(요청 순서) · 이유만 두고, 종목 · 이유를 고르면 둘 다 맞는 거래만 보인다', async () => {
    render(<PortfolioTrades transactions={transactions} symbols={['SPY', 'QQQ', 'IAU']} />)

    expect(options('종목')).toEqual(['모든 종목', 'SPY', 'QQQ'])
    expect(options('이유')).toEqual(['모든 이유', '한 번에 매수', '기간 끝 매도', '배당 재투자', '양도세 납부'])

    await userEvent.selectOptions(screen.getByRole('combobox', { name: '종목' }), 'QQQ')
    expect(rows().map((row) => cells(row)[2])).toEqual(['한 번에 매수', '기간 끝 매도'])
    await userEvent.selectOptions(screen.getByRole('combobox', { name: '이유' }), '기간 끝 매도')
    expect(rows().map((row) => cells(row)[4])).toEqual(['$2,990.00'])
    expect(screen.getByText('1건')).toBeInTheDocument()

    await userEvent.selectOptions(screen.getByRole('combobox', { name: '이유' }), '양도세 납부')
    expect(screen.getByText('고른 종목 · 이유의 거래가 없습니다')).toBeInTheDocument()
    await userEvent.selectOptions(screen.getByRole('combobox', { name: '종목' }), '모든 종목')
    expect(rows().map((row) => cells(row)[2])).toEqual(['양도세 납부'])
  })

  it('리밸런싱 매도는 일부 수량 · 받은 돈 · 실현 손익, 리밸런싱 매수는 쓴 현금으로 보이고 이유 고르기는 리밸런싱 매수 · 리밸런싱 매도 순서', async () => {
    const rebalanced: PortfolioTransaction[] = [
      transactions[0],
      transactions[1],
      { symbol: 'SPY', reason: 'REBALANCE_SELL', date: '2021-01-04', actualDate: '2021-01-04', trigger: '리밸런싱 매도', shares: -2.5, price: 370, amount: 0, fxRate: 1100, proceeds: 922.69, realizedProfit: 110.5 },
      { symbol: 'QQQ', reason: 'REBALANCE_BUY', date: '2021-01-04', actualDate: '2021-01-04', trigger: '리밸런싱 매수', shares: 2.97, price: 310, amount: 0, fxRate: 1100, cashUsed: 922.69 },
    ]
    render(<PortfolioTrades transactions={rebalanced} symbols={['SPY', 'QQQ']} />)

    expect(rows().slice(2).map(cells)).toEqual([
      ['2021-01-04', 'SPY', '리밸런싱 매도', '2.5주', '$922.69', '+$110.50'],
      ['2021-01-04', 'QQQ', '리밸런싱 매수', '2.97주', '$922.69', '-'],
    ])
    expect(options('이유')).toEqual(['모든 이유', '한 번에 매수', '리밸런싱 매수', '리밸런싱 매도'])

    await userEvent.selectOptions(screen.getByRole('combobox', { name: '이유' }), '리밸런싱 매도')
    expect(rows().map((row) => cells(row)[1])).toEqual(['SPY'])
  })

  it('익절 · 손절은 전량 수량 · 받은 돈 · 실현 손익과 거래 글로 보이고 이유 고르기는 리밸런싱 다음 익절 · 손절 순서', async () => {
    const exits: PortfolioTransaction[] = [
      transactions[0],
      transactions[1],
      { symbol: 'QQQ', reason: 'STOP_LOSS', date: '2020-03-16', actualDate: '2020-03-16', trigger: '10.0%손절', shares: -10, price: 270.75, amount: 0, fxRate: 1230, proceeds: 2700.98, realizedProfit: -299.02 },
      { symbol: 'SPY', reason: 'REBALANCE_SELL', date: '2021-01-04', actualDate: '2021-01-04', trigger: '리밸런싱 매도', shares: -2.5, price: 370, amount: 0, fxRate: 1100, proceeds: 922.69, realizedProfit: 110.5 },
      { symbol: 'SPY', reason: 'TAKE_PROFIT', date: '2021-08-10', actualDate: '2021-08-10', trigger: '35.0%익절', shares: -17.62345678, price: 440.1, amount: 0, fxRate: 1160, proceeds: 7736.94, realizedProfit: 2005.12 },
    ]
    render(<PortfolioTrades transactions={exits} symbols={['SPY', 'QQQ']} />)

    expect(rows().slice(2).map(cells)).toEqual([
      ['2020-03-16', 'QQQ', '10.0%손절', '10주', '$2,700.98', '-$299.02'],
      ['2021-01-04', 'SPY', '리밸런싱 매도', '2.5주', '$922.69', '+$110.50'],
      ['2021-08-10', 'SPY', '35.0%익절', '17.623457주', '$7,736.94', '+$2,005.12'],
    ])
    expect(options('이유')).toEqual(['모든 이유', '한 번에 매수', '리밸런싱 매도', '익절', '손절'])

    await userEvent.selectOptions(screen.getByRole('combobox', { name: '이유' }), '손절')
    expect(rows().map((row) => cells(row)[2])).toEqual(['10.0%손절'])
    await userEvent.selectOptions(screen.getByRole('combobox', { name: '이유' }), '익절')
    expect(rows().map((row) => cells(row)[1])).toEqual(['SPY'])
  })
})
