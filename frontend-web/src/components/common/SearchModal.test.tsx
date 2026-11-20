import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { ComponentProps, ReactElement } from 'react'
import { render as baseRender, screen, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { stockApi } from '../../services/api'
import SearchModal from './SearchModal'

vi.mock('../../services/api', () => ({ stockApi: { searchSymbols: vi.fn() } }))

// 검색 결과를 담는 조회 캐시(테스트마다 새로)
function render(ui: ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return baseRender(ui, {
    wrapper: ({ children }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>,
  })
}

const search = vi.mocked(stockApi.searchSymbols)

beforeEach(() => {
  search.mockReset()
  search.mockReturnValue(new Promise(() => {}) as never)
})

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
  it('종목 검색 대화 상자로 열려 검색 칸에 포커스', () => {
    renderModal()

    expect(screen.getByRole('dialog', { name: '종목 검색' })).toHaveAttribute('aria-modal', 'true')
    expect(searchInput()).toHaveFocus()
  })

  it('결과는 목록 상자의 항목이고 화살표로 고른 항목을 검색 칸이 알린다', async () => {
    const user = userEvent.setup()
    renderModal({ supportedSymbols: ['AAPL', 'MSFT'] })
    const combobox = screen.getByRole('combobox', { name: '종목명 또는 티커' })
    const options = within(screen.getByRole('listbox', { name: '검색 결과' })).getAllByRole('option')

    expect(options[0]).toHaveAttribute('aria-selected', 'true')
    await user.keyboard('{ArrowDown}')

    expect(options[1]).toHaveAttribute('aria-selected', 'true')
    expect(combobox).toHaveAttribute('aria-activedescendant', options[1].id)
  })

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

  it('표에 없는 회사 이름은 서버 검색 결과로 찾음', async () => {
    search.mockResolvedValue({ data: [{ symbol: 'KO', name: 'Coca-Cola Co' }] } as never)
    const user = userEvent.setup()
    renderModal({ supportedSymbols: ['AAPL', 'KO'] })

    await user.type(searchInput(), 'coca')

    expect(await screen.findByText('Coca-Cola Co')).toBeInTheDocument()
    expect(search).toHaveBeenCalledWith('coca', 10, expect.any(AbortSignal))
  })

  it('서버 검색이 실패하면 안내 한 줄', async () => {
    search.mockRejectedValue(new Error('network'))
    const user = userEvent.setup()
    renderModal()

    await user.type(searchInput(), 'v')

    expect(await screen.findByText('이름 검색을 못 해 티커로만 찾은 결과')).toBeInTheDocument()
    expect(shownSymbols()[0]).toBe('V')
  })

  it('화살표로 고르고 Enter 면 그 종목 차트로 가고 닫는다', async () => {
    const user = userEvent.setup()
    const { onClose } = renderModal({ supportedSymbols: ['AAPL', 'MSFT', 'NVDA'] })

    await user.click(searchInput())
    await user.keyboard('{ArrowDown}{Enter}')

    expect(onClose).toHaveBeenCalled()
    expect(screen.getByText('/charts/MSFT')).toBeInTheDocument()
  })

  it('검색 결과가 서버 결과로 줄어들면 고른 줄을 범위 안으로 되돌려 Enter 로 남은 종목을 고른다', async () => {
    search.mockResolvedValue({ data: [{ symbol: 'V', name: 'Visa Inc' }] } as never)
    const user = userEvent.setup()
    const onSelectStock = vi.fn()
    renderModal({ onSelectStock })

    await user.type(searchInput(), 'v')
    await user.keyboard('{ArrowDown}{ArrowDown}')
    await screen.findByText('Visa Inc')
    await user.keyboard('{Enter}')

    expect(onSelectStock).toHaveBeenCalledWith('V')
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

  it('찾는 것이 없으면 없다고 보이고 검색 칸은 없는 목록을 가리키지 않는다', async () => {
    const user = userEvent.setup()
    renderModal()
    const combobox = screen.getByRole('combobox', { name: '종목명 또는 티커' })
    expect(combobox).toHaveAttribute('aria-controls', screen.getByRole('listbox', { name: '검색 결과' }).id)

    await user.type(searchInput(), 'zzz')

    expect(screen.getByText('"zzz"에 대한 검색 결과가 없습니다.')).toBeInTheDocument()
    expect(combobox).toHaveAttribute('aria-expanded', 'false')
    expect(combobox).not.toHaveAttribute('aria-controls')
  })
})
