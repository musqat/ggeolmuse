import { describe, it, expect } from 'vitest'
import { AxiosError, AxiosHeaders } from 'axios'
import { getApiErrorMessage, getApiErrorStatus, getOAuthErrorMessage } from './apiError'

/** axios 오류를 만든다. isAxiosError 가 참이 되려면 AxiosError 인스턴스여야 한다. */
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

describe('getApiErrorMessage', () => {
  it('서버가 준 detail 을 쓴다', () => {
    const err = axiosErrorWith(400, { detail: '매수일이 매도일보다 뒤입니다' })
    expect(getApiErrorMessage(err, '기본값')).toBe('매수일이 매도일보다 뒤입니다')
  })

  it('detail 이 없으면 message 를 쓴다', () => {
    // 백엔드가 ProblemDetail 을 안 쓰는 경로는 message 로 준다
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
    // catch 는 무엇이든 잡는다. 문자열이나 undefined 가 올 수 있다.
    expect(getApiErrorMessage('그냥 문자열', '기본값')).toBe('기본값')
    expect(getApiErrorMessage(undefined, '기본값')).toBe('기본값')
    expect(getApiErrorMessage(null, '기본값')).toBe('기본값')
  })

  it('message 가 빈 문자열이면 기본값을 쓴다', () => {
    expect(getApiErrorMessage(new Error(''), '기본값')).toBe('기본값')
  })
})

describe('getApiErrorStatus', () => {
  it('응답이 있으면 상태 코드를 준다', () => {
    expect(getApiErrorStatus(axiosErrorWith(403, {}))).toBe(403)
  })

  it('axios 오류가 아니면 undefined 를 준다', () => {
    // 로그인 화면이 401·403 을 갈라 쓰므로, 아닌 값에 0 같은 걸 주면 안 된다
    expect(getApiErrorStatus(new Error('boom'))).toBeUndefined()
    expect(getApiErrorStatus('문자열')).toBeUndefined()
  })
})

describe('getOAuthErrorMessage', () => {
  it('error_description 을 쓴다', () => {
    const err = axiosErrorWith(400, { error_description: 'code_verifier 가 맞지 않습니다' })
    expect(getOAuthErrorMessage(err, '기본값')).toBe('code_verifier 가 맞지 않습니다')
  })

  it('없으면 기본값을 쓴다', () => {
    expect(getOAuthErrorMessage(axiosErrorWith(400, { detail: '다른 필드' }), '기본값')).toBe('기본값')
    expect(getOAuthErrorMessage(new Error('boom'), '기본값')).toBe('기본값')
  })
})
