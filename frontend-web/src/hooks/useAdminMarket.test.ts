import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { AxiosError, type AxiosResponse } from 'axios'
import { marketAdminApi } from '@services/adminApi'
import { useAdminMarket } from './useAdminMarket'

vi.mock('@services/adminApi', () => ({
  marketAdminApi: {
    getAllAssetSummaries: vi.fn(),
    searchAssets: vi.fn(),
    previewAsset: vi.fn(),
    createAsset: vi.fn(),
    updateAssetName: vi.fn(),
    deleteAsset: vi.fn(),
    restoreAsset: vi.fn(),
    bulkDeleteAssets: vi.fn(),
    updateAssetPrice: vi.fn(),
    updateAssetMarketCap: vi.fn(),
    updateAllPrices: vi.fn(),
    updateAllMarketCaps: vi.fn(),
    collectNewSymbols: vi.fn(),
  },
}))

const api = vi.mocked(marketAdminApi)
const getAll = api.getAllAssetSummaries
const asset = (symbol: string, name = symbol) => ({ symbol, name }) as never
const serverError = (detail: string) =>
  new AxiosError('fail', 'ERR', undefined, undefined, { status: 400, data: { detail }, statusText: '', headers: {}, config: {} } as AxiosResponse)

let confirmSpy: ReturnType<typeof vi.spyOn>
let alertSpy: ReturnType<typeof vi.spyOn>

beforeEach(() => {
  vi.clearAllMocks()
  getAll.mockImplementation(async (page = 0, size = 20) => ({
    content: [], totalPages: 1, totalElements: 0, number: page, size,
  }) as never)
  confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
  alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {})
  vi.spyOn(console, 'error').mockImplementation(() => {})
})
afterEach(() => vi.restoreAllMocks())

// 훅을 열고 첫 목록 로딩이 끝날 때까지 기다린다
const renderLoaded = async () => {
  const hook = renderHook(() => useAdminMarket())
  await waitFor(() => expect(hook.result.current.loading).toBe(false))
  return hook
}

describe('useAdminMarket 목록 불러오기', () => {
  it('처음 열 때 목록을 한 번 받는다', async () => {
    await renderLoaded()

    expect(getAll).toHaveBeenCalledTimes(1)
    expect(getAll).toHaveBeenCalledWith(0, 20, 'symbol', 'asc', true)
  })

  it('페이지 크기를 바꾸면 새 크기로 첫 페이지를 받는다', async () => {
    const { result } = await renderLoaded()
    getAll.mockClear()

    act(() => result.current.handlePageSizeChange(50))

    await waitFor(() => expect(getAll).toHaveBeenCalledTimes(1))
    expect(getAll).toHaveBeenCalledWith(0, 50, 'symbol', 'asc', true)
    expect(result.current.pageSize).toBe(50)
  })

  it('정렬을 바꾸면 그 정렬로 첫 페이지를 받는다', async () => {
    const { result } = await renderLoaded()
    getAll.mockClear()

    act(() => result.current.handleSortChange('latestPrice', 'desc'))

    await waitFor(() => expect(getAll).toHaveBeenCalledTimes(1))
    expect(getAll).toHaveBeenCalledWith(0, 20, 'latestPrice', 'desc', true)
  })

  it('받은 페이지 정보를 그대로 둔다', async () => {
    getAll.mockResolvedValue({ content: [asset('AAPL')], totalPages: 3, totalElements: 41, number: 0, size: 20 } as never)

    const { result } = await renderLoaded()

    expect(result.current.assets).toEqual([asset('AAPL')])
    expect(result.current.totalPages).toBe(3)
    expect(result.current.totalElements).toBe(41)
  })

  it('페이지를 바꾸면 그 페이지를 받고 고른 것을 비운다', async () => {
    const { result } = await renderLoaded()
    act(() => result.current.toggleOne('AAPL'))
    getAll.mockClear()

    act(() => result.current.handlePageChange(2))

    await waitFor(() => expect(getAll).toHaveBeenCalledWith(2, 20, 'symbol', 'asc', true))
    expect(result.current.selected.size).toBe(0)
  })

  it('상장폐지 목록으로 바꾸면 active=false 로 첫 페이지를 받는다', async () => {
    const { result } = await renderLoaded()
    getAll.mockClear()

    act(() => result.current.handleListActiveChange(false))

    await waitFor(() => expect(getAll).toHaveBeenCalledWith(0, 20, 'symbol', 'asc', false))
    expect(result.current.listActive).toBe(false)
  })

  it('목록을 못 받으면 오류 문구를 둔다', async () => {
    getAll.mockRejectedValue(new Error('down'))

    const { result } = await renderLoaded()

    expect(result.current.error).toBe('심볼 목록 조회에 실패했습니다.')
  })
})

