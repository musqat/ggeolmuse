import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import {
  tokenManager,
  checkAuthStatus,
  handleTokenExpiration,
  TOKEN_STORAGE_KEY,
  REFRESH_TOKEN_STORAGE_KEY,
} from './auth'

const NOW_SEC = 1_800_000_000

function makeToken(payload: Record<string, unknown>): string {
  return `header.${btoa(JSON.stringify(payload))}.signature`
}

beforeEach(() => {
  localStorage.clear()
})

describe('토큰 저장/조회', () => {
  it('access 토큰 왕복', () => {
    expect(tokenManager.getToken()).toBeNull()
    tokenManager.setToken('abc')
    expect(tokenManager.getToken()).toBe('abc')
  })

  it('refresh 토큰 왕복', () => {
    expect(tokenManager.getRefreshToken()).toBeNull()
    tokenManager.setRefreshToken('xyz')
    expect(tokenManager.getRefreshToken()).toBe('xyz')
  })

  it('setTokens 는 두 개를 각자 키에 넣는다', () => {
    tokenManager.setTokens('access', 'refresh')
    expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBe('access')
    expect(localStorage.getItem(REFRESH_TOKEN_STORAGE_KEY)).toBe('refresh')
  })

  it('removeToken 은 access 만 지운다', () => {
    tokenManager.setTokens('access', 'refresh')
    tokenManager.removeToken()
    expect(tokenManager.getToken()).toBeNull()
    expect(tokenManager.getRefreshToken()).toBe('refresh')
  })

  it('removeTokens 는 둘 다 지운다', () => {
    tokenManager.setTokens('access', 'refresh')
    tokenManager.removeTokens()
    expect(tokenManager.getToken()).toBeNull()
    expect(tokenManager.getRefreshToken()).toBeNull()
  })
})

describe('decodeToken', () => {
  it('payload 를 객체로 돌려준다', () => {
    const token = makeToken({ sub: 'user-1', email: 'a@test.com', exp: NOW_SEC })
    expect(tokenManager.decodeToken(token)).toEqual({
      sub: 'user-1',
      email: 'a@test.com',
      exp: NOW_SEC,
    })
  })

  it('점이 없으면 null', () => {
    expect(tokenManager.decodeToken('notajwt')).toBeNull()
  })

  it('빈 문자열이면 null', () => {
    expect(tokenManager.decodeToken('')).toBeNull()
  })

  it('base64 가 깨졌으면 null', () => {
    expect(tokenManager.decodeToken('header.@@@@.sig')).toBeNull()
  })

  it('base64 는 맞는데 JSON 이 아니면 null', () => {
    expect(tokenManager.decodeToken(`header.${btoa('not json')}.sig`)).toBeNull()
  })
})

describe('isTokenExpired', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(NOW_SEC * 1000)
  })
  afterEach(() => vi.useRealTimers())

  it('exp 가 지났으면 만료', () => {
    expect(tokenManager.isTokenExpired(makeToken({ exp: NOW_SEC - 1 }))).toBe(true)
  })

  it('exp 가 남았으면 유효', () => {
    expect(tokenManager.isTokenExpired(makeToken({ exp: NOW_SEC + 60 }))).toBe(false)
  })

  it('exp === 현재 초면 아직 유효', () => {
    // 비교가 < 라 같은 초는 통과한다
    expect(tokenManager.isTokenExpired(makeToken({ exp: NOW_SEC }))).toBe(false)
  })

  it('exp 가 없으면 만료 취급', () => {
    expect(tokenManager.isTokenExpired(makeToken({ sub: 'user-1' }))).toBe(true)
  })

  it('디코딩 실패하면 만료 취급', () => {
    expect(tokenManager.isTokenExpired('garbage')).toBe(true)
  })

  it('exp 가 0 이면 만료 취급', () => {
    // 0 은 falsy 라 exp 없음과 같은 경로를 탄다
    expect(tokenManager.isTokenExpired(makeToken({ exp: 0 }))).toBe(true)
  })
})

describe('isCurrentTokenValid / checkAuthStatus', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(NOW_SEC * 1000)
  })
  afterEach(() => vi.useRealTimers())

  it('저장된 토큰이 없으면 false', () => {
    expect(tokenManager.isCurrentTokenValid()).toBe(false)
  })

  it('만료 안 된 토큰이면 true', () => {
    tokenManager.setToken(makeToken({ exp: NOW_SEC + 60 }))
    expect(tokenManager.isCurrentTokenValid()).toBe(true)
  })

  it('만료된 토큰이면 false', () => {
    tokenManager.setToken(makeToken({ exp: NOW_SEC - 60 }))
    expect(tokenManager.isCurrentTokenValid()).toBe(false)
  })

  it('깨진 토큰이면 false', () => {
    tokenManager.setToken('garbage')
    expect(tokenManager.isCurrentTokenValid()).toBe(false)
  })

  it('checkAuthStatus 는 isCurrentTokenValid 와 같은 결과', () => {
    tokenManager.setToken(makeToken({ exp: NOW_SEC + 60 }))
    expect(checkAuthStatus()).toBe(true)
    tokenManager.setToken(makeToken({ exp: NOW_SEC - 60 }))
    expect(checkAuthStatus()).toBe(false)
  })
})

describe('getUserFromToken', () => {
  it('email 과 sub 를 뽑는다', () => {
    const token = makeToken({ sub: 'user-1', email: 'a@test.com', role: 'ADMIN' })
    expect(tokenManager.getUserFromToken(token)).toEqual({
      sub: 'user-1',
      email: 'a@test.com',
    })
  })

  it('없는 필드는 undefined', () => {
    expect(tokenManager.getUserFromToken(makeToken({ sub: 'user-1' }))).toEqual({
      sub: 'user-1',
      email: undefined,
    })
  })

  it('디코딩 실패하면 null', () => {
    expect(tokenManager.getUserFromToken('garbage')).toBeNull()
  })
})

describe('handleTokenExpiration', () => {
  it('access 만 지우고 refresh 는 남긴다', () => {
    // 재발급 흐름 때문인지 의도인지 확인 필요. 현재 동작을 고정해둔다
    tokenManager.setTokens('access', 'refresh')
    handleTokenExpiration()
    expect(tokenManager.getToken()).toBeNull()
    expect(tokenManager.getRefreshToken()).toBe('refresh')
  })
})
