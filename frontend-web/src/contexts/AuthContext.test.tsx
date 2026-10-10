import React from 'react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { AxiosError, type AxiosResponse } from 'axios'
import { authApi } from '../services/api'
import { useAuth } from '../hooks/useAuth'
import { AuthProvider } from './AuthContext'

vi.mock('../services/api', () => ({
  authApi: {
    getCurrentUser: vi.fn(),
    login: vi.fn(),
    register: vi.fn(),
    forgotPassword: vi.fn(),
    resetPassword: vi.fn(),
    resendVerification: vi.fn(),
  },
}))

const api = vi.mocked(authApi)
const USER = { id: 1, email: 'u@example.local', name: 'U', nickname: 'U', emailVerified: true, role: 'USER', createdAt: '2026-01-05T12:00:00' }

// 만료가 먼 토큰(서명은 안 본다)
const validToken = () => `h.${btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }))}.s`
const httpError = (status: number) =>
  new AxiosError('fail', 'ERR', undefined, undefined, { status, data: {}, statusText: '', headers: {}, config: {} } as AxiosResponse)
const ok = <T,>(data: T) => ({ data }) as AxiosResponse<T>

const wrapper = ({ children }: { children: React.ReactNode }) => <AuthProvider>{children}</AuthProvider>
// 첫 확인이 끝날 때까지 기다린다
const renderAuth = async () => {
  const hook = renderHook(() => useAuth(), { wrapper })
  await waitFor(() => expect(hook.result.current.isLoading).toBe(false))
  return hook
}

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
})

describe('AuthProvider 처음 확인', () => {
  it('유효한 토큰이 있으면 내 정보를 받아 로그인 상태가 된다', async () => {
    localStorage.setItem('accessToken', validToken())
    api.getCurrentUser.mockResolvedValue(ok(USER) as never)

    const { result } = await renderAuth()

    expect(result.current.isAuthenticated).toBe(true)
    expect(result.current.user).toEqual(USER)
    expect(result.current.isAdmin).toBe(false)
  })

  it('토큰은 있는데 내 정보 받기가 실패하면 토큰을 지우고 로그아웃 상태로 둔다', async () => {
    localStorage.setItem('accessToken', validToken())
    api.getCurrentUser.mockRejectedValue(httpError(500))

    const { result } = await renderAuth()

    expect(result.current.isAuthenticated).toBe(false)
    expect(result.current.user).toBeNull()
    expect(localStorage.getItem('accessToken')).toBeNull()
  })

  it.each([
    ['토큰 없음', null],
    ['만료된 토큰', `h.${btoa(JSON.stringify({ exp: 1 }))}.s`],
  ])('%s 이면 내 정보를 묻지 않는다', async (_label, token) => {
    if (token) localStorage.setItem('accessToken', token)

    const { result } = await renderAuth()

    expect(api.getCurrentUser).not.toHaveBeenCalled()
    expect(result.current.isAuthenticated).toBe(false)
  })

  it('관리자면 isAdmin 이 참이다', async () => {
    localStorage.setItem('accessToken', validToken())
    api.getCurrentUser.mockResolvedValue(ok({ ...USER, role: 'ADMIN' }) as never)

    const { result } = await renderAuth()

    expect(result.current.isAdmin).toBe(true)
  })
})

