import React from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { AxiosError, type AxiosResponse } from 'axios'
import { authApi, refreshAccessToken } from '../services/api'
import { useAuth } from '../hooks/useAuth'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
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
const refresh = vi.mocked(refreshAccessToken)
const USER = { id: 1, email: 'u@example.local', name: 'U', nickname: 'U', emailVerified: true, role: 'USER', createdAt: '2026-01-05T12:00:00' }

// 만료가 먼 토큰(서명은 안 본다)
const validToken = () => `h.${btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }))}.s`
const expiredToken = `h.${btoa(JSON.stringify({ exp: 1 }))}.s`
const httpError = (status: number) =>
  new AxiosError('fail', 'ERR', undefined, undefined, { status, data: {}, statusText: '', headers: {}, config: {} } as AxiosResponse)
const ok = <T,>(data: T) => ({ data }) as AxiosResponse<T>

// 로그아웃 뒤 캐시를 볼 수 있게 테스트마다 새 클라이언트
let client: QueryClient
const wrapper = ({ children }: { children: React.ReactNode }) => (
  <QueryClientProvider client={client}>
    <AuthProvider>{children}</AuthProvider>
  </QueryClientProvider>
)
// 첫 확인이 끝날 때까지 대기
const renderAuth = async () => {
  const hook = renderHook(() => useAuth(), { wrapper })
  await waitFor(() => expect(hook.result.current.isLoading).toBe(false))
  return hook
}

beforeEach(() => {
  client = new QueryClient()
  localStorage.clear()
  vi.clearAllMocks()
  api.logout.mockResolvedValue(ok(undefined) as never)
})

describe('AuthProvider 처음 확인', () => {
  it('유효한 토큰이 있으면 내 정보를 받아 로그인 상태가 된다', async () => {
    localStorage.setItem('accessToken', validToken())
    api.getCurrentUser.mockResolvedValue(ok(USER) as never)

    const { result } = await renderAuth()

    expect(result.current.isAuthenticated).toBe(true)
    expect(result.current.user).toEqual(USER)
    expect(result.current.isAdmin).toBe(false)
    expect(refresh).not.toHaveBeenCalled()
  })

  it('예전 버전이 localStorage 에 둔 refresh token 은 지운다(로그인은 유지)', async () => {
    localStorage.setItem('accessToken', validToken())
    localStorage.setItem('refreshToken', 'LEGACY')
    api.getCurrentUser.mockResolvedValue(ok(USER) as never)

    const { result } = await renderAuth()

    expect(localStorage.getItem('refreshToken')).toBeNull()
    expect(result.current.isAuthenticated).toBe(true)
  })

  it('토큰은 있는데 내 정보 받기가 실패하면 토큰을 지우고 로그아웃 상태로 둔다', async () => {
    localStorage.setItem('accessToken', validToken())
    api.getCurrentUser.mockRejectedValue(httpError(500))

    const { result } = await renderAuth()

    expect(result.current.isAuthenticated).toBe(false)
    expect(result.current.user).toBeNull()
    expect(localStorage.getItem('accessToken')).toBeNull()
  })

  it('access token 이 아예 없으면 갱신도 내 정보도 묻지 않는다', async () => {
    const { result } = await renderAuth()

    expect(refresh).not.toHaveBeenCalled()
    expect(api.getCurrentUser).not.toHaveBeenCalled()
    expect(result.current.isAuthenticated).toBe(false)
  })

  it('access 가 만료돼도 refresh token 쿠키로 갱신되면 로그인 상태를 이어 간다(새로고침)', async () => {
    localStorage.setItem('accessToken', expiredToken)
    refresh.mockImplementation(async () => {
      localStorage.setItem('accessToken', validToken())
      return 'NEW'
    })
    api.getCurrentUser.mockResolvedValue(ok(USER) as never)

    const { result } = await renderAuth()

    expect(refresh).toHaveBeenCalledTimes(1)
    expect(result.current.isAuthenticated).toBe(true)
    expect(result.current.user).toEqual(USER)
  })

  it('갱신도 실패하면(쿠키 없음 · 세션 끝) 토큰을 지우고 로그아웃 상태로 둔다', async () => {
    localStorage.setItem('accessToken', expiredToken)
    refresh.mockRejectedValue(httpError(401))

    const { result } = await renderAuth()

    expect(api.getCurrentUser).not.toHaveBeenCalled()
    expect(result.current.isAuthenticated).toBe(false)
    expect(localStorage.getItem('accessToken')).toBeNull()
  })

  it('관리자면 isAdmin 이 참이다', async () => {
    localStorage.setItem('accessToken', validToken())
    api.getCurrentUser.mockResolvedValue(ok({ ...USER, role: 'ADMIN' }) as never)

    const { result } = await renderAuth()

    expect(result.current.isAdmin).toBe(true)
  })
})

