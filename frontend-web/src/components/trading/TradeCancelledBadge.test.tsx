import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import TradeCancelledBadge from './TradeCancelledBadge'

describe('TradeCancelledBadge', () => {
  it('취소됨과 사유를 보여 준다', () => {
    render(<TradeCancelledBadge reason="INSUFFICIENT_USD_BALANCE" />)
    expect(screen.getByText('취소됨 · 잔액 부족')).toBeTruthy()
  })
})
