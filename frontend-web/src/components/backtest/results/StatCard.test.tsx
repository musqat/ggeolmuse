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

  it('좁은 카드는 휴대폰에서 안쪽 여백 · 값 글자를 줄이고 긴 값을 칸 안에서 줄바꿈한다', () => {
    const { container } = render(<StatCard label="총 평가" value="₩1,234,567,890" compact />)

    expect(container.firstChild).toHaveClass('min-w-0', 'p-4', 'sm:p-5')
    expect(screen.getByText('₩1,234,567,890')).toHaveClass('text-xl', 'sm:text-2xl', 'break-words')
    expect(screen.getByText('₩1,234,567,890')).not.toHaveClass('text-2xl')
  })

  it('아이콘을 그리지 않는다', () => {
    const { container } = render(<StatCard label="수익률" value="-3.20%" />)

    expect(container.querySelector('svg')).toBeNull()
  })
})
