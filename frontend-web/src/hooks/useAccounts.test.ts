import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { accountsApi } from '../services/api'
import { useAccounts } from './useAccounts'

vi.mock('../services/api', () => ({ accountsApi: { getAccounts: vi.fn() } }))

const getAccounts = vi.mocked(accountsApi.getAccounts)
const account = (accountId: number) => ({ accountId, accountName: `계좌 ${accountId}`, usdBalance: 100, krwBalance: 0, commissionRate: 0 })

beforeEach(() => {
  vi.clearAllMocks()
})

describe('useAccounts', () => {
  it('로그인 전이면 계좌를 묻지 않는다', () => {
    const { result } = renderHook(() => useAccounts(false))

    expect(getAccounts).not.toHaveBeenCalled()
    expect(result.current.accounts).toEqual([])
    expect(result.current.selectedAccountId).toBeNull()
  })

  it('로그인하면 accountsApi 로 계좌를 받고 첫 계좌를 고른다', async () => {
    getAccounts.mockResolvedValue({ data: [account(3), account(7)] } as never)

    const { result } = renderHook(() => useAccounts(true))

    await waitFor(() => expect(result.current.selectedAccountId).toBe(3))
    expect(result.current.accounts.map((a) => a.accountId)).toEqual([3, 7])
    expect(result.current.loading).toBe(false)
  })

  it('못 받으면 오류 문구를 둔다', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    getAccounts.mockRejectedValue(new Error('down'))

    const { result } = renderHook(() => useAccounts(true))

    await waitFor(() => expect(result.current.error).toBe('계좌 목록을 불러오는데 실패했습니다.'))
    expect(result.current.accounts).toEqual([])
  })
})
