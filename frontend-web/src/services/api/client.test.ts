import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { AxiosAdapter } from 'axios'

// 공용 클라이언트가 싣는 Authorization(가짜 어댑터를 물려받게 새로 불러옴)

async function setup() {
  vi.resetModules()
  const axios = (await import('axios')).default
  const auth: Record<string, unknown> = {}
  const adapter: AxiosAdapter = async (config) => {
    auth[config.url!] = config.headers?.Authorization
    return { data: {}, status: 200, statusText: 'OK', headers: {}, config, request: {} }
  }
  axios.defaults.adapter = adapter
  const { apiClient } = await import('./client')
  return { apiClient, auth }
}

describe('apiClient 토큰 싣기', () => {
  beforeEach(() => localStorage.clear())

  it('시세 · 로그인 같은 공개 경로에는 토큰을 싣지 않는다', async () => {
    localStorage.setItem('accessToken', 'A1')
    const { apiClient, auth } = await setup()

    await apiClient.get('/market/price/AAPL')
    await apiClient.post('/auth/login', {})

    expect(auth['/market/price/AAPL']).toBeUndefined()
    expect(auth['/auth/login']).toBeUndefined()
  })

  it('관리자 시세 경로처럼 중간에 market 이 든 주소에는 토큰을 싣는다', async () => {
    localStorage.setItem('accessToken', 'A1')
    const { apiClient, auth } = await setup()

    await apiClient.get('/admin/market/assets/search')

    expect(auth['/admin/market/assets/search']).toBe('Bearer A1')
  })
})
