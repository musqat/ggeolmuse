import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { SimpleChart } from './SimpleChart'
import { stockApi } from '../../../services/api'

vi.mock('../../../services/api', () => ({
  stockApi: { getOHLCData: vi.fn(), getExchangeRatesBulk: vi.fn() },
}))

const getOHLCData = vi.mocked(stockApi.getOHLCData)

function renderChart(endDate?: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <SimpleChart
        symbol="AAPL"
        purchaseDate="2024-01-15"
        shares={1}
        investmentAmount={300000}
        endDate={endDate}
      />
    </QueryClientProvider>
  )
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-10-09T12:00:00+09:00'))
  getOHLCData.mockReset()
  getOHLCData.mockResolvedValue({ data: [] } as never)
})

afterEach(() => {
  vi.useRealTimers()
})

describe('SimpleChart 가격 구간', () => {
  it('평가일이 있으면 그날까지 가격을 받는다', async () => {
    renderChart('2024-06-14')

    await waitFor(() => expect(getOHLCData).toHaveBeenCalled())
    expect(getOHLCData.mock.calls[0][2]).toBe('2024-06-14')
  })

  it('평가일이 없으면 오늘까지 받는다', async () => {
    renderChart()

    await waitFor(() => expect(getOHLCData).toHaveBeenCalled())
    expect(getOHLCData.mock.calls[0][2]).toBe('2026-10-09')
  })
})
