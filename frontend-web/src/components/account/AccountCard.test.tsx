import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { AccountCard } from './AccountCard'
import type { AccountSummary, AccountBalance } from '../../services/api'

const account: AccountSummary = {
  accountId: 1,
  accountName: '메인 계좌',
  accountNumber: '1001-2024-0001',
  commissionRate: 0.0025,
  usdBalance: 1000,
  krwBalance: 1300000,
}

function renderCard(slippageRate: number) {
  const balance: AccountBalance = {
    accountName: '메인 계좌',
    accountNumber: '1001-2024-0001',
    balanceKrw: 1300000,
    balanceUsd: 1000,
    currentValueKrw: 2600000,
    myAvgExchangeRate: 1300,
    currentExchangeRate: 1320,
    commissionRate: 0.0025,
    slippageRate,
  }

  render(
    <MemoryRouter>
      <AccountCard
        account={account}
        balance={balance}
        formatBalance={(amount, currency) => `${currency} ${amount}`}
        onDeposit={vi.fn()}
        onExchange={vi.fn()}
        onDelete={vi.fn()}
      />
    </MemoryRouter>
  )
}

describe('AccountCard', () => {
  it('슬리피지율을 퍼센트로 보여준다', () => {
    renderCard(0.001)

    expect(screen.getByText('슬리피지')).toBeInTheDocument()
    expect(screen.getByText('0.10%')).toBeInTheDocument()
  })

  it('슬리피지율이 0 이면 0.00% 로 보여준다', () => {
    renderCard(0)

    expect(screen.getByText('0.00%')).toBeInTheDocument()
  })
})
