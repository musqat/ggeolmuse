import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { stockApi } from '../services/api'
import { SYMBOL_SEARCH_DELAY_MS, SYMBOL_SEARCH_MAX_LENGTH, useSymbolSearch } from './useSymbolSearch'

vi.mock('../services/api', () => ({ stockApi: { searchSymbols: vi.fn() } }))

const search = vi.mocked(stockApi.searchSymbols)
const LOCAL = ['AAPL', 'MSFT', 'KO', 'COST']

// 테스트에서 직접 끝내는 응답
function deferred() {
  let resolve!: (value: unknown) => void
  const promise = new Promise((res) => {
    resolve = res
  })
  return { promise, resolve }
}

const reply = (...matches: { symbol: string; name: string }[]) => ({ data: matches })

beforeEach(() => {
  vi.useFakeTimers()
  search.mockReset()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('useSymbolSearch', () => {
  it('입력이 멈추고 250ms 가 지나기 전에는 서버 검색 호출 없음', async () => {
    search.mockReturnValue(new Promise(() => {}) as never)
    const { rerender } = renderHook(({ term }) => useSymbolSearch(term, LOCAL, 20), { initialProps: { term: 'c' } })

    rerender({ term: 'co' })
    await act(async () => {
      vi.advanceTimersByTime(SYMBOL_SEARCH_DELAY_MS - 1)
    })
    expect(search).not.toHaveBeenCalled()

    await act(async () => {
      vi.advanceTimersByTime(1)
    })
    expect(search).toHaveBeenCalledTimes(1)
    expect(search).toHaveBeenCalledWith('co', 20, expect.any(AbortSignal))
  })

  it('늦게 온 앞 검색어 응답은 버리고 지금 검색어 결과', async () => {
    const first = deferred()
    const second = deferred()
    search.mockReturnValueOnce(first.promise as never).mockReturnValueOnce(second.promise as never)
    const { result, rerender } = renderHook(({ term }) => useSymbolSearch(term, LOCAL, 20), { initialProps: { term: 'co' } })

    await act(async () => {
      vi.advanceTimersByTime(SYMBOL_SEARCH_DELAY_MS)
    })
    rerender({ term: 'coca' })
    await act(async () => {
      vi.advanceTimersByTime(SYMBOL_SEARCH_DELAY_MS)
    })
    await act(async () => {
      second.resolve(reply({ symbol: 'KO', name: 'Coca-Cola Co' }))
    })
    await act(async () => {
      first.resolve(reply({ symbol: 'COST', name: 'Costco Wholesale' }))
    })

    expect(result.current.matches).toEqual([{ symbol: 'KO', name: 'Coca-Cola Co' }])
    expect(result.current.failed).toBe(false)
  })

  it('서버 응답 전에는 받은 종목 목록에서 찾은 결과', () => {
    search.mockReturnValue(new Promise(() => {}) as never)
    const { result } = renderHook(() => useSymbolSearch('ms', LOCAL, 20))

    expect(result.current.matches.map((match) => match.symbol)).toEqual(['MSFT'])
  })

  it('받은 종목 목록에 없는 서버 결과는 뺌', async () => {
    search.mockResolvedValue(reply({ symbol: 'KO', name: 'Coca-Cola Co' }, { symbol: 'KOF', name: 'Coca-Cola FEMSA' }) as never)
    const { result } = renderHook(() => useSymbolSearch('coca', LOCAL, 20))

    await act(async () => {
      vi.advanceTimersByTime(SYMBOL_SEARCH_DELAY_MS)
    })

    expect(result.current.matches.map((match) => match.symbol)).toEqual(['KO'])
  })

  it('서버 검색이 실패하면 받은 종목 목록에서 찾은 결과와 실패 표시', async () => {
    search.mockRejectedValue(new Error('network'))
    const { result } = renderHook(() => useSymbolSearch('ms', LOCAL, 20))

    await act(async () => {
      vi.advanceTimersByTime(SYMBOL_SEARCH_DELAY_MS)
    })

    expect(result.current.failed).toBe(true)
    expect(result.current.matches.map((match) => match.symbol)).toEqual(['MSFT'])
  })

  it('검색어가 바뀌거나 지웠다 다시 쳐도 앞 검색어의 실패 표시 없음', async () => {
    search.mockRejectedValueOnce(new Error('network')).mockReturnValue(new Promise(() => {}) as never)
    const { result, rerender } = renderHook(({ term }) => useSymbolSearch(term, LOCAL, 20), { initialProps: { term: 'ms' } })

    await act(async () => {
      vi.advanceTimersByTime(SYMBOL_SEARCH_DELAY_MS)
    })
    expect(result.current.failed).toBe(true)

    rerender({ term: 'msf' })
    expect(result.current.failed).toBe(false)
    rerender({ term: '' })
    rerender({ term: 'ms' })
    expect(result.current.failed).toBe(false)
  })

  it('50자 넘는 검색어는 서버 검색 없이 받은 종목 목록에서 찾은 결과, 실패 표시 없음', async () => {
    search.mockRejectedValue(new Error('400'))
    const { result } = renderHook(() => useSymbolSearch('m'.repeat(51), LOCAL, 20))

    await act(async () => {
      vi.advanceTimersByTime(SYMBOL_SEARCH_DELAY_MS)
    })

    expect(search).not.toHaveBeenCalled()
    expect(result.current.matches).toEqual([])
    expect(result.current.failed).toBe(false)
    expect(SYMBOL_SEARCH_MAX_LENGTH).toBe(50)
  })

  it('빈 검색어는 서버 검색 없이 받은 목록 앞부분', () => {
    const { result } = renderHook(() => useSymbolSearch('  ', LOCAL, 2))

    act(() => {
      vi.advanceTimersByTime(SYMBOL_SEARCH_DELAY_MS)
    })

    expect(search).not.toHaveBeenCalled()
    expect(result.current.matches.map((match) => match.symbol)).toEqual(['AAPL', 'MSFT'])
    expect(result.current.failed).toBe(false)
  })
})
