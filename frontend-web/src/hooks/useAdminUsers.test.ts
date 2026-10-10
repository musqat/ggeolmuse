import { describe, it, expect, vi, beforeEach, afterEach, type Mock } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { userAdminApi } from '@services/adminApi'
import { useAdminUsers } from './useAdminUsers'

vi.mock('@services/adminApi', () => ({
  userAdminApi: {
    getUsers: vi.fn(),
    getUserStats: vi.fn(),
    getUserDetail: vi.fn(),
    updateUserRole: vi.fn(),
    updateUserEnabled: vi.fn(),
    updateNickname: vi.fn(),
    updatePassword: vi.fn(),
    verifyEmail: vi.fn(),
    deleteUser: vi.fn(),
  },
}))

const api = vi.mocked(userAdminApi)
// 표에서 고른 API 하나. 반환 타입이 제각각이라 묶으면 부를 수 없어 Mock 으로 본다
const mockOf = (method: keyof typeof api) => api[method] as unknown as Mock
const user = (userId: number) => ({ userId, email: `u${userId}@example.local` }) as never
const STATS = { totalUsers: 3, activeUsers: 2, inactiveUsers: 1, adminUsers: 1 }

let alertSpy: ReturnType<typeof vi.spyOn>

beforeEach(() => {
  vi.clearAllMocks()
  api.getUsers.mockImplementation(async (page = 0) => ({ content: [user(page + 1)], totalPages: 3, totalElements: 41, number: page, size: 20 }) as never)
  api.getUserStats.mockResolvedValue(STATS as never)
  api.getUserDetail.mockImplementation(async (userId) => ({ ...(user(userId) as object), accounts: [] }) as never)
  alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {})
  vi.spyOn(console, 'error').mockImplementation(() => {})
})
afterEach(() => vi.restoreAllMocks())

const renderLoaded = async () => {
  const hook = renderHook(() => useAdminUsers())
  await waitFor(() => expect(hook.result.current.loading).toBe(false))
  await waitFor(() => expect(hook.result.current.stats).not.toBeNull())
  return hook
}

describe('useAdminUsers 불러오기', () => {
  it('처음 열 때 첫 페이지(가입일 최신순)와 통계를 받는다', async () => {
    const { result } = await renderLoaded()

    expect(api.getUsers).toHaveBeenCalledWith(0, 20, 'createdAt,desc')
    expect(result.current.users).toEqual([user(1)])
    expect(result.current.totalPages).toBe(3)
    expect(result.current.stats).toEqual(STATS)
  })

  it('페이지는 범위 안에서만 옮긴다', async () => {
    const { result } = await renderLoaded()
    api.getUsers.mockClear()

    act(() => result.current.goToPage(-1))
    act(() => result.current.goToPage(3))
    expect(api.getUsers).not.toHaveBeenCalled()

    act(() => result.current.goToPage(2))
    await waitFor(() => expect(result.current.page).toBe(2))
    expect(api.getUsers).toHaveBeenCalledWith(2, 20, 'createdAt,desc')
  })

  it('목록을 못 받으면 오류 문구를 두고, 통계 실패는 조용히 넘긴다', async () => {
    api.getUsers.mockRejectedValue(new Error('down'))
    api.getUserStats.mockRejectedValue(new Error('down'))

    const { result } = renderHook(() => useAdminUsers())

    await waitFor(() => expect(result.current.error).toBe('사용자 목록 조회에 실패했습니다.'))
    expect(result.current.stats).toBeNull()
  })

  it('상세를 받고, 실패하면 오류 문구를 둔다', async () => {
    const { result } = await renderLoaded()

    await act(() => result.current.loadUserDetail(5))
    expect(result.current.selectedUser).toMatchObject({ userId: 5 })

    api.getUserDetail.mockRejectedValueOnce(new Error('nope'))
    await act(() => result.current.loadUserDetail(6))
    expect(result.current.error).toBe('사용자 상세 조회에 실패했습니다.')
  })
})

