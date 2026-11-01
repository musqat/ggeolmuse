import { describe, it, expect } from 'vitest'
import { AxiosError, AxiosHeaders } from 'axios'
import { getApiErrorCode, getApiErrorMessage, getApiErrorStatus, getServerMessage } from './apiError'

/** axios 오류 생성(isAxiosError 가 참이 되게 AxiosError 인스턴스) */
function axiosErrorWith(status: number, data: unknown, message = 'Request failed') {
  const config = { headers: new AxiosHeaders() }
  return new AxiosError(message, 'ERR_BAD_RESPONSE', config, null, {
    status,
    statusText: '',
    headers: {},
    config,
    data,
  })
}

describe('getServerMessage', () => {
  it('서버가 준 detail · message 만 쓰고 없으면 비운다', () => {
    expect(getServerMessage(axiosErrorWith(409, { detail: '이미 사용 중인 닉네임입니다.' }))).toBe('이미 사용 중인 닉네임입니다.')
    expect(getServerMessage(axiosErrorWith(409, { message: '이미 사용 중인 이메일입니다.' }))).toBe('이미 사용 중인 이메일입니다.')
    expect(getServerMessage(axiosErrorWith(409, {}, 'Request failed with status code 409'))).toBeUndefined()
    expect(getServerMessage(new Error('boom'))).toBeUndefined()
  })
})

describe('getApiErrorMessage', () => {
  it('서버가 준 detail 을 쓴다', () => {
    const err = axiosErrorWith(400, { detail: '매수일이 매도일보다 뒤입니다' })
    expect(getApiErrorMessage(err, '기본값')).toBe('매수일이 매도일보다 뒤입니다')
  })

  it('detail 이 없으면 message 를 쓴다', () => {
    // 백엔드가 ProblemDetail 을 안 쓰는 경로는 message
    const err = axiosErrorWith(400, { message: '이름은 비워둘 수 없습니다' })
    expect(getApiErrorMessage(err, '기본값')).toBe('이름은 비워둘 수 없습니다')
  })

  it('detail 과 message 가 있으면 detail 이 이긴다', () => {
    const err = axiosErrorWith(400, { detail: '이쪽', message: '저쪽' })
    expect(getApiErrorMessage(err, '기본값')).toBe('이쪽')
  })

  it('본문이 비면 axios 자신의 message 로 떨어진다', () => {
    const err = axiosErrorWith(500, {}, 'Network Error')
    expect(getApiErrorMessage(err, '기본값')).toBe('Network Error')
  })

  it('axios 오류가 아닌 Error 는 그 message 를 쓴다', () => {
    expect(getApiErrorMessage(new Error('JSON 파싱 실패'), '기본값')).toBe('JSON 파싱 실패')
  })

  it('Error 도 아닌 값이 던져지면 기본값을 쓴다', () => {
    // catch 는 무엇이든 잡음(문자열이나 undefined 도)
    expect(getApiErrorMessage('그냥 문자열', '기본값')).toBe('기본값')
    expect(getApiErrorMessage(undefined, '기본값')).toBe('기본값')
    expect(getApiErrorMessage(null, '기본값')).toBe('기본값')
  })

  it('message 가 빈 문자열이면 기본값을 쓴다', () => {
    expect(getApiErrorMessage(new Error(''), '기본값')).toBe('기본값')
  })
})

describe('getApiErrorCode', () => {
  it('본문의 code 를 준다', () => {
    expect(getApiErrorCode(axiosErrorWith(404, { code: 'symbol_not_found', message: '없음' }))).toBe('symbol_not_found')
  })

  it('code 가 없거나 본문이 객체가 아니면 undefined', () => {
    expect(getApiErrorCode(axiosErrorWith(503, { error: '서비스 사용 불가' }))).toBeUndefined()
    expect(getApiErrorCode(axiosErrorWith(502, '<html>'))).toBeUndefined()
    expect(getApiErrorCode(new Error('boom'))).toBeUndefined()
  })
})

describe('getApiErrorStatus', () => {
  it('응답이 있으면 상태 코드를 준다', () => {
    expect(getApiErrorStatus(axiosErrorWith(403, {}))).toBe(403)
  })

  it('axios 오류가 아니면 undefined 를 준다', () => {
    // 상태가 없으면 undefined(로그인 화면이 401 · 403 을 갈라 써서 0 같은 값은 오판)
    expect(getApiErrorStatus(new Error('boom'))).toBeUndefined()
    expect(getApiErrorStatus('문자열')).toBeUndefined()
  })
})
