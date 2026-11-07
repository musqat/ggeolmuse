import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ResultFold } from './ResultFold'

describe('ResultFold', () => {
  it('닫힌 채 시작하고 제목 옆에 요약을 보인다', () => {
    render(<ResultFold title="상세" meta="변동성 24.50%"><p>안</p></ResultFold>)

    expect(screen.getByText('상세').closest('details')).not.toHaveAttribute('open')
    expect(screen.getByText('· 변동성 24.50%')).toBeInTheDocument()
    expect(screen.queryByText('안')).toBeNull()
  })

  it('처음 열 때 안을 그리고 다시 닫아도 남긴다', async () => {
    render(<ResultFold title="상세"><p>안</p></ResultFold>)

    await userEvent.click(screen.getByText('상세'))
    expect(await screen.findByText('안')).toBeVisible()

    await userEvent.click(screen.getByText('상세'))
    expect(screen.getByText('안')).toBeInTheDocument()
  })
})
