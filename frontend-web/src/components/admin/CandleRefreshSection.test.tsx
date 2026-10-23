import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { marketAdminApi } from '@services/adminApi'
import { notify } from '@/components/common/notify'
import CandleRefreshSection from './CandleRefreshSection'

vi.mock('@services/adminApi', () => ({
  marketAdminApi: {
    findUnadjustedSymbols: vi.fn(), findLastRefreshAll: vi.fn(), startUnadjustedScan: vi.fn(),
    refreshCandles: vi.fn(), refreshAllCandles: vi.fn(),
  },
}))

const IDLE = { running: false, from: null, count: 0, symbols: null, finishedAt: null, tookMillis: 0, error: null }
const FINISHED = {
  running: false, from: '2026-09-24', count: 2, symbols: ['AAA', 'BBB'],
  finishedAt: '2026-10-20T03:00:00Z', tookMillis: 3000, error: null,
}

const dateInput = () => document.querySelector('input[type="date"]') as HTMLInputElement

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(marketAdminApi.findUnadjustedSymbols).mockResolvedValue(IDLE)
  vi.mocked(marketAdminApi.findLastRefreshAll).mockResolvedValue({ lastRunAt: null, published: 0 })
  vi.spyOn(notify, 'confirm').mockResolvedValue(true)
})
afterEach(() => {
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('CandleRefreshSection', () => {
  it('열면 지난 찾기 결과와 마지막 전체 다시 받기를 보인다', async () => {
    vi.mocked(marketAdminApi.findUnadjustedSymbols).mockResolvedValue(FINISHED)
    vi.mocked(marketAdminApi.findLastRefreshAll).mockResolvedValue({ lastRunAt: '2026-10-20T03:00:00', published: 1234 })
    render(<CandleRefreshSection />)

    expect(await screen.findByText('지난 찾기 · 2026-09-24 이후 2개')).toBeInTheDocument()
    expect(screen.getByText('AAA, BBB')).toBeInTheDocument()
    expect(await screen.findByText('마지막 전체 다시 받기 2026-10-20 03:00 · 1,234개 발행')).toBeInTheDocument()
  })

  it('찾기는 시작일로 탐색을 시작하고 끝날 때까지 5초마다 결과를 받는다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    vi.mocked(marketAdminApi.startUnadjustedScan).mockResolvedValue({ ...IDLE, running: true })
    render(<CandleRefreshSection />)
    await act(async () => {})
    vi.mocked(marketAdminApi.findUnadjustedSymbols).mockResolvedValue(FINISHED)

    await user.click(screen.getByRole('button', { name: '찾기' }))
    expect(marketAdminApi.startUnadjustedScan).toHaveBeenCalledWith(dateInput().value)
    expect(await screen.findByText('탐색이 도는 중입니다. 몇 분 걸립니다.')).toBeInTheDocument()

    await act(() => vi.advanceTimersByTimeAsync(5000))
    expect(screen.getByText('지난 찾기 · 2026-09-24 이후 2개')).toBeInTheDocument()
    const calls = vi.mocked(marketAdminApi.findUnadjustedSymbols).mock.calls.length
    await act(() => vi.advanceTimersByTimeAsync(10000))
    expect(marketAdminApi.findUnadjustedSymbols).toHaveBeenCalledTimes(calls)
  })

  it('탐색이 실패로 끝나면 실패 문구를 보인다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    vi.mocked(marketAdminApi.startUnadjustedScan).mockResolvedValue({ ...IDLE, running: true })
    render(<CandleRefreshSection />)
    await act(async () => {})
    vi.mocked(marketAdminApi.findUnadjustedSymbols).mockResolvedValue({ ...FINISHED, symbols: [], count: 0, error: 'boom' })

    await user.click(screen.getByRole('button', { name: '찾기' }))
    await act(() => vi.advanceTimersByTimeAsync(5000))

    expect(screen.getByText('탐색 실패: boom')).toBeInTheDocument()
  })

  it('다시 받기는 200개씩 나눠 1970-01-01 부터 보내고 발행 수를 더한다', async () => {
    const user = userEvent.setup()
    const symbols = Array.from({ length: 250 }, (_, i) => `S${i}`)
    vi.mocked(marketAdminApi.findUnadjustedSymbols).mockResolvedValue({ ...FINISHED, count: 250, symbols })
    vi.mocked(marketAdminApi.refreshCandles)
      .mockResolvedValueOnce({ published: 200 } as never)
      .mockResolvedValueOnce({ published: 50 } as never)
    render(<CandleRefreshSection />)

    await user.click(await screen.findByRole('button', { name: '250개 다시 받기' }))

    expect(await screen.findByText(/^250개 발행 완료/)).toBeInTheDocument()
    expect(marketAdminApi.refreshCandles).toHaveBeenNthCalledWith(1, symbols.slice(0, 200), '1970-01-01')
    expect(marketAdminApi.refreshCandles).toHaveBeenNthCalledWith(2, symbols.slice(200), '1970-01-01')
  })

  it('확인을 취소하면 보내지 않는다', async () => {
    const user = userEvent.setup()
    vi.mocked(notify.confirm).mockResolvedValue(false)
    vi.mocked(marketAdminApi.findUnadjustedSymbols).mockResolvedValue(FINISHED)
    render(<CandleRefreshSection />)

    await user.click(await screen.findByRole('button', { name: '2개 다시 받기' }))

    expect(notify.confirm).toHaveBeenCalled()
    expect(marketAdminApi.refreshCandles).not.toHaveBeenCalled()
  })

  it('전체 다시 받기는 발행 수를 보인다', async () => {
    const user = userEvent.setup()
    vi.mocked(marketAdminApi.refreshAllCandles).mockResolvedValue({ lastRunAt: '2026-10-24T02:00:00', published: 14795 })
    render(<CandleRefreshSection />)

    await user.click(screen.getByRole('button', { name: '전체 다시 받기' }))

    expect(await screen.findByText(/^14,795개 발행을 시작했습니다/)).toBeInTheDocument()
    expect(screen.getByText('마지막 전체 다시 받기 2026-10-24 02:00 · 14,795개 발행')).toBeInTheDocument()
  })
})
