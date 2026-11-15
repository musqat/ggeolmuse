import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { DividendUnavailableNotice } from './DividendUnavailableNotice'

describe('DividendUnavailableNotice', () => {
  it('배당 기록을 받지 못한 종목이 없으면 그리지 않음', () => {
    const { container: none } = render(<DividendUnavailableNotice symbols={undefined} />)
    const { container: empty } = render(<DividendUnavailableNotice symbols={[]} />)

    expect(none).toBeEmptyDOMElement()
    expect(empty).toBeEmptyDOMElement()
  })

  it('배당 기록을 받지 못한 종목이 있으면 배당 없이 계산한 종목을 한 줄로 보임', () => {
    render(<DividendUnavailableNotice symbols={['QQQ', 'SPY']} />)

    expect(screen.getByRole('note')).toHaveTextContent('배당 기록을 받지 못해 배당 없이 계산한 종목: QQQ · SPY')
  })
})