describe('AuthProvider 로그인', () => {
  it('access token 만 저장하고(refresh 는 서버가 쿠키로 심는다) 내 정보를 받아 로그인 상태가 된다', async () => {
    api.login.mockResolvedValue(ok({ accessToken: 'A1', expiresIn: 3600 }) as never)
    api.getCurrentUser.mockResolvedValue(ok(USER) as never)
    const { result } = await renderAuth()

    await act(() => result.current.login('u@example.local', 'pw'))

    expect(api.login).toHaveBeenCalledWith({ email: 'u@example.local', password: 'pw' })
    expect(localStorage.getItem('accessToken')).toBe('A1')
    expect(localStorage.getItem('refreshToken')).toBeNull()
    expect(result.current.isAuthenticated).toBe(true)
    expect(result.current.user).toEqual(USER)
  })

  it.each([401, 500])('%i 이면 다시 보내지 않고 던지며 토큰을 지운다', async (status) => {
    api.login.mockRejectedValue(httpError(status))
    const { result } = await renderAuth()

    await act(() => expect(result.current.login('u@example.local', 'bad')).rejects.toMatchObject({ response: { status } }))

    expect(api.login).toHaveBeenCalledTimes(1)
    expect(result.current.isAuthenticated).toBe(false)
    expect(localStorage.getItem('accessToken')).toBeNull()
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

  it('logout 은 access token 을 지우고 서버에 쿠키 세션 종료를 보낸다', async () => {
    const { result } = await loggedIn()

    act(() => result.current.logout())

    expect(result.current.isAuthenticated).toBe(false)
    expect(result.current.user).toBeNull()
    expect(localStorage.getItem('accessToken')).toBeNull()
    expect(api.logout).toHaveBeenCalledWith()
  })

  it('서버 로그아웃이 실패해도 화면은 로그아웃 상태다', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    api.logout.mockRejectedValue(httpError(500))
    const { result } = await loggedIn()

    act(() => result.current.logout())

    await waitFor(() => expect(warn).toHaveBeenCalled())
    expect(result.current.isAuthenticated).toBe(false)
    warn.mockRestore()
  })

  it('인터셉터가 보낸 auth:logout 이벤트를 받으면 토큰을 지우고 로그아웃 상태로 둔다', async () => {
    const { result } = await loggedIn()

    act(() => { window.dispatchEvent(new CustomEvent('auth:logout')) })

    expect(result.current.isAuthenticated).toBe(false)
    expect(result.current.user).toBeNull()
    expect(localStorage.getItem('accessToken')).toBeNull()
  })

  it('logout 하면 앞 사용자의 조회 결과(쿼리 캐시)를 비운다', async () => {
    const { result } = await loggedIn()
    client.setQueryData(['accounts', 'list'], [{ accountId: 1 }])

    act(() => result.current.logout())

    expect(client.getQueryData(['accounts', 'list'])).toBeUndefined()
  })

  it('auth:logout 이벤트로 로그아웃해도 쿼리 캐시를 비운다', async () => {
    const { result } = await loggedIn()
    client.setQueryData(['accounts', 'list'], [{ accountId: 1 }])

    act(() => { window.dispatchEvent(new CustomEvent('auth:logout')) })

    expect(result.current.isAuthenticated).toBe(false)
    expect(client.getQueryData(['accounts', 'list'])).toBeUndefined()
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
    // 회원가입은 이메일 인증 전이라 로그인 안 함
    expect(result.current.isAuthenticated).toBe(false)
  })

  it('요청이 실패해도 로딩을 끈다', async () => {
    api.register.mockRejectedValue(httpError(409))
    const { result } = await renderAuth()

    await act(() => expect(result.current.signup('n@example.local', 'pw', '닉')).rejects.toBeTruthy())

    expect(result.current.isLoading).toBe(false)
  })
})
