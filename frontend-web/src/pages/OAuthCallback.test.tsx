import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { AxiosError, type AxiosResponse } from 'axios'
import { authApi } from '../services/api'
import { OAUTH_STATE_KEY, PKCE_VERIFIER_KEY } from '../utils/googleLogin'
import OAuthCallback from './OAuthCallback'

vi.mock('../services/api', () => ({ authApi: { exchangeGoogleCode: vi.fn() } }))

const exchange = vi.mocked(authApi.exchangeGoogleCode)

// 콜백 주소(?code · state)로 열고 그린다
function openCallback(search: string) {
  window.history.pushState({}, '', `/oauth/callback${search}`)
  return render(
    <MemoryRouter>
      <OAuthCallback />
    </MemoryRouter>
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
  sessionStorage.clear()
  sessionStorage.setItem(OAUTH_STATE_KEY, 'STATE1')
  sessionStorage.setItem(PKCE_VERIFIER_KEY, 'VERIFIER1')
  vi.spyOn(console, 'error').mockImplementation(() => {})
})
afterEach(() => {
  vi.restoreAllMocks()
  window.history.pushState({}, '', '/')
})

describe('OAuthCallback', () => {
  it('state 가 맞으면 인가 코드 · verifier · redirect_uri 를 user-service 로 보내 access token 만 저장한다', async () => {
    exchange.mockResolvedValue({ data: { accessToken: 'ACCESS', expiresIn: 3600 } } as never)

    openCallback('?code=CODE1&state=STATE1')

    await waitFor(() => expect(localStorage.getItem('accessToken')).toBe('ACCESS'))
    expect(exchange).toHaveBeenCalledWith({
      code: 'CODE1',
      codeVerifier: 'VERIFIER1',
      redirectUri: `${window.location.origin}/oauth/callback`,
    })
    expect(localStorage.getItem('refreshToken')).toBeNull()
    expect(sessionStorage.getItem(PKCE_VERIFIER_KEY)).toBeNull()
    expect(sessionStorage.getItem(OAUTH_STATE_KEY)).toBeNull()
  })

  it('state 가 다르면 교환하지 않고 막는다', async () => {
    openCallback('?code=CODE1&state=OTHER')

    expect(await screen.findByText(/state 불일치/)).toBeInTheDocument()
    expect(exchange).not.toHaveBeenCalled()
  })

  it('교환이 실패하면 서버 문구를 보여준다', async () => {
    exchange.mockRejectedValue(new AxiosError('fail', 'ERR', undefined, undefined, {
      status: 400, data: { detail: '구글 로그인 처리에 실패했습니다. 다시 로그인해주세요.' }, statusText: '', headers: {}, config: {},
    } as AxiosResponse))

    openCallback('?code=USED&state=STATE1')

    expect(await screen.findByText('구글 로그인 처리에 실패했습니다. 다시 로그인해주세요.')).toBeInTheDocument()
    expect(localStorage.getItem('accessToken')).toBeNull()
  })
})
