import { describe, it, expect } from 'vitest'
import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ChartTabs } from './ChartTabs'

// 누를 때마다 수가 느는 내용(다시 그려지면 0 부터)
const Counter = ({ name }: { name: string }) => {
  const [count, setCount] = useState(0)
  return <button type="button" onClick={() => setCount(count + 1)}>{`${name} ${count}`}</button>
}

const tabs = [
  { key: 'asset', label: '자산 추이', render: () => <p>자산 차트</p> },
  { key: 'price', label: '주가 추이', render: () => <Counter name="주가" /> },
  { key: 'days', label: '매수일별 수익률', render: () => <p>매수일 차트</p> },
]

describe('ChartTabs', () => {
  it('첫 탭을 고른 채 그 탭만 그리고, 탭 목록은 이름으로 읽힌다', () => {
    render(<ChartTabs label="결과 차트" tabs={tabs} />)

    expect(screen.getByRole('tablist', { name: '결과 차트' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '자산 추이' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: '주가 추이' })).toHaveAttribute('aria-selected', 'false')
    expect(screen.getByRole('tabpanel', { name: '자산 추이' })).toHaveTextContent('자산 차트')
    expect(screen.queryByText(/주가 0/)).toBeNull()
  })

  it('다른 탭을 누르면 그 탭을 보이고, 한 번 연 탭은 다시 돌아와도 상태를 지킨다', async () => {
    render(<ChartTabs label="결과 차트" tabs={tabs} />)

    await userEvent.click(screen.getByRole('tab', { name: '주가 추이' }))
    await userEvent.click(screen.getByRole('button', { name: '주가 0' }))
    await userEvent.click(screen.getByRole('tab', { name: '자산 추이' }))

    expect(screen.getByRole('tabpanel', { name: '자산 추이' })).toBeVisible()
    expect(screen.getByRole('button', { name: '주가 1', hidden: true })).not.toBeVisible()

    await userEvent.click(screen.getByRole('tab', { name: '주가 추이' }))
    expect(screen.getByRole('button', { name: '주가 1' })).toBeVisible()
  })

  it('고른 탭만 탭 순서에 두고, 화살표 · Home · End 로 탭을 옮긴다', async () => {
    render(<ChartTabs label="결과 차트" tabs={tabs} />)
    const tab = (name: string) => screen.getByRole('tab', { name })

    expect(tab('자산 추이')).toHaveAttribute('tabindex', '0')
    expect(tab('주가 추이')).toHaveAttribute('tabindex', '-1')

    await userEvent.click(tab('자산 추이'))
    await userEvent.keyboard('{ArrowRight}')
    expect(tab('주가 추이')).toHaveFocus()
    expect(tab('주가 추이')).toHaveAttribute('aria-selected', 'true')

    await userEvent.keyboard('{End}')
    expect(tab('매수일별 수익률')).toHaveAttribute('aria-selected', 'true')
    await userEvent.keyboard('{ArrowRight}')
    expect(tab('자산 추이')).toHaveAttribute('aria-selected', 'true')
    await userEvent.keyboard('{ArrowLeft}')
    expect(tab('매수일별 수익률')).toHaveFocus()
    await userEvent.keyboard('{Home}')
    expect(tab('자산 추이')).toHaveAttribute('aria-selected', 'true')
  })
})
