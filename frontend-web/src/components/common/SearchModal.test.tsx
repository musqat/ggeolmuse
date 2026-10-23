import { describe, it, expect, vi } from 'vitest'
import type { ComponentProps } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import SearchModal from './SearchModal'

const TWELVE = ['AAPL', 'MSFT', 'NVDA', 'GOOGL', 'TSLA', 'AMZN', 'META', 'AVGO', 'JPM', 'LLY', 'UNH', 'XOM']

// 이동한 경로
function Where() {
  return <p>{useLocation().pathname}</p>
}

function renderModal(props: Partial<ComponentProps<typeof SearchModal>> = {}) {
  const onClose = vi.fn()
  render(
    <MemoryRouter>
      <Routes>
        <Route path="/" element={<SearchModal isOpen onClose={onClose} supportedSymbols={['AVGO', 'VZ', 'V']} {...props} />} />
        <Route path="*" element={<Where />} />
      </Routes>
    </MemoryRouter>
  )
  return { onClose }
}

const searchInput = () => screen.getByPlaceholderText('종목명 또는 티커 검색...')

// 결과에 보이는 티커를 위에서부터
const shownSymbols = () => screen.getAllByText(/^(AVGO|VZ|V)$/).map((el) => el.textContent)

describe('SearchModal', () => {
  it('정확히 맞는 티커를 먼저 보인다', async () => {
    const user = userEvent.setup()
    renderModal()

    await user.type(searchInput(), 'v')

    expect(shownSymbols()[0]).toBe('V')
  })

  it('검색어가 없으면 처음 열 개를 보인다', () => {
    renderModal({ supportedSymbols: TWELVE })

    expect(screen.getAllByText('Enter ↵')).toHaveLength(10)
  })

  it('회사 이름으로도 찾는다', async () => {
    const user = userEvent.setup()
    renderModal({ supportedSymbols: ['AAPL', 'MSFT'] })

    await user.type(searchInput(), 'micro')

    expect(screen.getByText('MSFT')).toBeInTheDocument()
    expect(screen.queryByText('AAPL')).toBeNull()
  })

  it('화살표로 고르고 Enter 면 그 종목 차트로 가고 닫는다', async () => {
    const user = userEvent.setup()
    const { onClose } = renderModal({ supportedSymbols: ['AAPL', 'MSFT', 'NVDA'] })

    await user.click(searchInput())
    await user.keyboard('{ArrowDown}{Enter}')

    expect(onClose).toHaveBeenCalled()
    expect(screen.getByText('/charts/MSFT')).toBeInTheDocument()
  })

  it('onSelectStock 이 있으면 이동 대신 넘긴다', async () => {
    const user = userEvent.setup()
    const onSelectStock = vi.fn()
    const { onClose } = renderModal({ supportedSymbols: ['AAPL', 'MSFT'], onSelectStock })

    await user.click(screen.getByText('MSFT'))

    expect(onSelectStock).toHaveBeenCalledWith('MSFT')
    expect(onClose).toHaveBeenCalled()
    expect(screen.queryByText('/charts/MSFT')).toBeNull()
  })

  it('Esc 면 닫는다', async () => {
    const user = userEvent.setup()
    const { onClose } = renderModal()

    await user.keyboard('{Escape}')

    expect(onClose).toHaveBeenCalled()
  })

  it('찾는 것이 없으면 없다고 보인다', async () => {
    const user = userEvent.setup()
    renderModal()

    await user.type(searchInput(), 'zzz')

    expect(screen.getByText('"zzz"에 대한 검색 결과가 없습니다.')).toBeInTheDocument()
  })
})