describe('useAdminUsers 바꾸기', () => {
  // [이름, 부르기, API 와 인자, 성공 alert, 실패 문구]
  const cases = [
    ['역할', (h: ReturnType<typeof useAdminUsers>) => h.updateRole(5, 'ADMIN'), 'updateUserRole', [5, 'ADMIN'], '역할이 변경되었습니다.', '역할 변경에 실패했습니다.'],
    ['비활성화', (h: ReturnType<typeof useAdminUsers>) => h.updateEnabled(5, false), 'updateUserEnabled', [5, false], '사용자가 비활성화되었습니다.', '활성화 상태 변경에 실패했습니다.'],
    ['닉네임', (h: ReturnType<typeof useAdminUsers>) => h.updateNickname(5, '새이름'), 'updateNickname', [5, '새이름'], '닉네임이 변경되었습니다.', '닉네임 변경에 실패했습니다.'],
    ['이메일 인증', (h: ReturnType<typeof useAdminUsers>) => h.verifyEmail(5), 'verifyEmail', [5], '이메일이 인증되었습니다.', '이메일 인증에 실패했습니다.'],
  ] as const

  it.each(cases)('%s: 바꾸고 지금 페이지와 열어 둔 상세를 다시 받는다', async (_name, call, method, args, alertText) => {
    mockOf(method).mockResolvedValue(undefined)
    const { result } = await renderLoaded()
    await act(() => result.current.loadUserDetail(5))
    api.getUsers.mockClear()
    api.getUserDetail.mockClear()

    await act(() => call(result.current))

    expect(api[method]).toHaveBeenCalledWith(...args)
    expect(alertSpy).toHaveBeenCalledWith(alertText)
    await waitFor(() => expect(api.getUsers).toHaveBeenCalledWith(0, 20, 'createdAt,desc'))
    await waitFor(() => expect(api.getUserDetail).toHaveBeenCalledWith(5))
  })

  it.each(cases)('%s: 다른 사용자 상세가 열려 있으면 상세는 다시 받지 않는다', async (_name, call, method) => {
    mockOf(method).mockResolvedValue(undefined)
    const { result } = await renderLoaded()
    await act(() => result.current.loadUserDetail(9))
    api.getUserDetail.mockClear()

    await act(() => call(result.current))

    await waitFor(() => expect(api.getUsers).toHaveBeenCalled())
    expect(api.getUserDetail).not.toHaveBeenCalled()
  })

  it.each(cases)('%s: 실패하면 오류 문구를 둔다', async (_name, call, method, _args, _alert, errorText) => {
    mockOf(method).mockRejectedValue(new Error('nope'))
    const { result } = await renderLoaded()

    await act(() => call(result.current))

    expect(result.current.error).toBe(errorText)
    expect(alertSpy).not.toHaveBeenCalled()
  })

  it('비밀번호는 바꾸기만 하고 목록을 다시 받지 않는다', async () => {
    api.updatePassword.mockResolvedValue(undefined)
    const { result } = await renderLoaded()
    api.getUsers.mockClear()

    await act(() => result.current.updatePassword(5, 'newpw'))

    expect(api.updatePassword).toHaveBeenCalledWith(5, 'newpw')
    expect(alertSpy).toHaveBeenCalledWith('비밀번호가 변경되었습니다.')
    expect(api.getUsers).not.toHaveBeenCalled()
  })

  it('삭제하면 상세를 닫고 목록 · 통계를 다시 받는다', async () => {
    api.deleteUser.mockResolvedValue(undefined)
    const { result } = await renderLoaded()
    await act(() => result.current.loadUserDetail(5))
    api.getUsers.mockClear()
    api.getUserStats.mockClear()

    await act(() => result.current.deleteUser(5))

    expect(api.deleteUser).toHaveBeenCalledWith(5)
    expect(result.current.selectedUser).toBeNull()
    await waitFor(() => expect(api.getUsers).toHaveBeenCalled())
    await waitFor(() => expect(api.getUserStats).toHaveBeenCalled())
  })
})
