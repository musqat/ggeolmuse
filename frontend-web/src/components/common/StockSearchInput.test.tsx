import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render as baseRender, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactElement } from 'react'
import userEvent from '@testing-library/user-event'
import { stockApi } from '../../services/api'
import StockSearchInput from './StockSearchInput'

vi.mock('../../services/api', () => ({ stockApi: { searchSymbols: vi.fn() } }))

// 검색 결과를 담는 조회 캐시(테스트마다 새로)
function render(ui: ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return baseRender(ui, {
    wrapper: ({ children }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>,
  })
}

const SYMBOLS = ['AAPL', 'MSFT', 'NVDA']
const search = vi.mocked(stockApi.searchSymbols)

beforeEach(() => {
  search.mockReset()
  search.mockReturnValue(new Promise(() => {}) as never)
})

describe('StockSearchInput', () => {
  it('기본은 돋보기와 테두리가 있는 칸이고 고른 종목을 넘긴다', async () => {
    const onChange = vi.fn()
    const { container } = render(<StockSearchInput value="" onChange={onChange} supportedSymbols={SYMBOLS} placeholder="종목 검색" />)

    const input = screen.getByPlaceholderText('종목 검색')
    expect(input).toHaveClass('border-line-strong')
    expect(container.querySelector('svg')).not.toBeNull()
    await userEvent.type(input, 'ms')
    await userEvent.click(screen.getByText('MSFT'))

    expect(onChange).toHaveBeenCalledWith('MSFT')
  })

  it('inline 이면 돋보기 · 지우기 없이 밑줄 칸과 고른 종목의 회사 이름을 보인다', () => {
    const { container } = render(<StockSearchInput value="AAPL" onChange={vi.fn()} supportedSymbols={SYMBOLS} variant="inline" ariaLabel="종목" />)

    const input = screen.getByRole('combobox', { name: '종목' })
    expect(input).toHaveClass('input-inline')
    expect(input).toHaveValue('AAPL')
    expect(container.querySelector('svg')).toBeNull()
    expect(screen.queryByRole('button', { name: '검색어 지우기' })).toBeNull()
    expect(screen.getByText('Apple Inc.')).toBeInTheDocument()
  })

  it('inline 의 목록은 감싼 칸을 기준으로 펼치고 고른 종목을 넘긴다', async () => {
    const onChange = vi.fn()
    render(
      <div className="relative">
        <StockSearchInput value="" onChange={onChange} supportedSymbols={SYMBOLS} variant="inline" ariaLabel="종목 더하기" />
      </div>
    )

    const input = screen.getByRole('combobox', { name: '종목 더하기' })
    expect(input.parentElement).not.toHaveClass('relative')
    await userEvent.click(input)
    await userEvent.click(screen.getByText('NVDA'))

    expect(onChange).toHaveBeenCalledWith('NVDA')
  })

  it('↓ 로 목록을 열고 ↑ ↓ 로 고른 뒤 Enter 로 넘긴다', async () => {
    const onChange = vi.fn()
    render(<StockSearchInput value="AAPL" onChange={onChange} supportedSymbols={SYMBOLS} variant="inline" ariaLabel="종목" />)

    const input = screen.getByRole('combobox', { name: '종목' })
    input.focus()
    await userEvent.keyboard('{Escape}')
    expect(input).toHaveAttribute('aria-expanded', 'false')
    await userEvent.keyboard('{ArrowDown}')
    expect(input).toHaveAttribute('aria-expanded', 'true')
    await userEvent.keyboard('{ArrowDown}{ArrowDown}{ArrowUp}')
    expect(input).toHaveAttribute('aria-activedescendant', screen.getByRole('option', { name: /MSFT/ }).id)
    expect(screen.getByRole('option', { name: /MSFT/ })).toHaveAttribute('aria-selected', 'true')
    await userEvent.keyboard('{Enter}')

    expect(onChange).toHaveBeenCalledWith('MSFT')
    expect(screen.queryByRole('listbox')).toBeNull()
  })

  it('inline 에서 고르지 않고 나가면 입력을 고른 종목으로 되돌린다', async () => {
    render(
      <>
        <StockSearchInput value="AAPL" onChange={vi.fn()} supportedSymbols={SYMBOLS} variant="inline" ariaLabel="종목" />
        <button>밖</button>
      </>
    )

    const input = screen.getByRole('combobox', { name: '종목' })
    await userEvent.type(input, 'zz')
    await userEvent.click(screen.getByRole('button', { name: '밖' }))

    expect(input).toHaveValue('AAPL')
  })

  it('clearOnSelect 면 고른 뒤 입력을 비운다', async () => {
    const onChange = vi.fn()
    render(<StockSearchInput value="" onChange={onChange} supportedSymbols={SYMBOLS} variant="inline" ariaLabel="종목 더하기" clearOnSelect />)

    const input = screen.getByRole('combobox', { name: '종목 더하기' })
    await userEvent.type(input, 'ms{Enter}')

    expect(onChange).toHaveBeenCalledWith('MSFT')
    expect(input).toHaveValue('')
  })

  it('표에 없는 회사 이름은 서버 검색 결과로 찾고 고른 종목의 이름 표시', async () => {
    search.mockResolvedValue({ data: [{ symbol: 'KO', name: 'Coca-Cola Co' }] } as never)
    const onChange = vi.fn()
    const { rerender } = render(<StockSearchInput value="" onChange={onChange} supportedSymbols={['AAPL', 'KO']} variant="inline" ariaLabel="종목" />)

    await userEvent.type(screen.getByRole('combobox', { name: '종목' }), 'coca')
    await userEvent.click(await screen.findByText('KO'))
    rerender(<StockSearchInput value="KO" onChange={onChange} supportedSymbols={['AAPL', 'KO']} variant="inline" ariaLabel="종목" />)

    expect(onChange).toHaveBeenCalledWith('KO')
    expect(screen.getByText('Coca-Cola Co')).toBeInTheDocument()
  })

  it('서버 검색이 실패하면 받은 목록에서 찾은 결과와 안내 한 줄', async () => {
    search.mockRejectedValue(new Error('network'))
    render(<StockSearchInput value="" onChange={vi.fn()} supportedSymbols={SYMBOLS} placeholder="종목 검색" />)

    await userEvent.type(screen.getByPlaceholderText('종목 검색'), 'ms')

    expect(await screen.findByText('이름 검색을 못 해 티커로만 찾은 결과')).toBeInTheDocument()
    expect(screen.getByText('MSFT')).toBeInTheDocument()
  })

  it('invalid · describedBy 를 입력의 aria-invalid · aria-describedby 로 넘긴다', () => {
    render(<StockSearchInput value="ZZZ" onChange={vi.fn()} variant="inline" ariaLabel="종목" invalid describedBy="symbol-error" />)

    const input = screen.getByRole('combobox', { name: '종목' })
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAttribute('aria-describedby', 'symbol-error')
  })
})
