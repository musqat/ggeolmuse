import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { TotalAssetsSummary } from './TotalAssetsSummary'

const format = (amount: number) => `${amount}원`

describe('TotalAssetsSummary', () => {
  it('주식 평가액을 못 받았으면 총자산이 현금만이라고 알린다', () => {
    render(<TotalAssetsSummary totalAssets={1000} accountCount={1} currentExchangeRate={1350} formatBalance={format} stocksUnavailable />)

    expect(screen.getByText('주식 평가액을 불러오지 못해 현금만 셌습니다')).toBeInTheDocument()
  })

  it('주식 평가액을 받았으면 안내가 없다', () => {
    render(<TotalAssetsSummary totalAssets={1000} accountCount={1} currentExchangeRate={1350} formatBalance={format} />)

    expect(screen.queryByText('주식 평가액을 불러오지 못해 현금만 셌습니다')).not.toBeInTheDocument()
  })
})
