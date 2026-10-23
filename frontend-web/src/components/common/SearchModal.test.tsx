import { describe, it, expect, vi } from 'vitest'
import type { ComponentProps } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import SearchModal from './SearchModal'

function renderModal(props: Partial<ComponentProps<typeof SearchModal>> = {}) {
  render(
    <MemoryRouter>
      <SearchModal isOpen onClose={vi.fn()} supportedSymbols={['AVGO', 'VZ', 'V']} {...props} />
    </MemoryRouter>
  )
}

// 결과에 보이는 티커를 위에서부터
const shownSymbols = () => screen.getAllByText(/^(AVGO|VZ|V)$/).map((el) => el.textContent)

describe('SearchModal', () => {
  it('정확히 맞는 티커를 먼저 보인다', async () => {
    const user = userEvent.setup()
    renderModal()

    await user.type(screen.getByPlaceholderText('종목명 또는 티커 검색...'), 'v')

    expect(shownSymbols()[0]).toBe('V')
  })
})
