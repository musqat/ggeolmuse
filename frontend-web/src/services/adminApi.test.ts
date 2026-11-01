import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { AxiosAdapter } from 'axios'

// 기본 어댑터를 가짜로 바꾼 뒤 새로 불러 adminApi 의 모듈 안 axios 인스턴스가 그 어댑터를 물려받게

type Admin = typeof import('./adminApi')

interface Call {
  method: string
  url: string
  body?: unknown
  params?: unknown
  auth?: unknown
}

interface Case {
  name: string
  call: (admin: Admin) => Promise<unknown>
  method: string
  url: string
  body?: unknown
  params?: unknown
  returns?: boolean
}

const REPLY = { ok: true }

async function setup() {
  vi.resetModules()
  const axios = (await import('axios')).default
  const calls: Call[] = []
  const adapter: AxiosAdapter = async (config) => {
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data
    calls.push({ method: config.method!.toUpperCase(), url: config.url!, body, params: config.params, auth: config.headers?.Authorization })
    return { data: REPLY, status: 200, statusText: 'OK', headers: {}, config, request: {} }
  }
  axios.defaults.adapter = adapter
  const admin = await import('./adminApi')
  return { admin, calls }
}

const asset = { symbol: 'AAPL', collectData: true, fromDate: '2024-01-01', toDate: '2024-12-31', includeDividends: false }

