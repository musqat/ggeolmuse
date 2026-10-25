import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { PageSpinner, Spinner } from './Spinner'

describe('PageSpinner', () => {
  it('불러오는 중 표시를 보인다', () => {
    render(<PageSpinner />)

    expect(screen.getByRole('status', { name: '불러오는 중' })).toBeInTheDocument()
  })
})

describe('Spinner', () => {
  it.each([
    ['sm', 'h-6'],
    ['md', 'h-8'],
    ['lg', 'h-12'],
  ] as const)('%s 크기는 %s 원이다', (size, height) => {
    render(<Spinner size={size} />)

    expect(screen.getByRole('status', { name: '불러오는 중' })).toHaveClass(height)
  })

  it('크기를 고르지 않으면 lg 다', () => {
    render(<Spinner />)

    expect(screen.getByRole('status')).toHaveClass('h-12', 'w-12')
  })
})
