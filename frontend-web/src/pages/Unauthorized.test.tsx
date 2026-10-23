import { describe, it, expect } from 'vitest'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import Unauthorized from './Unauthorized'

describe('Unauthorized', () => {
  it('바탕이 앱 바탕색이다', () => {
    const { container } = render(
      <MemoryRouter>
        <Unauthorized />
      </MemoryRouter>
    )

    const page = container.firstElementChild
    expect(page).toHaveClass('bg-canvas')
    expect(page).not.toHaveClass('from-red-50')
  })
})
