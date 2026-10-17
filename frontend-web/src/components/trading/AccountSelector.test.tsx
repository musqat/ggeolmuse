import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import AccountSelector from './AccountSelector'

describe('AccountSelector', () => {
  it('잔액이 마이너스면 부호를 달러 기호 앞에 보인다', () => {
    render(
      <AccountSelector
        accounts={[{ accountId: 1, accountName: '기본 계좌', usdBalance: -12.3 }]}
        selectedAccountId={1}
        onAccountChange={vi.fn()}
      />
    )

    expect(screen.getByRole('option', { name: '기본 계좌 · -$12.30' })).toBeInTheDocument()
  })
})
