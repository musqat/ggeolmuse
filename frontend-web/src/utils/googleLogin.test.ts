import { describe, it, expect, beforeEach } from 'vitest'
import {
  buildGoogleLoginUrl,
  createCodeChallenge,
  createCodeVerifier,
  keycloakTokenUrl,
  OAUTH_STATE_KEY,
  PKCE_VERIFIER_KEY,
} from './googleLogin'

describe('PKCE', () => {
  it('code_challenge 는 RFC 7636 부록 B 예시 값과 같다', async () => {
    expect(await createCodeChallenge('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk')).toBe(
      'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM'
    )
  })

  it('code_verifier 는 64자이고 unreserved 문자만 쓴다', () => {
    const verifier = createCodeVerifier()
    expect(verifier).toHaveLength(64)
    expect(verifier).toMatch(/^[A-Za-z0-9\-._~]+$/)
    expect(createCodeVerifier()).not.toBe(verifier)
  })
})

describe('buildGoogleLoginUrl', () => {
  beforeEach(() => sessionStorage.clear())

  it('Keycloak 인증 주소에 PKCE · state · 구글 IdP 를 싣고 verifier · state 를 저장한다', async () => {
    const url = new URL(await buildGoogleLoginUrl('https://app.example'))
    const verifier = sessionStorage.getItem(PKCE_VERIFIER_KEY)
    const state = sessionStorage.getItem(OAUTH_STATE_KEY)

    expect(url.origin + url.pathname).toBe('https://app.example/auth/realms/muscathan/protocol/openid-connect/auth')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      client_id: 'ggeolmuse-frontend',
      response_type: 'code',
      scope: 'openid email profile',
      redirect_uri: 'https://app.example/oauth/callback',
      code_challenge: await createCodeChallenge(verifier!),
      code_challenge_method: 'S256',
      kc_idp_hint: 'google',
      state,
    })
    expect(verifier).toHaveLength(64)
    expect(state).toBeTruthy()
  })

  it('부를 때마다 verifier · state 를 새로 만든다', async () => {
    await buildGoogleLoginUrl('https://app.example')
    const first = [sessionStorage.getItem(PKCE_VERIFIER_KEY), sessionStorage.getItem(OAUTH_STATE_KEY)]
    await buildGoogleLoginUrl('https://app.example')
    expect(sessionStorage.getItem(PKCE_VERIFIER_KEY)).not.toBe(first[0])
    expect(sessionStorage.getItem(OAUTH_STATE_KEY)).not.toBe(first[1])
  })

  it('토큰 주소는 같은 realm 의 token 이다', () => {
    expect(keycloakTokenUrl('https://app.example')).toBe(
      'https://app.example/auth/realms/muscathan/protocol/openid-connect/token'
    )
  })
})
