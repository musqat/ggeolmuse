import { describe, it, expect } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { StockLogo } from './StockLogo'

const img = (container: HTMLElement) => container.querySelector('img') as HTMLImageElement

describe('StockLogo', () => {
  it('티커로 FMP 그림을 늦게 받고, 장식이라 대체 글은 비우며 리퍼러를 보내지 않는다', () => {
    const { container } = render(<StockLogo symbol="AAPL" />)

    expect(img(container)).toHaveAttribute('src', 'https://financialmodelingprep.com/image-stock/AAPL.png')
    expect(img(container)).toHaveAttribute('alt', '')
    expect(img(container)).toHaveAttribute('loading', 'lazy')
    expect(img(container)).toHaveAttribute('referrerpolicy', 'no-referrer')
  })

  it.each(['BRK.B', 'BRK/B'])('%s 처럼 점 · 빗금이 든 티커는 하이픈으로 바꿔 부른다', (symbol) => {
    const { container } = render(<StockLogo symbol={symbol} />)

    expect(img(container)).toHaveAttribute('src', 'https://financialmodelingprep.com/image-stock/BRK-B.png')
  })

  it('그림을 못 받으면 첫 글자를 보인다', () => {
    const { container } = render(<StockLogo symbol="ZZZQ" />)

    fireEvent.error(img(container))

    expect(img(container)).toBeNull()
    expect(screen.getByText('Z')).toBeInTheDocument()
  })
})