describe('useAdminMarket 검색 · 추가', () => {
  it('검색어로 등록된 종목을 찾고, 빈 검색어면 검색을 푼다', async () => {
    api.searchAssets.mockResolvedValue([asset('AAPL')])
    const { result } = await renderLoaded()

    act(() => result.current.setSearchKeyword('aap'))
    await act(() => result.current.handleSearch())

    expect(api.searchAssets).toHaveBeenCalledWith('aap')
    expect(result.current.searchActive).toBe(true)
    expect(result.current.searchResults).toEqual([asset('AAPL')])

    act(() => result.current.setSearchKeyword('  '))
    await act(() => result.current.handleSearch())

    expect(result.current.searchActive).toBe(false)
    expect(result.current.searchResults).toEqual([])
    expect(api.searchAssets).toHaveBeenCalledTimes(1)
  })

  it('미리 본 종목을 1년치 데이터 수집과 함께 추가하고 목록을 다시 받는다', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-11T10:00:00+09:00'))
    api.previewAsset.mockResolvedValue({ symbol: 'NVDA', name: 'NVIDIA', country: 'US', currency: 'USD', assetType: 'STOCK' } as never)
    api.createAsset.mockResolvedValue(undefined)
    const { result } = await renderLoaded()

    await act(() => result.current.handlePreview('NVDA'))
    getAll.mockClear()
    await act(() => result.current.handleAddAsset())
    vi.useRealTimers()

    expect(api.createAsset).toHaveBeenCalledWith({
      symbol: 'NVDA', name: 'NVIDIA', country: 'US', currency: 'USD', assetType: 'STOCK',
      collectData: true, fromDate: '2025-10-11', toDate: '2026-10-11', includeDividends: true,
    })
    expect(result.current.preview).toBeNull()
    await waitFor(() => expect(getAll).toHaveBeenCalled())
  })

  it('미리보기가 실패하면 오류 문구를 둔다', async () => {
    api.previewAsset.mockRejectedValue(new Error('nope'))
    const { result } = await renderLoaded()

    await act(() => result.current.handlePreview('ZZZZ'))

    expect(result.current.error).toBe('미리보기 조회에 실패했습니다.')
    expect(result.current.preview).toBeNull()
  })
})

describe('useAdminMarket 종목 하나 작업', () => {
  // [이름, 부를 함수, API, 성공 alert, 목록을 다시 받나, 실패 문구]
  const cases = [
    ['가격 업데이트', 'handleUpdatePrice', 'updateAssetPrice', '가격 데이터가 업데이트되었습니다.', true],
    ['시가총액 업데이트', 'handleUpdateMarketCap', 'updateAssetMarketCap', '시가총액이 업데이트되었습니다.', true],
    ['삭제', 'handleDeleteAsset', 'deleteAsset', '심볼이 삭제되었습니다.', true],
    ['복구', 'handleRestoreAsset', 'restoreAsset', null, true],
  ] as const

  it.each(cases)('%s: 확인하면 API 를 부르고 목록을 다시 받는다', async (_name, handler, method, alertText) => {
    api[method].mockResolvedValue(undefined)
    const { result } = await renderLoaded()
    getAll.mockClear()

    await act(() => result.current[handler]('AAPL'))

    expect(api[method]).toHaveBeenCalledWith('AAPL')
    if (alertText) expect(alertSpy).toHaveBeenCalledWith(alertText)
    await waitFor(() => expect(getAll).toHaveBeenCalled())
  })

  it.each(cases)('%s: 확인을 취소하면 아무것도 안 한다', async (_name, handler, method) => {
    confirmSpy.mockReturnValue(false)
    const { result } = await renderLoaded()

    await act(() => result.current[handler]('AAPL'))

    expect(api[method]).not.toHaveBeenCalled()
  })

  it('가격 업데이트가 실패하면 서버 문구를 보여준다', async () => {
    api.updateAssetPrice.mockRejectedValue(serverError('야후 응답이 비었습니다'))
    const { result } = await renderLoaded()

    await act(() => result.current.handleUpdatePrice('AAPL'))

    expect(result.current.error).toBe('야후 응답이 비었습니다')
    expect(result.current.loading).toBe(false)
  })
})

