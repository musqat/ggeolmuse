import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { AxiosError, type AxiosResponse } from 'axios'
import { authApi, refreshAccessToken } from '../services/api'
import { useAuth } from '../hooks/useAuth'
import { AuthProvider } from './AuthContext'

vi.mock('../services/api', () => ({
  refreshAccessToken: vi.fn(),
  authApi: {
    getCurrentUser: vi.fn(),
    login: vi.fn(),
    logout: vi.fn(),
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

  it('토큰은 있는데 내 정보 받기가 실패하면 두 토큰을 지우고 로그아웃 상태로 둔다', async () => {
    localStorage.setItem('accessToken', validToken())
    localStorage.setItem('refreshToken', 'R1')
    api.getCurrentUser.mockRejectedValue(httpError(500))

    const { result } = await renderAuth()

    expect(result.current.isAuthenticated).toBe(false)
    expect(result.current.user).toBeNull()
    expect(localStorage.getItem('accessToken')).toBeNull()
    expect(localStorage.getItem('refreshToken')).toBeNull()
  })

  it.each([
    ['토큰 없음', null],
    ['만료된 토큰', `h.${btoa(JSON.stringify({ exp: 1 }))}.s`],
  ])('%s 이면 내 정보를 묻지 않는다', async (_label, token) => {
    if (token) localStorage.setItem('accessToken', token)

    const { result } = await renderAuth()

    expect(api.getCurrentUser).not.toHaveBeenCalled()
    expect(refreshAccessToken).not.toHaveBeenCalled()
    expect(result.current.isAuthenticated).toBe(false)
  })

  it('access 가 만료돼도 refresh token 으로 갱신되면 로그인 상태를 이어 간다(새로고침)', async () => {
    localStorage.setItem('accessToken', `h.${btoa(JSON.stringify({ exp: 1 }))}.s`)
    localStorage.setItem('refreshToken', 'R1')
    vi.mocked(refreshAccessToken).mockImplementation(async () => {
      localStorage.setItem('accessToken', validToken())
      return 'NEW'
    })
    api.getCurrentUser.mockResolvedValue(ok(USER) as never)

    const { result } = await renderAuth()

    expect(refreshAccessToken).toHaveBeenCalledTimes(1)
    expect(result.current.isAuthenticated).toBe(true)
    expect(result.current.user).toEqual(USER)
  })

  it('갱신도 실패하면 두 토큰을 지우고 로그아웃 상태로 둔다', async () => {
    localStorage.setItem('accessToken', `h.${btoa(JSON.stringify({ exp: 1 }))}.s`)
    localStorage.setItem('refreshToken', 'R1')
    vi.mocked(refreshAccessToken).mockRejectedValue(httpError(400))

    const { result } = await renderAuth()

    expect(api.getCurrentUser).not.toHaveBeenCalled()
    expect(result.current.isAuthenticated).toBe(false)
    expect(localStorage.getItem('accessToken')).toBeNull()
    expect(localStorage.getItem('refreshToken')).toBeNull()
  })

  it('관리자면 isAdmin 이 참이다', async () => {
    localStorage.setItem('accessToken', validToken())
    api.getCurrentUser.mockResolvedValue(ok({ ...USER, role: 'ADMIN' }) as never)

    const { result } = await renderAuth()

    expect(result.current.isAdmin).toBe(true)
  })
})

describe('AuthProvider 로그인', () => {
  it('받은 두 토큰을 저장하고 내 정보를 받아 로그인 상태가 된다', async () => {
    api.login.mockResolvedValue(ok({ accessToken: 'A1', refreshToken: 'R1', expiresIn: 3600 }) as never)
    api.getCurrentUser.mockResolvedValue(ok(USER) as never)
    const { result } = await renderAuth()

    await act(() => result.current.login('u@example.local', 'pw'))

    expect(api.login).toHaveBeenCalledWith({ email: 'u@example.local', password: 'pw' })
    expect(localStorage.getItem('accessToken')).toBe('A1')
    expect(localStorage.getItem('refreshToken')).toBe('R1')
    expect(result.current.isAuthenticated).toBe(true)
    expect(result.current.user).toEqual(USER)
  })

  it('이전 사용자의 refresh token 을 새것으로 덮는다', async () => {
    localStorage.setItem('refreshToken', 'PREVIOUS_USER')
    api.login.mockResolvedValue(ok({ accessToken: 'A2', refreshToken: 'R2', expiresIn: 3600 }) as never)
    api.getCurrentUser.mockResolvedValue(ok(USER) as never)
    const { result } = await renderAuth()

    await act(() => result.current.login('u@example.local', 'pw'))

    expect(localStorage.getItem('refreshToken')).toBe('R2')
  })

  it.each([401, 500])('%i 이면 다시 보내지 않고 던지며 두 토큰을 지운다', async (status) => {
    localStorage.setItem('refreshToken', 'PREVIOUS_USER')
    api.login.mockRejectedValue(httpError(status))
    const { result } = await renderAuth()

    await act(() => expect(result.current.login('u@example.local', 'bad')).rejects.toMatchObject({ response: { status } }))

    expect(api.login).toHaveBeenCalledTimes(1)
    expect(result.current.isAuthenticated).toBe(false)
    expect(localStorage.getItem('accessToken')).toBeNull()
    expect(localStorage.getItem('refreshToken')).toBeNull()
  })
})

describe('AuthProvider 로그아웃', () => {
  const loggedIn = async () => {
    localStorage.setItem('accessToken', validToken())
    localStorage.setItem('refreshToken', 'R1')
    api.getCurrentUser.mockResolvedValue(ok(USER) as never)
    const hook = await renderAuth()
    expect(hook.result.current.isAuthenticated).toBe(true)
    return hook
  }

  it('logout 은 두 토큰을 지우고 그 refresh token 으로 서버 세션을 끊는다', async () => {
    api.logout.mockResolvedValue(ok(undefined) as never)
    const { result } = await loggedIn()

    act(() => result.current.logout())

    expect(result.current.isAuthenticated).toBe(false)
    expect(result.current.user).toBeNull()
    expect(localStorage.getItem('accessToken')).toBeNull()
    expect(localStorage.getItem('refreshToken')).toBeNull()
    expect(api.logout).toHaveBeenCalledWith('R1')
  })

  it('서버 로그아웃이 실패해도 화면은 로그아웃 상태다', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    api.logout.mockRejectedValue(httpError(500))
    const { result } = await loggedIn()

    act(() => result.current.logout())

    await waitFor(() => expect(warn).toHaveBeenCalled())
    expect(result.current.isAuthenticated).toBe(false)
    expect(localStorage.getItem('refreshToken')).toBeNull()
    warn.mockRestore()
  })

  it('refresh token 이 없으면 서버 로그아웃을 부르지 않는다', async () => {
    localStorage.setItem('accessToken', validToken())
    api.getCurrentUser.mockResolvedValue(ok(USER) as never)
    const { result } = await renderAuth()

    act(() => result.current.logout())

    expect(api.logout).not.toHaveBeenCalled()
    expect(result.current.isAuthenticated).toBe(false)
  })

  it('인터셉터가 보낸 auth:logout 이벤트를 받으면 두 토큰을 지우고 로그아웃 상태로 둔다', async () => {
    const { result } = await loggedIn()

    act(() => { window.dispatchEvent(new CustomEvent('auth:logout')) })

    expect(result.current.isAuthenticated).toBe(false)
    expect(result.current.user).toBeNull()
    expect(localStorage.getItem('accessToken')).toBeNull()
    expect(localStorage.getItem('refreshToken')).toBeNull()
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