const cases: Case[] = [
  { name: '종목 검색', call: (a) => a.marketAdminApi.searchAssets('app'), method: 'GET', url: '/admin/market/assets/search', params: { keyword: 'app' }, returns: true },
  { name: '종목 미리 보기', call: (a) => a.marketAdminApi.previewAsset('AAPL'), method: 'GET', url: '/admin/market/assets/preview/AAPL', returns: true },
  { name: '종목 요약 목록(기본값)', call: (a) => a.marketAdminApi.getAllAssetSummaries(), method: 'GET', url: '/admin/market/assets/summary', params: { page: 0, size: 20, sortBy: 'symbol', direction: 'asc', active: true }, returns: true },
  { name: '종목 요약 목록(지운 종목)', call: (a) => a.marketAdminApi.getAllAssetSummaries(2, 50, 'marketCap', 'desc', false), method: 'GET', url: '/admin/market/assets/summary', params: { page: 2, size: 50, sortBy: 'marketCap', direction: 'desc', active: false } },
  { name: '종목 추가', call: (a) => a.marketAdminApi.createAsset(asset), method: 'POST', url: '/admin/market/assets', body: asset },
  { name: '종목 이름 바꾸기', call: (a) => a.marketAdminApi.updateAssetName('AAPL', 'Apple'), method: 'PUT', url: '/admin/market/assets/AAPL', body: { name: 'Apple' } },
  { name: '종목 지우기', call: (a) => a.marketAdminApi.deleteAsset('AAPL'), method: 'DELETE', url: '/admin/market/assets/AAPL' },
  { name: '종목 되살리기', call: (a) => a.marketAdminApi.restoreAsset('AAPL'), method: 'POST', url: '/admin/market/assets/AAPL/restore' },
  { name: '종목 여럿 지우기', call: (a) => a.marketAdminApi.bulkDeleteAssets(['A', 'B']), method: 'POST', url: '/admin/market/assets/bulk-delete', body: { symbols: ['A', 'B'] }, returns: true },
  { name: '한 종목 시세 갱신', call: (a) => a.marketAdminApi.updateAssetPrice('AAPL'), method: 'POST', url: '/admin/market/assets/AAPL/update-price' },
  { name: '한 종목 시가총액 갱신', call: (a) => a.marketAdminApi.updateAssetMarketCap('AAPL'), method: 'POST', url: '/admin/market/assets/AAPL/update-market-cap' },
  { name: '전체 시세 갱신', call: (a) => a.marketAdminApi.updateAllPrices(), method: 'POST', url: '/admin/market/update/candles' },
  { name: '전체 시가총액 갱신', call: (a) => a.marketAdminApi.updateAllMarketCaps(), method: 'POST', url: '/admin/market/update/market-cap' },
  { name: '분할 미반영 탐색 시작', call: (a) => a.marketAdminApi.startUnadjustedScan('2024-01-01'), method: 'POST', url: '/admin/market/candles/unadjusted/scan', params: { from: '2024-01-01' }, returns: true },
  { name: '분할 미반영 결과', call: (a) => a.marketAdminApi.findUnadjustedSymbols(), method: 'GET', url: '/admin/market/candles/unadjusted', returns: true },
  { name: '지정 종목 캔들 다시 받기', call: (a) => a.marketAdminApi.refreshCandles(['AAPL'], '2024-01-01'), method: 'POST', url: '/admin/market/candles/refresh', body: { symbols: ['AAPL'], from: '2024-01-01' }, returns: true },
  { name: '전체 캔들 다시 받기', call: (a) => a.marketAdminApi.refreshAllCandles(), method: 'POST', url: '/admin/market/candles/refresh-all', returns: true },
  { name: '마지막 전체 다시 받기', call: (a) => a.marketAdminApi.findLastRefreshAll(), method: 'GET', url: '/admin/market/candles/refresh-all', returns: true },
  { name: '신규 상장 종목 받기', call: (a) => a.marketAdminApi.collectNewSymbols(), method: 'POST', url: '/admin/market/update/symbols' },
  { name: '환율 수집 상태', call: (a) => a.marketAdminApi.findFxCollect(), method: 'GET', url: '/admin/market/fx/collect', returns: true },
  { name: '환율 빠진 날 채우기', call: (a) => a.marketAdminApi.startFxCollect('missing'), method: 'POST', url: '/admin/market/fx/collect', params: { mode: 'missing' }, returns: true },
  { name: '사용자 목록(기본값)', call: (a) => a.userAdminApi.getUsers(), method: 'GET', url: '/admin/users', params: { page: 0, size: 20, sort: 'createdAt,desc' }, returns: true },
  { name: '사용자 상세', call: (a) => a.userAdminApi.getUserDetail(7), method: 'GET', url: '/admin/users/7', returns: true },
  { name: '사용자 통계', call: (a) => a.userAdminApi.getUserStats(), method: 'GET', url: '/admin/users/stats', returns: true },
  { name: '역할 바꾸기', call: (a) => a.userAdminApi.updateUserRole(7, 'ADMIN'), method: 'PATCH', url: '/admin/users/7/role', body: { role: 'ADMIN' } },
  { name: '활성 바꾸기', call: (a) => a.userAdminApi.updateUserEnabled(7, false), method: 'PATCH', url: '/admin/users/7/enabled', body: { enabled: false } },
  { name: '닉네임 바꾸기', call: (a) => a.userAdminApi.updateNickname(7, '새닉네임'), method: 'PATCH', url: '/admin/users/7/nickname', body: { nickname: '새닉네임' }, returns: true },
  { name: '비밀번호 바꾸기', call: (a) => a.userAdminApi.updatePassword(7, 'Newpass1!'), method: 'PATCH', url: '/admin/users/7/password', body: { newPassword: 'Newpass1!' } },
  { name: '이메일 인증 처리', call: (a) => a.userAdminApi.verifyEmail(7), method: 'PATCH', url: '/admin/users/7/verify-email', returns: true },
  { name: '사용자 탈퇴', call: (a) => a.userAdminApi.deleteUser(7), method: 'DELETE', url: '/admin/users/7' },
]

describe('관리자 API 요청', () => {
  beforeEach(() => localStorage.clear())

  it.each(cases)('$name: $method $url', async ({ call, method, url, body, params, returns }) => {
    const { admin, calls } = await setup()

    const result = await call(admin)

    expect(calls).toHaveLength(1)
    expect(calls[0]).toMatchObject({ method, url })
    if (body !== undefined) expect(calls[0].body).toEqual(body)
    if (params !== undefined) expect(calls[0].params).toEqual(params)
    if (returns) expect(result).toEqual(REPLY)
  })

  it('저장된 access token 을 싣는다', async () => {
    localStorage.setItem('accessToken', 'A1')
    const { admin, calls } = await setup()

    await admin.userAdminApi.getUserStats()

    expect(calls[0].auth).toBe('Bearer A1')
  })
})