describe('useAdminMarket 이름 수정', () => {
  it('앞뒤 공백을 지워 저장하고 검색 결과에도 바로 반영한다', async () => {
    api.searchAssets.mockResolvedValue([asset('AAPL', 'Apple'), asset('MSFT', 'Microsoft')])
    api.updateAssetName.mockResolvedValue(undefined)
    const { result } = await renderLoaded()
    act(() => result.current.setSearchKeyword('a'))
    await act(() => result.current.handleSearch())

    let ok: boolean | undefined
    await act(async () => { ok = await result.current.handleUpdateName('AAPL', '  Apple Inc.  ') })

    expect(ok).toBe(true)
    expect(api.updateAssetName).toHaveBeenCalledWith('AAPL', 'Apple Inc.')
    expect(result.current.searchResults).toEqual([asset('AAPL', 'Apple Inc.'), asset('MSFT', 'Microsoft')])
  })

  it('빈 이름은 보내지 않고 false', async () => {
    const { result } = await renderLoaded()

    let ok: boolean | undefined
    await act(async () => { ok = await result.current.handleUpdateName('AAPL', '   ') })

    expect(ok).toBe(false)
    expect(api.updateAssetName).not.toHaveBeenCalled()
    expect(result.current.error).toBe('이름은 비워둘 수 없습니다.')
  })

  it('서버가 거절하면 서버 문구와 false', async () => {
    api.updateAssetName.mockRejectedValue(serverError('이름이 너무 깁니다'))
    const { result } = await renderLoaded()

    let ok: boolean | undefined
    await act(async () => { ok = await result.current.handleUpdateName('AAPL', 'x') })

    expect(ok).toBe(false)
    expect(result.current.error).toBe('이름이 너무 깁니다')
  })
})

describe('useAdminMarket 고르기 · 일괄 삭제', () => {
  it('하나씩 고르고 풀며, 전체 고르기는 다 골랐으면 비운다', async () => {
    const { result } = await renderLoaded()

    act(() => result.current.toggleOne('AAPL'))
    act(() => result.current.toggleOne('MSFT'))
    act(() => result.current.toggleOne('AAPL'))
    expect([...result.current.selected]).toEqual(['MSFT'])

    act(() => result.current.toggleAll(['AAPL', 'MSFT']))
    expect([...result.current.selected].sort()).toEqual(['AAPL', 'MSFT'])
    act(() => result.current.toggleAll(['AAPL', 'MSFT']))
    expect(result.current.selected.size).toBe(0)
  })

  it('고른 종목을 한 번에 비활성화하고 서버 문구를 띄운 뒤 고른 것을 비운다', async () => {
    api.bulkDeleteAssets.mockResolvedValue({ requested: 2, deleted: 2, message: '2개 비활성화' })
    const { result } = await renderLoaded()
    act(() => result.current.toggleAll(['AAPL', 'MSFT']))

    await act(() => result.current.handleBulkDelete())

    expect(api.bulkDeleteAssets).toHaveBeenCalledWith(['AAPL', 'MSFT'])
    expect(alertSpy).toHaveBeenCalledWith('2개 비활성화')
    expect(result.current.selected.size).toBe(0)
  })

  it('고른 것이 없으면 묻지도 않는다', async () => {
    const { result } = await renderLoaded()

    await act(() => result.current.handleBulkDelete())

    expect(confirmSpy).not.toHaveBeenCalled()
    expect(api.bulkDeleteAssets).not.toHaveBeenCalled()
  })
})

describe('useAdminMarket 전체 작업', () => {
  it.each([
    ['handleUpdateAllPrices', 'updateAllPrices', '전체 가격 데이터 업데이트가 백그라운드에서 시작되었습니다.'],
    ['handleUpdateAllMarketCaps', 'updateAllMarketCaps', '전체 시가총액 업데이트가 백그라운드에서 시작되었습니다.'],
    ['handleCollectNewSymbols', 'collectNewSymbols', '신규 종목 수집이 백그라운드에서 시작되었습니다. 잠시 뒤 목록을 새로고침해 주세요.'],
  ] as const)('%s: 확인하면 백그라운드 작업을 시작한다', async (handler, method, alertText) => {
    api[method].mockResolvedValue(undefined)
    const { result } = await renderLoaded()

    await act(() => result.current[handler]())

    expect(api[method]).toHaveBeenCalledTimes(1)
    expect(alertSpy).toHaveBeenCalledWith(alertText)
  })

  it('전체 가격 업데이트가 실패하면 서버 문구를 보여준다', async () => {
    api.updateAllPrices.mockRejectedValue(serverError('이미 돌고 있습니다'))
    const { result } = await renderLoaded()

    await act(() => result.current.handleUpdateAllPrices())

    expect(result.current.error).toBe('이미 돌고 있습니다')
  })
})
