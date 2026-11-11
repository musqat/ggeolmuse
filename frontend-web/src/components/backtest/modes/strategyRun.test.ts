import { describe, it, expect, vi } from 'vitest'
import { strategyRun } from './strategyRun'

describe('strategyRun', () => {
  it('요청을 그 API 로 보내고 결과에 모드를 담으며, 이력은 전략 시뮬레이션으로 남긴다', async () => {
    const request = { symbol: 'AAPL', startDate: '2023-01-01' }
    const call = vi.fn().mockResolvedValue({ data: { symbol: 'AAPL', totalInvested: 1200000 } })

    const run = strategyRun({ request, call, mode: 'dca', fxMode: 'manual', failureMessage: 'DCA 전략 실행에 실패했습니다.' })

    expect(run).toMatchObject({
      historyType: 'STRATEGY_SIMULATION',
      params: request,
      fxMode: 'manual',
      failureMessage: 'DCA 전략 실행에 실패했습니다.',
    })
    await expect(run.execute()).resolves.toEqual({ symbol: 'AAPL', totalInvested: 1200000, mode: 'dca' })
    expect(call).toHaveBeenCalledWith(request)
  })

  it('예산이 있으면 결과에 그 예산을 같이 담는다', async () => {
    const call = vi.fn().mockResolvedValue({ data: { symbol: 'AAPL', totalInvested: 600000 } })

    const run = strategyRun({ request: {}, call, mode: 'conditional', fxMode: 'auto', failureMessage: '', budget: 1000000 })

    await expect(run.execute()).resolves.toEqual({ symbol: 'AAPL', totalInvested: 600000, mode: 'conditional', budget: 1000000 })
  })

  it('이동평균 기간이 있으면 결과에 그 기간을 같이 담는다', async () => {
    const call = vi.fn().mockResolvedValue({ data: { symbol: 'AAPL' } })

    const run = strategyRun({ request: {}, call, mode: 'ma-cross', fxMode: 'auto', failureMessage: '', windows: { short: 5, long: 120 } })

    await expect(run.execute()).resolves.toEqual({ symbol: 'AAPL', mode: 'ma-cross', windows: { short: 5, long: 120 } })
  })

  it('밴드 설정이 있으면 결과에 그 설정을 같이 담는다', async () => {
    const call = vi.fn().mockResolvedValue({ data: { symbol: 'SPY' } })
    const band = { window: 200, upperRate: 0.025, lowerRate: 0.03 }

    const run = strategyRun({ request: {}, call, mode: 'ma-band', fxMode: 'auto', failureMessage: '', band })

    await expect(run.execute()).resolves.toEqual({ symbol: 'SPY', mode: 'ma-band', band })
  })
})
