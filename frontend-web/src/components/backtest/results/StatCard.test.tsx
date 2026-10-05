import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { DollarSign } from 'lucide-react'
import { StatCard } from './StatCard'

describe('StatCard', () => {
  it('라벨과 값, 보조 줄을 보여준다', () => {
    render(
      <StatCard label="현재 가치" value="₩1,500,000" sub="주식: ₩1,400,000" icon={DollarSign} />
    )

    expect(screen.getByText('현재 가치')).toBeInTheDocument()
    expect(screen.getByText('₩1,500,000')).toBeInTheDocument()
    expect(screen.getByText('주식: ₩1,400,000')).toBeInTheDocument()
  })

  it('값에 붙인 색 클래스를 그대로 쓴다', () => {
    render(<StatCard label="총 수익" value="+₩10" valueClassName="text-green-600" icon={DollarSign} />)

    expect(screen.getByText('+₩10')).toHaveClass('text-green-600')
  })

  it('색을 안 주면 기본 글자색이다', () => {
    render(<StatCard label="초기 투자금" value="₩300,000" icon={DollarSign} />)

    expect(screen.getByText('₩300,000')).toHaveClass('text-tx-1')
  })
})

describe('StatCard 아이콘 틀', () => {
  it('아이콘 배경과 색을 바꿀 수 있다', () => {
    const { container } = render(
      <StatCard
        label="수익률"
        value="-3.20%"
        icon={DollarSign}
        iconBoxClassName="bg-red-500/100/15"
        iconClassName="text-red-600"
      />
    )

    expect(container.querySelector('svg')?.parentElement).toHaveClass('bg-red-500/100/15')
    expect(container.querySelector('svg')).toHaveClass('text-red-600')
  })
})
