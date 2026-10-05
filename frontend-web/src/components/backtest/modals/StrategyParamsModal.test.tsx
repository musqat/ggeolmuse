import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StrategyParamsModal } from './StrategyParamsModal'

function renderModal(type: 'SIMPLE' | 'DCA' | 'CONDITIONAL_PURCHASE') {
  const props = {
    modalStrategyType: type,
    strategyParameters: {},
    setStrategyParameters: vi.fn(),
    strategyStartDate: '2023-01-01',
    strategyInvestment: '1000000',
    handleSaveStrategyParams: vi.fn(),
    setShowStrategyModal: vi.fn(),
    setModalStrategyType: vi.fn(),
  }
  render(<StrategyParamsModal {...props} />)
  return props
}

describe('StrategyParamsModal', () => {
  it('단순 매수는 시작일에 산다고 안내한다', () => {
    renderModal('SIMPLE')

    expect(screen.getByText('시작일(2023-01-01)')).toBeInTheDocument()
  })

  it('취소하면 모달을 닫고 선택을 비운다', async () => {
    const props = renderModal('DCA')

    await userEvent.click(screen.getByRole('button', { name: '취소' }))

    expect(props.setShowStrategyModal).toHaveBeenCalledWith(false)
    expect(props.setModalStrategyType).toHaveBeenCalledWith(null)
  })
})
