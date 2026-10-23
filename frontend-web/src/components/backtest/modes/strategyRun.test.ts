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
})
