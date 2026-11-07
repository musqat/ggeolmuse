import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { DetailSection, DetailRow } from './DetailSection'

describe('DetailSection', () => {
  it('제목과 줄을 보여준다', () => {
    render(
      <DetailSection title="투자 정보">
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
      <DetailSection title="투자 정보">
        <DetailRow label="종목" value="AAPL" />
        <DetailRow label="매수일" value="2023-01-03" last />
      </DetailSection>
    )

    expect(screen.getByText('종목').parentElement).toHaveClass('border-b')
    expect(screen.getByText('매수일').parentElement).not.toHaveClass('border-b')
  })

  it('아이콘 · 카드 테두리 없이 그린다', () => {
    const { container } = render(
      <DetailSection title="환율 영향">
        <DetailRow label="환차익률" value="+1.00%" last />
      </DetailSection>
    )

    expect(container.querySelector('svg')).toBeNull()
    expect(container.firstElementChild).not.toHaveClass('bg-surface')
  })
})
