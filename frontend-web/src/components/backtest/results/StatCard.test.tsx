import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { StatCard } from './StatCard'

describe('StatCard', () => {
  it('라벨과 값, 보조 줄을 보여준다', () => {
    render(<StatCard label="현재 가치" value="₩1,500,000" sub="주식: ₩1,400,000" />)

    expect(screen.getByText('현재 가치')).toBeInTheDocument()
    expect(screen.getByText('₩1,500,000')).toBeInTheDocument()
    expect(screen.getByText('주식: ₩1,400,000')).toBeInTheDocument()
  })

  it('값에 준 색 클래스를 그대로 쓴다', () => {
    render(<StatCard label="총 수익" value="+₩10" valueClassName="text-green-600" />)

    expect(screen.getByText('+₩10')).toHaveClass('text-green-600')
  })

  it('색을 안 주면 기본 글자색이다', () => {
    render(<StatCard label="초기 투자금" value="₩300,000" />)

    expect(screen.getByText('₩300,000')).toHaveClass('text-tx-1')
  })

  it('좁은 카드는 휴대폰에서 안쪽 여백과 값 글자를 15px 로 줄여 부호 있는 열 자리 금액까지 한 줄, 더 긴 값만 칸 안에서 줄바꿈', () => {
    const { container } = render(<StatCard label="실현 손익" value="+₩1,234,567,890" compact />)

    expect(container.firstChild).toHaveClass('min-w-0', 'p-4', 'sm:p-5')
    expect(screen.getByText('+₩1,234,567,890')).toHaveClass('text-[15px]', 'tracking-tight', 'sm:text-2xl', 'sm:tracking-normal', 'break-words')
    expect(screen.getByText('+₩1,234,567,890')).not.toHaveClass('text-base', 'text-lg', 'text-xl', 'text-2xl')
  })

  it('아이콘을 그리지 않는다', () => {
    const { container } = render(<StatCard label="수익률" value="-3.20%" />)

    expect(container.querySelector('svg')).toBeNull()
  })
})
