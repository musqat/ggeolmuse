import { describe, it, expect, vi } from 'vitest'
import type { ComponentProps } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import AddAssetModal from './AddAssetModal'

type Props = ComponentProps<typeof AddAssetModal>

const PREVIEW = {
  symbol: 'NVDA', name: 'NVIDIA', country: 'US', currency: 'USD', assetType: 'STOCK', exchange: 'NASDAQ',
  sector: 'Technology', industry: 'Semiconductors', marketCap: 3_000_000_000_000, description: '',
}

function renderModal(over: Partial<Props> = {}) {
  const props: Props = {
    isOpen: true, onClose: vi.fn(), preview: null, loading: false,
    onPreview: vi.fn(), onAdd: vi.fn(), onResetPreview: vi.fn(), ...over,
  }
  render(<AddAssetModal {...props} />)
  return props
}

describe('AddAssetModal', () => {
  it('신규 종목 추가 대화 상자로 열려 티커 칸에 포커스, Esc 로 닫는다', async () => {
    const props = renderModal()

    expect(screen.getByRole('dialog', { name: '신규 종목 추가' })).toHaveAttribute('aria-modal', 'true')
    expect(screen.getByRole('textbox', { name: '티커' })).toHaveFocus()
    await userEvent.keyboard('{Escape}')

    expect(props.onClose).toHaveBeenCalledOnce()
  })

  it('티커를 대문자로 받아 조회한다', async () => {
    const user = userEvent.setup()
    const props = renderModal()

    await user.type(screen.getByPlaceholderText('티커 입력 (예: NVDA)'), 'nvda{Enter}')

    expect(props.onPreview).toHaveBeenCalledWith('NVDA')
  })

  it('미리 본 회사가 있으면 추가를 부른다', async () => {
    const user = userEvent.setup()
    const props = renderModal({ preview: PREVIEW as never })

    expect(screen.getByText('NVIDIA')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '추가 및 데이터 수집 시작' }))

    expect(props.onAdd).toHaveBeenCalledTimes(1)
  })

  it('닫으면 미리보기를 비우고 닫는다', async () => {
    const user = userEvent.setup()
    const props = renderModal({ preview: PREVIEW as never })

    await user.click(screen.getByRole('button', { name: '닫기' }))

    expect(props.onResetPreview).toHaveBeenCalled()
    expect(props.onClose).toHaveBeenCalled()
  })
})
