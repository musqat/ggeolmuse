import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { AxiosAdapter } from 'axios'

// 계좌 · 인증 클라이언트가 부르는 경로 · 방식 · 본문(apiClient 가 가짜 어댑터를 물려받게 새로 불러옴)

type Accounts = typeof import('./accounts')
type Auth = typeof import('./auth')

interface Call {
  method: string
  url: string
  body?: unknown
  credentials?: boolean
}

interface Case {
  name: string
  call: (m: { accounts: Accounts; auth: Auth }) => Promise<unknown>
  method: string
  url: string
  body?: unknown
  credentials?: boolean
}

async function setup() {
  vi.resetModules()
  const axios = (await import('axios')).default
  const calls: Call[] = []
  const adapter: AxiosAdapter = async (config) => {
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data
    calls.push({ method: config.method!.toUpperCase(), url: config.url!, body, credentials: config.withCredentials })
    return { data: { ok: true }, status: 200, statusText: 'OK', headers: {}, config, request: {} }
  }
  axios.defaults.adapter = adapter
  const accounts = await import('./accounts')
  const auth = await import('./auth')
  return { modules: { accounts, auth }, calls }
}

const exchange = { fromCurrency: 'KRW' as const, toCurrency: 'USD' as const, originalAmount: 100000, exchangeRate: 1350 }

const cases: Case[] = [
  { name: '계좌 목록', call: ({ accounts }) => accounts.accountsApi.getAccounts(), method: 'GET', url: '/accounts' },
  { name: '계좌 잔액', call: ({ accounts }) => accounts.accountsApi.getAccountBalance(3), method: 'GET', url: '/accounts/3/balance' },
  { name: '계좌 만들기', call: ({ accounts }) => accounts.accountsApi.createAccount({ accountName: '연습', commissionRate: 0.001 }), method: 'POST', url: '/accounts', body: { accountName: '연습', commissionRate: 0.001 } },
  { name: '원화 입금', call: ({ accounts }) => accounts.accountsApi.depositKrw(3, { krwAmount: 50000 }), method: 'POST', url: '/accounts/3/deposit', body: { krwAmount: 50000 } },
  { name: '환전', call: ({ accounts }) => accounts.accountsApi.exchangeCurrency(3, exchange), method: 'POST', url: '/accounts/3/exchange', body: exchange },
  { name: '현재 환율', call: ({ accounts }) => accounts.accountsApi.getCurrentExchangeRate(), method: 'GET', url: '/accounts/exchange-rates/current' },
  { name: '환전 환율 범위', call: ({ accounts }) => accounts.accountsApi.getExchangeRateRange(), method: 'GET', url: '/accounts/exchange-rates/range' },
  { name: '계좌 지우기', call: ({ accounts }) => accounts.accountsApi.deleteAccount(3), method: 'DELETE', url: '/accounts/3' },
  { name: '회원가입', call: ({ auth }) => auth.authApi.register({ email: 'a@b.c', password: 'Pass123!', nickname: '닉' }), method: 'POST', url: '/auth/register', body: { email: 'a@b.c', password: 'Pass123!', nickname: '닉' } },
  { name: '인증 메일 다시 보내기', call: ({ auth }) => auth.authApi.resendVerification({ email: 'a@b.c' }), method: 'POST', url: '/auth/resend-verification', body: { email: 'a@b.c' } },
  { name: '비밀번호 재설정 메일', call: ({ auth }) => auth.authApi.forgotPassword({ email: 'a@b.c' }), method: 'POST', url: '/auth/forgot-password', body: { email: 'a@b.c' } },
  { name: '비밀번호 재설정', call: ({ auth }) => auth.authApi.resetPassword({ token: 't1', newPassword: 'Pass123!' }), method: 'POST', url: '/auth/reset-password', body: { token: 't1', newPassword: 'Pass123!' } },
  { name: '로그아웃', call: ({ auth }) => auth.authApi.logout(), method: 'POST', url: '/auth/logout', body: null, credentials: true },
  { name: '토큰 갱신', call: ({ auth }) => auth.authApi.refreshToken(), method: 'POST', url: '/auth/refresh', body: null, credentials: true },
  { name: '비밀번호 바꾸기', call: ({ auth }) => auth.authApi.changePassword({ currentPassword: 'Old123!', newPassword: 'New123!' }), method: 'PUT', url: '/users/me/password', body: { currentPassword: 'Old123!', newPassword: 'New123!' } },
  { name: '닉네임 바꾸기', call: ({ auth }) => auth.authApi.changeNickname({ nickname: '새닉' }), method: 'PUT', url: '/users/me', body: { nickname: '새닉' } },
  { name: '탈퇴(비밀번호 사용자)', call: ({ auth }) => auth.authApi.deleteAccount('Pass123!'), method: 'DELETE', url: '/users/me', body: { password: 'Pass123!' } },
  { name: '탈퇴(구글 사용자)', call: ({ auth }) => auth.authApi.deleteAccount(), method: 'DELETE', url: '/users/me', body: undefined },
]

describe('계좌 · 인증 API 요청', () => {
  beforeEach(() => localStorage.clear())

  it.each(cases)('$name: $method $url', async ({ call, method, url, body, credentials }) => {
    const { modules, calls } = await setup()

    await call(modules)

    expect(calls).toHaveLength(1)
    expect(calls[0]).toMatchObject({ method, url })
    expect(calls[0].body).toEqual(body)
    if (credentials) expect(calls[0].credentials).toBe(true)
  })
})
