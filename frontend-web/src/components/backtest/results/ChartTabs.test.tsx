import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ChartTabs } from './ChartTabs'

const tabs = [
  { key: 'asset', label: '자산 추이', render: () => <p>자산 차트</p> },
  { key: 'price', label: '주가 추이', render: () => <p>주가 차트</p> },
]

describe('ChartTabs', () => {
  it('첫 탭을 고른 채 그 탭 내용만 그린다', () => {
    render(<ChartTabs tabs={tabs} />)

    expect(screen.getByRole('tab', { name: '자산 추이' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: '주가 추이' })).toHaveAttribute('aria-selected', 'false')
    expect(screen.getByRole('tabpanel', { name: '자산 추이' })).toHaveTextContent('자산 차트')
    expect(screen.queryByText('주가 차트')).toBeNull()
  })

  it('다른 탭을 누르면 그 탭 내용으로 바꾼다', async () => {
    render(<ChartTabs tabs={tabs} />)

    await userEvent.click(screen.getByRole('tab', { name: '주가 추이' }))

    expect(screen.getByRole('tab', { name: '주가 추이' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tabpanel', { name: '주가 추이' })).toHaveTextContent('주가 차트')
    expect(screen.queryByText('자산 차트')).toBeNull()
  })
})
