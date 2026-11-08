import { describe, it, expect, vi } from 'vitest'
import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { NumberInput } from './NumberInput'

function Harness({
  initial = '',
  onChangeSpy,
  showKoreanHint,
}: {
  initial?: string
  onChangeSpy?: (v: string) => void
  showKoreanHint?: boolean
}) {
  const [value, setValue] = useState(initial)
  return (
    <NumberInput
      value={value}
      onChange={(v) => {
        setValue(v)
        onChangeSpy?.(v)
      }}
      showKoreanHint={showKoreanHint}
    />
  )
}

describe('NumberInput', () => {
  it('처음 받은 값에 콤마를 넣어 보여준다', () => {
    render(<Harness initial="1234567" />)
    expect(screen.getByRole('textbox')).toHaveValue('1,234,567')
  })

  it('입력하면 화면엔 콤마, 부모에겐 순수 숫자를 준다', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    render(<Harness onChangeSpy={onChange} />)

    await user.type(screen.getByRole('textbox'), '1234567')

    expect(screen.getByRole('textbox')).toHaveValue('1,234,567')
    // 부모로 올라가는 값에 콤마 섞이는지
    expect(onChange).toHaveBeenLastCalledWith('1234567')
  })

  it('숫자가 아닌 글자는 무시한다', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.type(screen.getByRole('textbox'), '12ab34')

    expect(screen.getByRole('textbox')).toHaveValue('1,234')
  })

  it('소수점을 받는다', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    render(<Harness onChangeSpy={onChange} />)

    await user.type(screen.getByRole('textbox'), '1234.56')

    expect(screen.getByRole('textbox')).toHaveValue('1,234.56')
    expect(onChange).toHaveBeenLastCalledWith('1234.56')
  })

  it('소수점 두 번째는 먹지 않는다', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    render(<Harness onChangeSpy={onChange} />)

    await user.type(screen.getByRole('textbox'), '12.34.56')

    // 두 번째 점이 든 타이핑만 버림(뒤 숫자는 그대로 이어붙음)
    expect(screen.getByRole('textbox')).toHaveValue('12.3456')
    expect(onChange).toHaveBeenLastCalledWith('12.3456')
  })

  it('값이 있으면 한글 금액 힌트를 보여준다', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.type(screen.getByRole('textbox'), '1000000')

    expect(screen.getByText('100만원')).toBeInTheDocument()
  })

  it('값이 0이면 힌트를 숨긴다', () => {
    render(<Harness initial="0" />)
    expect(screen.queryByText(/원$/)).not.toBeInTheDocument()
  })

  it('showKoreanHint 를 끄면 힌트가 없다', () => {
    render(<Harness initial="1000000" showKoreanHint={false} />)
    expect(screen.queryByText('100만원')).not.toBeInTheDocument()
  })
})

describe('NumberInput inline', () => {
  it('테두리 없는 밑줄 칸 옆에 만 아래까지 읽기 쉬운 금액을 보인다', () => {
    render(<NumberInput value="1234567" onChange={vi.fn()} variant="inline" ariaLabel="금액(원)" />)

    const input = screen.getByRole('textbox', { name: '금액(원)' })
    expect(input).toHaveClass('input-inline')
    expect(input).toHaveValue('1,234,567')
    expect(input).toHaveAttribute('inputmode', 'decimal')
    expect(input.nextElementSibling).toHaveTextContent('123만 4,567원')
  })

  it('값이 비면 읽기 쉬운 금액을 숨긴다', () => {
    render(<NumberInput value="" onChange={vi.fn()} variant="inline" ariaLabel="예산(원)" placeholder="제한 없음" />)

    expect(screen.getByRole('textbox', { name: '예산(원)' })).toHaveAttribute('placeholder', '제한 없음')
    expect(screen.queryByText(/원$/)).not.toBeInTheDocument()
  })

  it('invalid · describedBy 를 입력의 aria-invalid · aria-describedby 로 넘긴다', () => {
    render(<NumberInput value="0" onChange={vi.fn()} variant="inline" ariaLabel="금액(원)" invalid describedBy="amount-error" />)

    const input = screen.getByRole('textbox', { name: '금액(원)' })
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAttribute('aria-describedby', 'amount-error')
  })
})