describe('AuthProvider 로그인', () => {
  afterEach(() => vi.useRealTimers())

  it('받은 토큰을 저장하고 내 정보를 받아 로그인 상태가 된다', async () => {
    api.login.mockResolvedValue(ok('TOKEN1') as never)
    api.getCurrentUser.mockResolvedValue(ok(USER) as never)
    const { result } = await renderAuth()

    await act(() => result.current.login('u@example.local', 'pw'))

    expect(api.login).toHaveBeenCalledWith({ email: 'u@example.local', password: 'pw' })
    expect(localStorage.getItem('accessToken')).toBe('TOKEN1')
    expect(result.current.isAuthenticated).toBe(true)
    expect(result.current.user).toEqual(USER)
  })

  // 지금 동작 고정: 401 이면 0.5초 뒤 한 번 더 보낸다(BACKLOG 이메일 로그인 항목에서 지울 예정)
  it('401 이면 0.5초 뒤 한 번 더 보내고, 또 401 이면 실패를 던진다', async () => {
    api.login.mockRejectedValue(httpError(401))
    const { result } = await renderAuth()
    vi.useFakeTimers()

    let caught: unknown
    const done = act(() => result.current.login('u@example.local', 'bad').catch((e) => { caught = e }))
    await vi.advanceTimersByTimeAsync(500)
    await done

    expect(api.login).toHaveBeenCalledTimes(2)
    expect((caught as AxiosError).response?.status).toBe(401)
    expect(result.current.isAuthenticated).toBe(false)
    expect(localStorage.getItem('accessToken')).toBeNull()
  })

  it('401 뒤 다시 보낸 것이 성공하면 로그인 상태가 된다', async () => {
    api.login.mockRejectedValueOnce(httpError(401)).mockResolvedValueOnce(ok('TOKEN2') as never)
    api.getCurrentUser.mockResolvedValue(ok(USER) as never)
    const { result } = await renderAuth()
    vi.useFakeTimers()

    const done = act(() => result.current.login('u@example.local', 'pw'))
    await vi.advanceTimersByTimeAsync(500)
    await done

    expect(api.login).toHaveBeenCalledTimes(2)
    expect(result.current.isAuthenticated).toBe(true)
  })

  it('401 이 아닌 실패는 다시 보내지 않고 던진다', async () => {
    api.login.mockRejectedValue(httpError(500))
    const { result } = await renderAuth()

    await act(() => expect(result.current.login('u@example.local', 'pw')).rejects.toMatchObject({ response: { status: 500 } }))

    expect(api.login).toHaveBeenCalledTimes(1)
    expect(result.current.isAuthenticated).toBe(false)
  })
})

describe('AuthProvider 로그아웃', () => {
  const loggedIn = async () => {
    localStorage.setItem('accessToken', validToken())
    api.getCurrentUser.mockResolvedValue(ok(USER) as never)
    const hook = await renderAuth()
    expect(hook.result.current.isAuthenticated).toBe(true)
    return hook
  }

  it('logout 은 access token 을 지우고 로그아웃 상태로 둔다', async () => {
    const { result } = await loggedIn()

    act(() => result.current.logout())

    expect(result.current.isAuthenticated).toBe(false)
    expect(result.current.user).toBeNull()
    expect(localStorage.getItem('accessToken')).toBeNull()
  })

  it('인터셉터가 보낸 auth:logout 이벤트를 받으면 로그아웃 상태로 둔다', async () => {
    const { result } = await loggedIn()

    act(() => { window.dispatchEvent(new CustomEvent('auth:logout')) })

    expect(result.current.isAuthenticated).toBe(false)
    expect(result.current.user).toBeNull()
    expect(localStorage.getItem('accessToken')).toBeNull()
  })
})

describe('AuthProvider 계정 요청', () => {
  it('회원가입 · 비밀번호 찾기 · 재설정 · 인증 메일 재발송을 그대로 넘긴다', async () => {
    api.register.mockResolvedValue(ok(undefined) as never)
    api.forgotPassword.mockResolvedValue(ok(undefined) as never)
    api.resetPassword.mockResolvedValue(ok(undefined) as never)
    api.resendVerification.mockResolvedValue(ok(undefined) as never)
    const { result } = await renderAuth()

    await act(async () => {
      await result.current.signup('n@example.local', 'pw', '닉')
      await result.current.forgotPassword('n@example.local')
      await result.current.resetPassword('RESET', 'pw2')
      await result.current.resendVerificationEmail('n@example.local')
    })

    expect(api.register).toHaveBeenCalledWith({ email: 'n@example.local', password: 'pw', nickname: '닉' })
    expect(api.forgotPassword).toHaveBeenCalledWith({ email: 'n@example.local' })
    expect(api.resetPassword).toHaveBeenCalledWith({ token: 'RESET', newPassword: 'pw2' })
    expect(api.resendVerification).toHaveBeenCalledWith({ email: 'n@example.local' })
    // 회원가입은 이메일 인증 전이라 로그인시키지 않는다
    expect(result.current.isAuthenticated).toBe(false)
  })

  it('요청이 실패해도 로딩을 끈다', async () => {
    api.register.mockRejectedValue(httpError(409))
    const { result } = await renderAuth()

    await act(() => expect(result.current.signup('n@example.local', 'pw', '닉')).rejects.toBeTruthy())

    expect(result.current.isLoading).toBe(false)
  })
})
