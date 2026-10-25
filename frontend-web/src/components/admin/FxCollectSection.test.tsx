import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, type AxiosResponse } from 'axios'
import { marketAdminApi, type FxCollectResponse } from '@services/adminApi'
import { notify } from '@/components/common/notify'
import FxCollectSection from './FxCollectSection'

vi.mock('@services/adminApi', () => ({
  marketAdminApi: { findFxCollect: vi.fn(), startFxCollect: vi.fn() },
}))

const IDLE: FxCollectResponse = {
  missingCount: 1, missingDates: ['2026-06-25'], running: false, mode: null, startedAt: null, finishedAt: null,
  total: 0, processed: 0, filled: 0, failed: 0, lastDate: null, stoppedReason: null, message: null,
}
const RUNNING: FxCollectResponse = {
  ...IDLE, running: true, mode: 'MISSING', startedAt: '2026-10-25T03:00:00', total: 3, processed: 1, filled: 1,
}
const FINISHED: FxCollectResponse = {
  ...IDLE, missingCount: 0, missingDates: [], mode: 'MISSING', startedAt: '2026-10-25T03:00:00',
  finishedAt: '2026-10-25T03:01:00', total: 3, processed: 3, filled: 3,
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(marketAdminApi.findFxCollect).mockResolvedValue(IDLE)
  vi.spyOn(notify, 'confirm').mockResolvedValue(true)
})
afterEach(() => {
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('FxCollectSection', () => {
  it('열면 빈 날 수와 최근 빈 날을 보이고, 기록이 없으면 그렇다고 보인다', async () => {
    render(<FxCollectSection />)

    expect(await screen.findByText('빈 날 1일')).toBeInTheDocument()
    expect(screen.getByText('최근 빈 날: 2026-06-25')).toBeInTheDocument()
    expect(screen.getByText('마지막 수집 기록 없음')).toBeInTheDocument()
  })

  it('마지막 수집의 시각 · 모드 · 채운 수와 멈춘 이유를 보인다', async () => {
    vi.mocked(marketAdminApi.findFxCollect).mockResolvedValue({
      ...FINISHED, mode: 'ALL', failed: 10, stoppedReason: '원천이 연속 10 영업일 환율을 주지 않아 멈춤(마지막 처리일 2014-01-14)',
    })
    render(<FxCollectSection />)

    expect(await screen.findByText('마지막 수집 2026-10-25 03:01 · 전체 다시 받기 · 채움 3 · 실패 10')).toBeInTheDocument()
    expect(screen.getByText('원천이 연속 10 영업일 환율을 주지 않아 멈춤(마지막 처리일 2014-01-14)')).toBeInTheDocument()
  })

  it('빠진 날 채우기는 확인 없이 시작하고 끝날 때까지 5초마다 진행을 받는다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    vi.mocked(marketAdminApi.startFxCollect).mockResolvedValue(RUNNING)
    render(<FxCollectSection />)
    await act(async () => {})
    vi.mocked(marketAdminApi.findFxCollect).mockResolvedValue(FINISHED)

    await user.click(screen.getByRole('button', { name: '빠진 날 채우기' }))

    expect(notify.confirm).not.toHaveBeenCalled()
    expect(marketAdminApi.startFxCollect).toHaveBeenCalledWith('missing')
    expect(await screen.findByText('빠진 날 채우기 진행 중 · 1 / 3 · 채움 1 · 실패 0')).toBeInTheDocument()

    await act(() => vi.advanceTimersByTimeAsync(5000))
    expect(screen.getByText('마지막 수집 2026-10-25 03:01 · 빠진 날 채우기 · 채움 3 · 실패 0')).toBeInTheDocument()
    const calls = vi.mocked(marketAdminApi.findFxCollect).mock.calls.length
    await act(() => vi.advanceTimersByTimeAsync(10000))
    expect(vi.mocked(marketAdminApi.findFxCollect).mock.calls.length).toBe(calls)
  })

  it('전체 다시 받기는 확인 창에서 계속할 때만 시작한다', async () => {
    const user = userEvent.setup()
    vi.mocked(marketAdminApi.startFxCollect).mockResolvedValue({ ...RUNNING, mode: 'ALL' })
    vi.mocked(notify.confirm).mockResolvedValueOnce(false)
    render(<FxCollectSection />)
    await screen.findByText('빈 날 1일')

    await user.click(screen.getByRole('button', { name: '전체 다시 받기' }))
    expect(marketAdminApi.startFxCollect).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: '전체 다시 받기' }))
    expect(marketAdminApi.startFxCollect).toHaveBeenCalledWith('all')
  })

  it('이미 진행 중이라 막히면 서버 문구를 보인다', async () => {
    const user = userEvent.setup()
    vi.mocked(marketAdminApi.startFxCollect).mockRejectedValue(new AxiosError('fail', 'ERR', undefined, undefined, {
      status: 409, data: { message: '이미 환율 수집이 진행 중입니다. 끝난 뒤 다시 시도해 주세요.' },
      statusText: '', headers: {}, config: {},
    } as AxiosResponse))
    vi.spyOn(console, 'error').mockImplementation(() => {})
    render(<FxCollectSection />)
    await screen.findByText('빈 날 1일')

    await user.click(screen.getByRole('button', { name: '빠진 날 채우기' }))

    expect(await screen.findByText('이미 환율 수집이 진행 중입니다. 끝난 뒤 다시 시도해 주세요.')).toBeInTheDocument()
  })
})
