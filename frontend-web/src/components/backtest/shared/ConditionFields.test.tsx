import { describe, it, expect, vi } from 'vitest'
import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConditionFields } from './StrategyFields'
import type { PriceDirection } from '@services/api'

// 방향 · 비율 상태를 가진 ConditionFields
const Harness = ({ onDirection = vi.fn() }: { onDirection?: (direction: PriceDirection) => void }) => {
  const [direction, setDirection] = useState<PriceDirection>('DROP')
  const [value, setValue] = useState('5')
  return (
    <ConditionFields
      direction={direction}
      onDirectionChange={(next) => {
        onDirection(next)
        setDirection(next)
      }}
      value={value}
      onChange={setValue}
    />
  )
}

describe('ConditionFields', () => {
  it('하락으로 시작하고 하락률 칸과 안내를 보인다', () => {
    render(<Harness />)

    expect(screen.getByRole('button', { name: '하락' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByText('하락률 (%)')).toBeInTheDocument()
    expect(screen.getByText('전날 종가보다 이만큼 떨어지면 그 날짜에 매수')).toBeInTheDocument()
  })

  it('탭을 고르면 방향을 넘기고 비율 이름 · 안내를 바꾼다', async () => {
    const onDirection = vi.fn()
    render(<Harness onDirection={onDirection} />)

    await userEvent.click(screen.getByRole('button', { name: '상승' }))
    expect(onDirection).toHaveBeenLastCalledWith('RISE')
    expect(screen.getByText('상승률 (%)')).toBeInTheDocument()
    expect(screen.getByRole('spinbutton', { name: '상승률 (%)' })).toHaveValue(5)
    expect(screen.getByText('전날 종가보다 이만큼 오르면 그 날짜에 매수')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '둘 다' }))
    expect(screen.getByRole('button', { name: '둘 다' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByText('변동률 (%)')).toBeInTheDocument()
    expect(screen.getByText('전날 종가보다 이만큼 떨어지거나 오르면 그 날짜에 매수')).toBeInTheDocument()
  })
})
