import { describe, it, expect, vi, beforeEach, afterEach, type Mock } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { accountsApi } from '../services/api'
import { notify } from '@/components/common/notify'
import { useAccountOperations } from './useAccountOperations'

vi.mock('../services/api', () => ({
  accountsApi: { createAccount: vi.fn(), depositKrw: vi.fn(), exchangeCurrency: vi.fn(), deleteAccount: vi.fn() },
}))

type Operations = ReturnType<typeof useAccountOperations>

let onSuccess: Mock<() => Promise<void>>

beforeEach(() => {
  vi.clearAllMocks()
  onSuccess = vi.fn<() => Promise<void>>().mockResolvedValue(undefined)
  vi.spyOn(notify, 'success').mockImplementation(() => {})
  vi.spyOn(notify, 'error').mockImplementation(() => {})
})
afterEach(() => vi.restoreAllMocks())

describe('useAccountOperations', () => {
  it('입금하면 알리고 다시 불러온다', async () => {
    vi.mocked(accountsApi.depositKrw).mockResolvedValue({} as never)
    const { result } = renderHook(() => useAccountOperations(onSuccess))

    await act(() => result.current.depositKrw({ accountId: 3, amount: 1000 }))

    expect(accountsApi.depositKrw).toHaveBeenCalledWith(3, { krwAmount: 1000 })
    expect(onSuccess).toHaveBeenCalledTimes(1)
    expect(notify.success).toHaveBeenCalledWith('입금이 완료되었습니다.')
  })

  it('실패하면 실패 알림 뒤 다시 던진다', async () => {
    vi.mocked(accountsApi.depositKrw).mockRejectedValue(new Error('down'))
    const { result } = renderHook(() => useAccountOperations(onSuccess))

    await expect(act(() => result.current.depositKrw({ accountId: 3, amount: 1000 }))).rejects.toThrow('down')

    expect(notify.error).toHaveBeenCalledWith('입금에 실패했습니다: down')
    expect(onSuccess).not.toHaveBeenCalled()
  })

  // [이름, 부르기, API, 성공 알림, 실패 알림]
  const cases = [
    ['생성', (ops: Operations) => ops.createAccount({ accountName: '연습', commissionRate: 0.25, slippageRate: 0.1 }),
      'createAccount', '계좌가 생성되었습니다.', '계좌 생성에 실패했습니다: down'],
    ['환전', (ops: Operations) => ops.exchangeCurrency({ accountId: 3, fromCurrency: 'KRW', amount: 1000, exchangeRate: 1350 }),
      'exchangeCurrency', '환전이 완료되었습니다.', '환전에 실패했습니다: down'],
    ['삭제', (ops: Operations) => ops.deleteAccount({ accountId: 3 }),
      'deleteAccount', '계좌가 삭제되었습니다.', '계좌 삭제에 실패했습니다: down'],
  ] as const

  it.each(cases)('%s: 성공하면 다시 불러온 뒤 알린다', async (_name, call, method, successText) => {
    vi.mocked(accountsApi[method]).mockResolvedValue({} as never)
    const { result } = renderHook(() => useAccountOperations(onSuccess))

    await act(() => call(result.current))

    expect(onSuccess).toHaveBeenCalledTimes(1)
    expect(notify.success).toHaveBeenCalledWith(successText)
  })

  it.each(cases)('%s: 실패하면 실패 알림 뒤 다시 던진다', async (_name, call, method, _success, failureText) => {
    vi.mocked(accountsApi[method]).mockRejectedValue(new Error('down'))
    const { result } = renderHook(() => useAccountOperations(onSuccess))

    await expect(act(() => call(result.current))).rejects.toThrow('down')

    expect(notify.error).toHaveBeenCalledWith(failureText)
    expect(onSuccess).not.toHaveBeenCalled()
  })

  it('생성은 수수료 · 슬리피지를 소수로 보낸다', async () => {
    vi.mocked(accountsApi.createAccount).mockResolvedValue({} as never)
    const { result } = renderHook(() => useAccountOperations(onSuccess))

    await act(() => result.current.createAccount({ accountName: '연습', commissionRate: 0.25, slippageRate: 0.1 }))

    expect(accountsApi.createAccount).toHaveBeenCalledWith({ accountName: '연습', commissionRate: 0.0025, slippageRate: 0.001 })
  })
})
