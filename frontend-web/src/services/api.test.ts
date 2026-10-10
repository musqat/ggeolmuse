import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios'

// apiClient 는 모듈 안에 있어 밖에서 못 꺼낸다. axios 기본 어댑터를 가짜로 바꾼 뒤 api.ts 를 새로 불러
// apiClient 가 그 어댑터를 물려받게 한다. 갱신 대기열 같은 모듈 상태도 테스트마다 새로 시작한다

interface Call {
  method: string
  url: string
  auth?: string
  body?: unknown
}

type Reply = { status: number; data?: unknown } | ((config: InternalAxiosRequestConfig) => { status: number; data?: unknown } | Promise<{ status: number; data?: unknown }>)

async function setup(routes: Record<string, Reply[]>) {
  vi.resetModules()
  const axios = (await import('axios')).default
  const { AxiosError } = await import('axios')
  const calls: Call[] = []
  const adapter: AxiosAdapter = async (config) => {
    const key = `${config.method?.toUpperCase()} ${config.url}`
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data
    calls.push({ method: config.method!.toUpperCase(), url: config.url!, auth: config.headers?.Authorization as string | undefined, body })
    const queue = routes[key]
    if (!queue?.length) throw new Error(`준비 안 된 요청: ${key}`)
    const next = queue.length > 1 ? queue.shift()! : queue[0]
    const { status, data } = typeof next === 'function' ? await next(config) : next
    const response = { data, status, statusText: String(status), headers: {}, config, request: {} }
    if (status >= 200 && status < 300) return response
    throw new AxiosError(`Request failed with status code ${status}`, AxiosError.ERR_BAD_RESPONSE, config, {}, response)
  }
  axios.defaults.adapter = adapter
  const api = await import('./api')
  return { api, calls }
}

describe('api 요청 인터셉터', () => {
  beforeEach(() => localStorage.clear())

  it('보호된 API 에는 저장된 access token 을 싣는다', async () => {
    localStorage.setItem('accessToken', 'A1')
    const { api, calls } = await setup({ 'GET /users/me': [{ status: 200, data: { id: 1 } }] })

    await api.authApi.getCurrentUser()

    expect(calls[0].auth).toBe('Bearer A1')
  })

  it.each([
    ['시세', (api: typeof import('./api')) => api.stockApi.getAllSymbols(), 'GET /market/symbols'],
    ['로그인', (api: typeof import('./api')) => api.authApi.login({ email: 'a@b.c', password: 'x' }), 'POST /auth/login'],
    ['백테스트', (api: typeof import('./api')) => api.backtestApi.runSimulation({} as never), 'POST /trading-simulation/simulation'],
  ])('공개 API(%s)에는 토큰을 싣지 않는다', async (_name, call, key) => {
    localStorage.setItem('accessToken', 'A1')
    const { api, calls } = await setup({ [key]: [{ status: 200, data: {} }] })

    await call(api)

    expect(calls[0].auth).toBeUndefined()
  })

  it('토큰이 없으면 헤더를 비운다', async () => {
    const { api, calls } = await setup({ 'GET /users/me': [{ status: 200, data: {} }] })

    await api.authApi.getCurrentUser()

    expect(calls[0].auth).toBeUndefined()
  })
})

