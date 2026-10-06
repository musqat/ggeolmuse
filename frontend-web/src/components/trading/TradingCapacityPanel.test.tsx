import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import TradingCapacityPanel from './TradingCapacityPanel'
import { tradeApi } from '../../services/api'
import type { PriceType } from '@/utils/priceUtils'

vi.mock('../../services/api', () => ({
  tradeApi: { canBuy: vi.fn(), canSell: vi.fn() },
}))

const canBuy = vi.mocked(tradeApi.canBuy)

beforeEach(() => {
  canBuy.mockReset()
  canBuy.mockResolvedValue({ data: { maxShares: 9, availableBalance: 90 } } as never)
})

function renderBuyPanel(priceType: PriceType, currentPrice: number) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <TradingCapacityPanel
        accountId={3}
        symbol="TSLL"
        tradeDate="2026-10-05"
        orderType="buy"
        priceType={priceType}
        currentPrice={currentPrice}
      />
    </QueryClientProvider>
  )
  return client
}

describe('TradingCapacityPanel', () => {
  it('고른 가격 유형을 매수 가능 수량 요청에 싣는다', async () => {
    renderBuyPanel('high', 9.1)

    await waitFor(() => expect(canBuy).toHaveBeenCalled())
    expect(canBuy).toHaveBeenCalledWith({
      accountId: '3',
      symbol: 'TSLL',
      tradeDate: '2026-10-05',
      priceType: 'HIGH',
      manualPrice: undefined,
    })
  })

  it('지정가는 입력한 가격을 같이 싣는다', async () => {
    renderBuyPanel('limit', 9)

    await waitFor(() => expect(canBuy).toHaveBeenCalled())
    expect(canBuy).toHaveBeenCalledWith(
      expect.objectContaining({ priceType: 'MANUAL', manualPrice: 9 })
    )
  })

  it('슬리피지·수수료를 넣은 수량이라고 알린다', async () => {
    renderBuyPanel('close', 8.97)

    expect(
      await screen.findByText('기준가 $8.97 · 슬리피지·수수료 포함 최대 9.00주 매수 가능')
    ).toBeInTheDocument()
  })

  it('주문 뒤 trade 쿼리를 무효화하면 매수 가능 수량을 다시 받는다', async () => {
    const client = renderBuyPanel('close', 8.97)
    await waitFor(() => expect(canBuy).toHaveBeenCalledTimes(1))

    await client.invalidateQueries({ queryKey: ['trade'] })

    await waitFor(() => expect(canBuy).toHaveBeenCalledTimes(2))
  })

  it('지정가에는 슬리피지가 없어 수수료만 넣었다고 알린다', async () => {
    renderBuyPanel('limit', 9)

    expect(
      await screen.findByText('지정가 $9.00 · 수수료 포함 최대 9.00주 매수 가능')
    ).toBeInTheDocument()
  })
})
