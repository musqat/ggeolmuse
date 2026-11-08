import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import StockSearchInput from './StockSearchInput'

const SYMBOLS = ['AAPL', 'MSFT', 'NVDA']

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

  it('invalid · describedBy 를 입력의 aria-invalid · aria-describedby 로 넘긴다', () => {
    render(<StockSearchInput value="ZZZ" onChange={vi.fn()} variant="inline" ariaLabel="종목" invalid describedBy="symbol-error" />)

    const input = screen.getByRole('combobox', { name: '종목' })
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAttribute('aria-describedby', 'symbol-error')
  })
})
