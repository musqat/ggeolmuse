import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { marketAdminApi } from '@services/adminApi'
import { useAdminMarket } from './useAdminMarket'

vi.mock('@services/adminApi', () => ({
  marketAdminApi: { getAllAssetSummaries: vi.fn() },
}))

const getAll = vi.mocked(marketAdminApi.getAllAssetSummaries)

// 훅을 열고 첫 목록 로딩이 끝날 때까지 기다린다
const renderLoaded = async () => {
  const hook = renderHook(() => useAdminMarket())
  await waitFor(() => expect(hook.result.current.loading).toBe(false))
  return hook
}

describe('useAdminMarket 목록 불러오기', () => {
  beforeEach(() => {
    getAll.mockReset()
    getAll.mockImplementation(async (page = 0, size = 20) => ({
      content: [], totalPages: 1, totalElements: 0, number: page, size,
    }) as never)
  })

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
})
