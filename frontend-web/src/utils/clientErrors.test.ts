import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { notify } from '@/components/common/notify'
import { listenForClientErrors, reportClientError } from './clientErrors'

// jsdom 에 없는 PromiseRejectionEvent 대신
function rejection(reason: unknown) {
  return Object.assign(new Event('unhandledrejection'), { reason })
}

let consoleError: ReturnType<typeof vi.spyOn>

beforeEach(() => {
  consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
  vi.spyOn(notify, 'error').mockImplementation(() => 0)
})
afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllEnvs()
})

describe('reportClientError', () => {
  it('콘솔에 남기고 개발 모드면 같은 문구는 한 번만 알림으로 보인다', () => {
    const error = new Error('차트 값이 비어 있음')

    reportClientError(error, 'render', '\n    at Chart')
    reportClientError(error, 'render')

    expect(consoleError).toHaveBeenCalledWith('화면 렌더 오류', error, '\n    at Chart')
    expect(consoleError).toHaveBeenCalledTimes(2)
    expect(notify.error).toHaveBeenCalledOnce()
    expect(notify.error).toHaveBeenCalledWith('화면 렌더 오류: 차트 값이 비어 있음')
  })

  it('운영 빌드면 콘솔에만 남긴다', () => {
    vi.stubEnv('DEV', false)

    reportClientError(new Error('운영 오류'), 'error')

    expect(consoleError).toHaveBeenCalledOnce()
    expect(notify.error).not.toHaveBeenCalled()
  })
})

describe('listenForClientErrors', () => {
  it('window 의 error · unhandledrejection 을 모으고, 되돌리면 더 받지 않는다', () => {
    const stop = listenForClientErrors(window)
    const thrown = new Error('누른 뒤 오류')

    window.dispatchEvent(new ErrorEvent('error', { error: thrown, message: thrown.message }))
    window.dispatchEvent(rejection('요청 실패'))
    stop()
    window.dispatchEvent(rejection('다음 실패'))

    expect(consoleError).toHaveBeenCalledWith('스크립트 오류', thrown)
    expect(consoleError).toHaveBeenCalledWith('처리 안 된 비동기 오류', '요청 실패')
    expect(consoleError).toHaveBeenCalledTimes(2)
  })

  it('오류 객체가 없는 error 이벤트는 문구로 남긴다', () => {
    const stop = listenForClientErrors(window)

    window.dispatchEvent(new ErrorEvent('error', { message: 'Script error.' }))
    stop()

    expect(consoleError).toHaveBeenCalledWith('스크립트 오류', 'Script error.')
  })
})