describe('api 응답 인터셉터 · 401', () => {
  let logouts = 0
  const onLogout = () => { logouts++ }

  beforeEach(() => {
    localStorage.clear()
    logouts = 0
    window.addEventListener('auth:logout', onLogout)
  })
  afterEach(() => window.removeEventListener('auth:logout', onLogout))

  it('refresh token 으로 새 access token 을 받아 저장하고 원래 요청을 다시 보낸다', async () => {
    localStorage.setItem('accessToken', 'OLD')
    localStorage.setItem('refreshToken', 'R1')
    const { api, calls } = await setup({
      'GET /users/me': [{ status: 401 }, { status: 200, data: { id: 7 } }],
      'POST /auth/refresh': [{ status: 200, data: 'NEW' }],
    })

    const res = await api.authApi.getCurrentUser()

    expect(res.data).toEqual({ id: 7 })
    // 갱신 요청에도 만료된 토큰이 실린다. /api/auth/** 는 게이트웨이 공개 체인(JWT 검사 없음)이라 막히지 않는다
    expect(calls.map((c) => `${c.method} ${c.url} ${c.auth ?? '-'}`)).toEqual([
      'GET /users/me Bearer OLD',
      'POST /auth/refresh Bearer OLD',
      'GET /users/me Bearer NEW',
    ])
    expect(calls[1].body).toEqual({ refreshToken: 'R1' })
    expect(localStorage.getItem('accessToken')).toBe('NEW')
    expect(logouts).toBe(0)
  })

  it('갱신하는 동안 온 401 은 기다렸다가 새 토큰으로 다시 보낸다. 갱신은 한 번만 한다', async () => {
    localStorage.setItem('accessToken', 'OLD')
    localStorage.setItem('refreshToken', 'R1')
    let releaseRefresh!: () => void
    const refreshGate = new Promise<void>((resolve) => { releaseRefresh = resolve })
    const { api, calls } = await setup({
      'GET /users/me': [{ status: 401 }, { status: 200, data: { id: 7 } }],
      'GET /accounts': [{ status: 401 }, { status: 200, data: [] }],
      'POST /auth/refresh': [async () => { await refreshGate; return { status: 200, data: 'NEW' } }],
    })

    const first = api.authApi.getCurrentUser()
    // 첫 요청이 갱신을 시작할 때까지 기다린 뒤 두 번째 401 을 낸다
    await vi.waitFor(() => expect(calls.some((c) => c.url === '/auth/refresh')).toBe(true))
    const second = api.accountsApi.getAccounts()
    await vi.waitFor(() => expect(calls.filter((c) => c.url === '/accounts')).toHaveLength(1))
    releaseRefresh()

    await expect(first).resolves.toMatchObject({ data: { id: 7 } })
    await expect(second).resolves.toMatchObject({ data: [] })
    expect(calls.filter((c) => c.url === '/auth/refresh')).toHaveLength(1)
    expect(calls.filter((c) => c.auth === 'Bearer NEW').map((c) => c.url).sort()).toEqual(['/accounts', '/users/me'])
  })

  it('refresh token 이 없으면 access token 을 지우고 로그아웃 이벤트를 낸다', async () => {
    localStorage.setItem('accessToken', 'OLD')
    const { api, calls } = await setup({ 'GET /users/me': [{ status: 401 }] })

    await expect(api.authApi.getCurrentUser()).rejects.toMatchObject({ response: { status: 401 } })

    expect(calls).toHaveLength(1)
    expect(localStorage.getItem('accessToken')).toBeNull()
    expect(logouts).toBe(1)
  })

  it('갱신이 실패하면 두 토큰을 지우고 로그아웃한다. 기다리던 요청도 같이 실패한다', async () => {
    localStorage.setItem('accessToken', 'OLD')
    localStorage.setItem('refreshToken', 'R1')
    let releaseRefresh!: () => void
    const refreshGate = new Promise<void>((resolve) => { releaseRefresh = resolve })
    const { api, calls } = await setup({
      'GET /users/me': [{ status: 401 }],
      'GET /accounts': [{ status: 401 }],
      'POST /auth/refresh': [async () => { await refreshGate; return { status: 401 } }],
    })

    const first = api.authApi.getCurrentUser()
    await vi.waitFor(() => expect(calls.some((c) => c.url === '/auth/refresh')).toBe(true))
    const second = api.accountsApi.getAccounts()
    await vi.waitFor(() => expect(calls.filter((c) => c.url === '/accounts')).toHaveLength(1))
    releaseRefresh()

    await expect(first).rejects.toMatchObject({ config: { url: '/auth/refresh' } })
    await expect(second).rejects.toMatchObject({ config: { url: '/auth/refresh' } })
    expect(localStorage.getItem('accessToken')).toBeNull()
    expect(localStorage.getItem('refreshToken')).toBeNull()
    expect(logouts).toBe(1)
  })

  it('새 토큰으로 다시 보낸 요청도 401 이면 두 토큰을 지우고 로그아웃한다', async () => {
    localStorage.setItem('accessToken', 'OLD')
    localStorage.setItem('refreshToken', 'R1')
    const { api, calls } = await setup({
      'GET /users/me': [{ status: 401 }],
      'POST /auth/refresh': [{ status: 200, data: 'NEW' }],
    })

    await expect(api.authApi.getCurrentUser()).rejects.toMatchObject({ response: { status: 401 } })

    expect(calls.map((c) => c.url)).toEqual(['/users/me', '/auth/refresh', '/users/me'])
    expect(localStorage.getItem('accessToken')).toBeNull()
    expect(localStorage.getItem('refreshToken')).toBeNull()
    expect(logouts).toBe(1)
  })

  it('로그인 요청의 401 은 갱신 · 로그아웃 없이 그대로 돌려준다', async () => {
    localStorage.setItem('refreshToken', 'R1')
    const { api, calls } = await setup({ 'POST /auth/login': [{ status: 401 }] })

    await expect(api.authApi.login({ email: 'a@b.c', password: 'x' })).rejects.toMatchObject({ response: { status: 401 } })

    expect(calls).toHaveLength(1)
    expect(localStorage.getItem('refreshToken')).toBe('R1')
    expect(logouts).toBe(0)
  })
})

describe('api 응답 인터셉터 · 거래 400', () => {
  afterEach(() => vi.restoreAllMocks())

  it('매수 · 매도 400 은 서버 detail 을 경고로 남기고 그대로 돌려준다', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const { api } = await setup({ 'POST /trade/buy': [{ status: 400, data: { detail: '잔액이 부족합니다' } }] })

    await expect(api.tradeApi.buy({} as never)).rejects.toMatchObject({ response: { status: 400 } })

    expect(warn).toHaveBeenCalledWith('[거래 검증 실패] 잔액이 부족합니다')
  })
})
