import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { notify } from '@/components/common/notify'
import { listenForChunkLoadErrors, listenForClientErrors, reportClientError } from './clientErrors'

// jsdom 에 없는 PromiseRejectionEvent 대신
function rejection(reason: unknown) {
  return Object.assign(new Event('unhandledrejection'), { reason })
}

let consoleError: ReturnType<typeof vi.spyOn>
let sent: ReturnType<typeof vi.spyOn>

beforeEach(() => {
  consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
  sent = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 204 }))
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

  it('운영 빌드면 알림 없이 콘솔에 남긴다', () => {
    vi.stubEnv('DEV', false)

    reportClientError(new Error('운영 오류'), 'error')

    expect(consoleError).toHaveBeenCalledOnce()
    expect(notify.error).not.toHaveBeenCalled()
  })

  it('운영 빌드면 출처 · 메시지 · 경로(쿼리 없이)를 수집 주소로 보내고 같은 메시지는 한 번만 보낸다', () => {
    vi.stubEnv('DEV', false)
    window.history.pushState({}, '', '/portfolio?accountId=3')

    reportClientError(new Error('보유 표 오류'), 'render')
    reportClientError(new Error('보유 표 오류'), 'render')

    expect(sent).toHaveBeenCalledOnce()
    const [url, init] = sent.mock.calls[0]
    const params = new URL(String(url), 'http://localhost').searchParams
    expect(String(url).startsWith('/client-error?')).toBe(true)
    expect(Object.fromEntries(params)).toEqual({ src: 'render', msg: '보유 표 오류', path: '/portfolio' })
    expect(init).toEqual({ method: 'GET', keepalive: true, credentials: 'omit' })
  })

  it('수집으로 보내는 메시지는 300자까지만', () => {
    vi.stubEnv('DEV', false)

    reportClientError(new Error('긴'.repeat(400)), 'error')

    const msg = new URL(String(sent.mock.calls[0][0]), 'http://localhost').searchParams.get('msg')
    expect(msg).toHaveLength(300)
  })

  it('수집 요청이 실패해도 다시 오류를 내지 않는다', async () => {
    vi.stubEnv('DEV', false)
    sent.mockRejectedValue(new TypeError('offline'))

    expect(() => reportClientError(new Error('끊긴 뒤 오류'), 'error')).not.toThrow()
    await Promise.resolve()
  })

  it('개발 모드면 수집 주소로 보내지 않는다', () => {
    reportClientError(new Error('개발 중 오류'), 'error')

    expect(sent).not.toHaveBeenCalled()
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

describe('listenForChunkLoadErrors', () => {
  it('새 배포로 옛 화면 조각을 못 받으면 세션에 한 번만 새로고침한다', () => {
    const storage = new Map<string, string>()
    const reload = vi.fn()
    const stop = listenForChunkLoadErrors(window, {
      getItem: (key) => storage.get(key) ?? null,
      setItem: (key, value) => { storage.set(key, value) },
    }, reload)

    const first = new Event('vite:preloadError', { cancelable: true })
    window.dispatchEvent(first)
    window.dispatchEvent(new Event('vite:preloadError', { cancelable: true }))
    stop()

    expect(first.defaultPrevented).toBe(true)
    expect(reload).toHaveBeenCalledOnce()
  })
})
