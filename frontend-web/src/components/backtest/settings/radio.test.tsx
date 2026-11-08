import { describe, it, expect, vi } from 'vitest'
import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RadioGroup } from './radio'

const OPTIONS = [
  { value: 'a', label: '가' },
  { value: 'b', label: '나' },
  { value: 'c', label: '다' },
]

const Harness = ({ onChange = vi.fn(), initial = 'a' as string | null }) => {
  const [value, setValue] = useState(initial)
  return (
    <RadioGroup
      label="묶음"
      options={OPTIONS}
      value={value}
      onChange={(next) => {
        onChange(next)
        setValue(next)
      }}
      optionClassName={(checked) => (checked ? 'on' : 'off')}
    />
  )
}

describe('RadioGroup', () => {
  it('고른 것만 aria-checked 이고 Tab 은 고른 것에만 선다', () => {
    render(<Harness />)

    expect(screen.getByRole('radiogroup', { name: '묶음' })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: '가' })).toHaveAttribute('aria-checked', 'true')
    expect(screen.getByRole('radio', { name: '가' })).toHaveAttribute('tabindex', '0')
    expect(screen.getByRole('radio', { name: '나' })).toHaveAttribute('tabindex', '-1')
    expect(screen.getByRole('radio', { name: '가' })).toHaveClass('on')
  })

  it('화살표 · Home · End 로 옮기면 그 항목을 고르고 포커스도 옮긴다(끝에서 처음으로 돈다)', async () => {
    const onChange = vi.fn()
    render(<Harness onChange={onChange} />)

    screen.getByRole('radio', { name: '가' }).focus()
    await userEvent.keyboard('{ArrowRight}')
    expect(onChange).toHaveBeenLastCalledWith('b')
    expect(screen.getByRole('radio', { name: '나' })).toHaveFocus()

    await userEvent.keyboard('{End}')
    expect(onChange).toHaveBeenLastCalledWith('c')
    await userEvent.keyboard('{ArrowDown}')
    expect(onChange).toHaveBeenLastCalledWith('a')
    await userEvent.keyboard('{ArrowLeft}')
    expect(onChange).toHaveBeenLastCalledWith('c')
    await userEvent.keyboard('{Home}')
    expect(screen.getByRole('radio', { name: '가' })).toHaveAttribute('aria-checked', 'true')
  })

  it('고른 값이 없으면 첫 항목에 Tab 이 선다', () => {
    render(<Harness initial={null} />)

    expect(screen.getAllByRole('radio').map((radio) => radio.getAttribute('aria-checked'))).toEqual(['false', 'false', 'false'])
    expect(screen.getByRole('radio', { name: '가' })).toHaveAttribute('tabindex', '0')
  })
})
