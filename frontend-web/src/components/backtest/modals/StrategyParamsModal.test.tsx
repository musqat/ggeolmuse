import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StrategyParamsModal } from './StrategyParamsModal'

function renderModal(type: 'SIMPLE' | 'DCA' | 'CONDITIONAL_PURCHASE', params: Record<string, string> = {}) {
  const props = {
    type,
    params,
    onChange: vi.fn(),
    common: { startDate: '2023-01-01', endDate: '2023-12-31', investment: '1000000' },
    onSave: vi.fn(),
    onCancel: vi.fn(),
  }
  render(<StrategyParamsModal {...props} />)
  return props
}

describe('StrategyParamsModal', () => {
  it('단순 매수는 시작일에 산다고 안내한다', () => {
    renderModal('SIMPLE')

    expect(screen.getByText('시작일(2023-01-01)')).toBeInTheDocument()
  })

  it('그 전략의 칸에서 바꾼 값을 키와 함께 넘긴다', async () => {
    const props = renderModal('DCA', { amountPerPurchase: '100000', cadence: 'MONTHLY', months: '3', firstPurchaseDate: '' })

    expect(screen.getByRole('heading', { name: '전략 설정: 적립식' })).toBeInTheDocument()
    await userEvent.selectOptions(screen.getByRole('combobox', { name: '투자 주기' }), 'DAILY')

    expect(props.onChange).toHaveBeenCalledWith('cadence', 'DAILY')
  })

  it('저장 · 취소 단추를 누르면 onSave · onCancel 로 넘긴다', async () => {
    const props = renderModal('DCA')

    await userEvent.click(screen.getByRole('button', { name: '저장' }))
    await userEvent.click(screen.getByRole('button', { name: '취소' }))

    expect(props.onSave).toHaveBeenCalledTimes(1)
    expect(props.onCancel).toHaveBeenCalledTimes(1)
  })
})
