import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { DollarSign } from 'lucide-react'
import { DetailSection, DetailRow } from './DetailSection'

describe('DetailSection', () => {
  it('제목과 줄을 보여준다', () => {
    render(
      <DetailSection title="투자 정보" icon={DollarSign}>
        <DetailRow label="종목" value="AAPL" />
        <DetailRow label="총 수익" value="+₩10" valueClassName="font-bold text-green-600" last />
      </DetailSection>
    )

    expect(screen.getByText('투자 정보')).toBeInTheDocument()
    expect(screen.getByText('AAPL')).toBeInTheDocument()
    expect(screen.getByText('+₩10')).toHaveClass('text-green-600')
  })

  it('마지막 줄에는 아래 테두리가 없다', () => {
    render(
      <DetailSection title="투자 정보" icon={DollarSign}>
        <DetailRow label="종목" value="AAPL" />
        <DetailRow label="매수일" value="2023-01-03" last />
      </DetailSection>
    )

    expect(screen.getByText('종목').parentElement).toHaveClass('border-b')
    expect(screen.getByText('매수일').parentElement).not.toHaveClass('border-b')
  })
})

describe('DetailSection 변형', () => {
  it('테두리 · 배경 · 아이콘 색을 바꿀 수 있다', () => {
    const { container } = render(
      <DetailSection
        title="최적 타이밍"
        icon={DollarSign}
        borderClassName="border-brand/25"
        className="bg-brand-bg"
        iconClassName="text-green-600"
      >
        <DetailRow label="최적 매수일" value="2023-03-01" dividerClassName="border-brand/25" />
      </DetailSection>
    )

    const section = container.firstElementChild
    expect(section).toHaveClass('border-brand/25', 'bg-brand-bg')
    expect(container.querySelector('svg')).toHaveClass('text-green-600')
    expect(screen.getByText('최적 매수일').parentElement).toHaveClass('border-brand/25')
  })
})
