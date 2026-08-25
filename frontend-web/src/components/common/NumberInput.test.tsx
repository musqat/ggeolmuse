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

    // 두 번째 점이 든 타이핑만 버림. 뒤 숫자는 그대로 이어붙음
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
