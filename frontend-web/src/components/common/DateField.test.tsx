import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { DateField } from './DateField'

// 기대값은 TZ=Asia/Seoul 전제 (vitest.config.ts 에서 고정)
const dayButton = (day: string) =>
  within(screen.getByRole('dialog'))
    .getAllByRole('button')
    .find((b) => b.textContent === day)!

describe('DateField', () => {
  it('값을 2024. 06. 14. 꼴로 보인다', () => {
    render(<DateField value="2024-06-14" onChange={vi.fn()} testId="field" />)

    expect(screen.getByTestId('field')).toHaveTextContent('2024. 06. 14.')
  })

  it('값이 비면 안내 문구를 보인다', () => {
    render(<DateField value="" onChange={vi.fn()} testId="field" />)

    expect(screen.getByTestId('field')).toHaveTextContent('날짜 선택')
  })

  it('고른 날을 YYYY-MM-DD 로 넘기고 닫는다. KST 에서 하루 밀리지 않는다', async () => {
    const onChange = vi.fn()
    render(<DateField value="2024-06-14" onChange={onChange} testId="field" />)

    await userEvent.click(screen.getByTestId('field'))
    await userEvent.click(dayButton('20'))

    expect(onChange).toHaveBeenCalledWith('2024-06-20')
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('max 뒤 날짜는 고를 수 없다', async () => {
    render(<DateField value="2024-06-14" onChange={vi.fn()} max="2024-06-15" testId="field" />)

    await userEvent.click(screen.getByTestId('field'))

    expect(dayButton('15')).toBeEnabled()
    expect(dayButton('16')).toBeDisabled()
  })

  it('다른 칸을 열면 먼저 연 칸은 닫힌다', async () => {
    render(
      <>
        <DateField value="2024-06-14" onChange={vi.fn()} testId="start" />
        <DateField value="2024-07-01" onChange={vi.fn()} testId="end" />
      </>
    )

    await userEvent.click(screen.getByTestId('start'))
    await userEvent.click(screen.getByTestId('end'))

    expect(screen.getAllByRole('dialog')).toHaveLength(1)
    expect(screen.getByTestId('start')).toHaveAttribute('aria-expanded', 'false')
    expect(screen.getByTestId('end')).toHaveAttribute('aria-expanded', 'true')
  })

  it('Esc 나 바깥을 누르면 닫힌다', async () => {
    render(
      <>
        <DateField value="2024-06-14" onChange={vi.fn()} testId="field" />
        <p>바깥</p>
      </>
    )

    await userEvent.click(screen.getByTestId('field'))
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).toBeNull()

    await userEvent.click(screen.getByTestId('field'))
    await userEvent.click(screen.getByText('바깥'))
    expect(screen.queryByRole('dialog')).toBeNull()
  })
})

describe('DateField 빠른 선택', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-03-31T12:00:00+09:00'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('빠른 선택은 달력을 오늘에서 그만큼 전 달로 넘기기만 하고 값은 그대로 둔다', async () => {
    const onChange = vi.fn()
    render(<DateField value="2026-03-31" onChange={onChange} presets testId="field" />)

    await userEvent.click(screen.getByTestId('field'))
    await userEvent.click(screen.getByRole('button', { name: '3개월 전' }))

    expect(onChange).not.toHaveBeenCalled()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByLabelText('연도 선택')).toHaveValue('2025')
    expect(screen.getByLabelText('월 선택')).toHaveValue('11')

    await userEvent.click(screen.getByRole('button', { name: '1년 전' }))
    expect(screen.getByLabelText('연도 선택')).toHaveValue('2025')
    expect(screen.getByLabelText('월 선택')).toHaveValue('2')

    // 넘어간 달에서 날짜를 누르면 그때 값이 정해진다
    await userEvent.click(dayButton('10'))
    expect(onChange).toHaveBeenCalledWith('2025-03-10')
  })

  it('min 이 든 달보다 앞서는 빠른 선택은 누를 수 없다', async () => {
    render(<DateField value="2026-03-31" onChange={vi.fn()} presets min="2025-12-15" testId="field" />)

    await userEvent.click(screen.getByTestId('field'))

    expect(screen.getByRole('button', { name: '3개월 전' })).toBeEnabled()
    expect(screen.getByRole('button', { name: '6개월 전' })).toBeDisabled()
  })

  it('presets 를 안 켜면 빠른 선택이 없다', async () => {
    render(<DateField value="2026-03-31" onChange={vi.fn()} testId="field" />)

    await userEvent.click(screen.getByTestId('field'))

    expect(screen.queryByRole('button', { name: '1개월 전' })).toBeNull()
  })
})
