import { describe, it, expect, vi } from 'vitest'
import type { ComponentProps } from 'react'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import AssetListSection from './AssetListSection'

const ASSETS = [
  { symbol: 'AAPL', name: 'Apple Inc.', country: 'US', currency: 'USD', assetType: 'STOCK', currentPrice: 190.5, marketCap: 3e12, latestDataDate: '2026-10-23' },
  { symbol: 'SPY', name: 'SPDR S&P 500', country: 'US', currency: 'USD', assetType: 'ETF', currentPrice: 580, latestDataDate: '2026-10-23' },
]

type Props = ComponentProps<typeof AssetListSection>

function renderList(over: Partial<Props> = {}) {
  const props: Props = {
    assets: ASSETS, loading: false, listActive: true,
    onRefresh: vi.fn(), onDelete: vi.fn(), onRestore: vi.fn(), onListActiveChange: vi.fn(), onBulkDelete: vi.fn(),
    onUpdatePrice: vi.fn(), onUpdateMarketCap: vi.fn(), onUpdateName: vi.fn().mockResolvedValue(true),
    onUpdateAllPrices: vi.fn(), onUpdateAllMarketCaps: vi.fn(), onCollectNewSymbols: vi.fn(),
    currentPage: 0, pageSize: 20, totalPages: 3, totalElements: 50, sortBy: 'marketCap', sortDirection: 'desc',
    onPageChange: vi.fn(), onPageSizeChange: vi.fn(), onSortChange: vi.fn(),
    selected: new Set<string>(), onToggleOne: vi.fn(), onToggleAll: vi.fn(),
    ...over,
  }
  render(<AssetListSection {...props} />)
  return props
}

const rowOf = (symbol: string) => within(screen.getByText(symbol).closest('tr')!)

describe('AssetListSection', () => {
  it('상장 목록은 가격 · 시총 · 삭제를 그 종목으로 부른다', async () => {
    const user = userEvent.setup()
    const props = renderList()

    await user.click(rowOf('AAPL').getByRole('button', { name: '가격' }))
    await user.click(rowOf('AAPL').getByRole('button', { name: '시총' }))
    await user.click(rowOf('AAPL').getByRole('button', { name: '삭제' }))

    expect(props.onUpdatePrice).toHaveBeenCalledWith('AAPL')
    expect(props.onUpdateMarketCap).toHaveBeenCalledWith('AAPL')
    expect(props.onDelete).toHaveBeenCalledWith('AAPL')
  })

  it('상장폐지 목록은 삭제 대신 복구를 부른다', async () => {
    const user = userEvent.setup()
    const props = renderList({ listActive: false })

    expect(rowOf('AAPL').queryByRole('button', { name: '삭제' })).toBeNull()
    await user.click(rowOf('AAPL').getByRole('button', { name: '복구' }))

    expect(props.onRestore).toHaveBeenCalledWith('AAPL')
  })

  it('머리글을 누르면 오름차순, 같은 열을 다시 누르면 방향이 바뀐다', async () => {
    const user = userEvent.setup()
    const props = renderList()

    await user.click(screen.getByRole('columnheader', { name: '가격' }))
    await user.click(screen.getByRole('columnheader', { name: '시가총액' }))

    expect(props.onSortChange).toHaveBeenNthCalledWith(1, 'currentPrice', 'asc')
    expect(props.onSortChange).toHaveBeenNthCalledWith(2, 'marketCap', 'asc')
  })

  it('전체 고르기는 이 페이지 종목을 넘긴다', async () => {
    const user = userEvent.setup()
    const props = renderList()

    await user.click(screen.getAllByRole('checkbox')[0])

    expect(props.onToggleAll).toHaveBeenCalledWith(['AAPL', 'SPY'])
  })

  it('이름 수정은 Enter 면 저장, Escape 면 취소', async () => {
    const user = userEvent.setup()
    const props = renderList()

    await user.click(rowOf('AAPL').getByRole('button', { name: '이름 수정' }))
    const input = rowOf('AAPL').getByDisplayValue('Apple Inc.')
    await user.clear(input)
    await user.type(input, '애플{Enter}')
    expect(props.onUpdateName).toHaveBeenCalledWith('AAPL', '애플')

    await user.click(rowOf('SPY').getByRole('button', { name: '이름 수정' }))
    await user.type(rowOf('SPY').getByDisplayValue('SPDR S&P 500'), '!{Escape}')
    expect(props.onUpdateName).toHaveBeenCalledTimes(1)
    expect(rowOf('SPY').queryByDisplayValue('SPDR S&P 500!')).toBeNull()
  })

  it('다음 페이지를 누르면 그 페이지를 부른다', async () => {
    const user = userEvent.setup()
    const props = renderList()

    await user.click(screen.getByRole('button', { name: '다음 페이지' }))

    expect(props.onPageChange).toHaveBeenCalledWith(1)
  })
})
