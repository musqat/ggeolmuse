import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import OrderSummaryPanel from './OrderSummaryPanel'
import type { PriceType } from '@/utils/priceUtils'

function renderPanel(priceType: PriceType) {
  render(
    <OrderSummaryPanel
      selectedStock="TSLL"
      tradeDate="2026-10-05"
      quantity="10"
      priceType={priceType}
      currentPrice={8.97}
      totalAmount={89.7}
    />
  )
}

describe('OrderSummaryPanel', () => {
  it('가격 유형을 붙인 기준가와 주문 금액을 보여준다', () => {
    renderPanel('close')

    expect(screen.getByText('기준가 (종가)')).toBeInTheDocument()
    expect(screen.getByText('$8.97')).toBeInTheDocument()
    expect(screen.getByText('주문 금액')).toBeInTheDocument()
    expect(screen.getByText('$89.70')).toBeInTheDocument()
    expect(screen.queryByText('체결가')).not.toBeInTheDocument()
  })

  it('시가·고가·저가·종가는 슬리피지와 수수료가 붙는다고 알린다', () => {
    renderPanel('open')

    expect(screen.getByText('기준가 (시가)')).toBeInTheDocument()
    expect(
      screen.getByText('슬리피지·수수료가 붙어 실제 금액은 달라질 수 있습니다.')
    ).toBeInTheDocument()
  })

  it('지정가는 슬리피지 없이 수수료만 알린다', () => {
    renderPanel('limit')

    expect(screen.getByText('지정가')).toBeInTheDocument()
    expect(screen.getByText('수수료가 붙어 실제 금액은 달라질 수 있습니다.')).toBeInTheDocument()
  })
})
