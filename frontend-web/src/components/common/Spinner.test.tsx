import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { PageSpinner } from './Spinner'

describe('PageSpinner', () => {
  it('불러오는 중 표시를 보인다', () => {
    render(<PageSpinner />)

    expect(screen.getByRole('status', { name: '불러오는 중' })).toBeInTheDocument()
  })
})
