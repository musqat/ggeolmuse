import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useAdminMarket } from '@hooks/useAdminMarket'
import AdminMarket from './AdminMarket'

// 화면이 훅과 하위 칸을 잇는 것만(둘은 각자 테스트)
vi.mock('@hooks/useAdminMarket', () => ({ useAdminMarket: vi.fn() }))
vi.mock('@components/admin/CandleRefreshSection', () => ({ default: () => null }))
vi.mock('@components/admin/FxCollectSection', () => ({ default: () => null }))
vi.mock('@components/admin/AssetListSection', () => ({
  default: (props: { assets: { symbol: string }[]; searchMode: boolean }) => (
    <ul aria-label={props.searchMode ? '검색 결과 목록' : '전체 목록'}>
      {props.assets.map((a) => <li key={a.symbol}>{a.symbol}</li>)}
    </ul>
  ),
}))
vi.mock('@components/admin/AddAssetModal', () => ({
  default: (props: { isOpen: boolean; onAdd: () => Promise<void> }) =>
    props.isOpen ? <button onClick={props.onAdd}>추가 확인</button> : null,
}))

type Market = ReturnType<typeof useAdminMarket>

function market(overrides: Partial<Market> = {}): Market {
  return {
    searchKeyword: '',
    setSearchKeyword: vi.fn(),
    searchResults: [],
    searchActive: false,
    clearSearch: vi.fn(),
    assets: [{ symbol: 'AAPL' }, { symbol: 'MSFT' }],
    loading: false,
    error: null,
    handleSearch: vi.fn(),
    loadAssets: vi.fn(),
    handleAddAsset: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  } as unknown as Market
}

beforeEach(() => vi.clearAllMocks())

describe('AdminMarket', () => {
  it('검색하지 않았으면 전체 목록을 보인다', () => {
    vi.mocked(useAdminMarket).mockReturnValue(market())
    render(<AdminMarket />)

    expect(screen.getByRole('list', { name: '전체 목록' })).toHaveTextContent('AAPLMSFT')
  })

  it('검색 중이면 검색 결과와 그 수를 보이고, 없으면 추가 안내를 붙인다', () => {
    vi.mocked(useAdminMarket).mockReturnValue(
      market({ searchActive: true, searchKeyword: 'zzz', searchResults: [] })
    )
    render(<AdminMarket />)

    expect(screen.getByRole('list', { name: '검색 결과 목록' })).toBeEmptyDOMElement()
    expect(screen.getByText(/"zzz" 검색 결과 0개/)).toHaveTextContent('보유 종목에 없습니다')
  })

  it('검색 칸에서 Enter 를 누르면 검색하고, 해제 버튼으로 검색을 끈다', async () => {
    const m = market({ searchActive: true, searchKeyword: 'app', searchResults: [{ symbol: 'AAPL' }] as Market['searchResults'] })
    vi.mocked(useAdminMarket).mockReturnValue(m)
    render(<AdminMarket />)

    await userEvent.type(screen.getByPlaceholderText(/보유 종목 검색/), '{Enter}')
    await userEvent.click(screen.getByTitle('검색 해제'))

    expect(m.handleSearch).toHaveBeenCalledTimes(1)
    expect(m.clearSearch).toHaveBeenCalledTimes(1)
  })

  it('신규 종목 추가 창에서 추가하면 저장하고 창을 닫는다', async () => {
    const m = market()
    vi.mocked(useAdminMarket).mockReturnValue(m)
    render(<AdminMarket />)

    await userEvent.click(screen.getByRole('button', { name: /신규 종목 추가/ }))
    await userEvent.click(screen.getByRole('button', { name: '추가 확인' }))

    expect(m.handleAddAsset).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('button', { name: '추가 확인' })).not.toBeInTheDocument()
  })

  it('오류가 있으면 위에 보인다', () => {
    vi.mocked(useAdminMarket).mockReturnValue(market({ error: '목록을 불러오지 못했습니다' }))
    render(<AdminMarket />)

    expect(screen.getByText('목록을 불러오지 못했습니다')).toBeInTheDocument()
  })
})
