import { describe, it, expect, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ComponentProps } from 'react'
import { StrategyComparisonForm } from './StrategyComparisonForm'

const period = { start: '2023-01-02', end: '2023-12-29', agoCount: '1', agoUnit: 'YEAR' as const, agoOn: false }

type Props = ComponentProps<typeof StrategyComparisonForm>

const chip = (id: string, type: 'SIMPLE' | 'DCA' | 'CONDITIONAL_PURCHASE' | 'MA_CROSS', name: string, patch = {}) => ({
  id,
  type,
  name,
  error: null,
  short: false,
  ...patch,
})

function renderForm(patch: Partial<Props> = {}) {
  const props: Props = {
    symbol: { symbol: 'AAPL', onChange: vi.fn(), supportedSymbols: ['AAPL'] },
    period: {
      period: {
        ...period,
        setStart: vi.fn(), setEnd: vi.fn(), setAgoCount: vi.fn(), setAgoUnit: vi.fn(),
        values: period, load: vi.fn(),
      },
    },
    amount: { label: '전략마다 원금', amount: '1000000', onAmountChange: vi.fn() },
    chips: [chip('s1', 'SIMPLE', '단순 매수'), chip('s2', 'DCA', '적립식')],
    openId: 's2',
    onOpen: vi.fn(),
    onRemove: vi.fn(),
    onAdd: vi.fn(),
    panel: <p>열린 칩 설정</p>,
    ...patch,
  }
  render(<StrategyComparisonForm {...props} />)
  return props
}

const chips = () => within(screen.getByRole('group', { name: '견줄 전략' }))

describe('StrategyComparisonForm', () => {
  it('칩은 누르면 아래 설정을 여는 단추이고, 열린 칩의 설정을 보인다', async () => {
    const props = renderForm({
      chips: [chip('s2', 'DCA', '적립식'), chip('s3', 'CONDITIONAL_PURCHASE', '조건부 매수'), chip('s4', 'MA_CROSS', '이동평균')],
    })

    expect(screen.getByText('전략마다 원금')).toBeInTheDocument()
    expect(screen.getByText('적립식 · 조건부 · 이동평균은 눌러서 설정')).toBeInTheDocument()
    expect(chips().getByRole('button', { name: '적립식' })).toHaveAttribute('aria-expanded', 'true')
    expect(chips().getByRole('button', { name: '조건부 매수' })).toHaveAttribute('aria-expanded', 'false')
    expect(screen.getByText('열린 칩 설정')).toBeInTheDocument()
    await userEvent.click(chips().getByRole('button', { name: '조건부 매수' }))
    await userEvent.click(chips().getByRole('button', { name: '이동평균' }))

    expect(props.onOpen).toHaveBeenNthCalledWith(1, 's3')
    expect(props.onOpen).toHaveBeenNthCalledWith(2, 's4')
  })

  it('설정할 칸이 없는 단순 매수 칩은 이름만 두고 여는 단추가 아니다', async () => {
    const props = renderForm({ openId: 's1' })

    expect(chips().getByText('단순 매수')).toBeInTheDocument()
    expect(chips().queryByRole('button', { name: '단순 매수' })).toBeNull()
    expect(chips().getByRole('button', { name: '적립식' })).toHaveAttribute('aria-expanded', 'false')
    await userEvent.click(chips().getByText('단순 매수'))

    expect(props.onOpen).not.toHaveBeenCalled()
  })

  it('칩이 둘 미만이면 첫 ＋ 단추를 고칠 칸으로 표시하고 칸 아래 글과 잇는다', () => {
    renderForm({ chips: [chip('s2', 'DCA', '적립식')], chipsMessage: { kind: 'error', text: '전략을 둘 이상' } })

    const [first, ...rest] = chips().getAllByRole('button', { name: /^＋/ })
    expect(first).toHaveAccessibleName('＋ 단순 매수')
    expect(first).toHaveAttribute('data-bad', 'true')
    expect(first).toHaveAccessibleDescription('전략을 둘 이상')
    rest.forEach((button) => expect(button).not.toHaveAttribute('data-bad'))
  })

  it('칩이 둘 이상이면 ＋ 단추는 고칠 칸이 아니다', () => {
    renderForm()

    chips()
      .getAllByRole('button', { name: /^＋/ })
      .forEach((button) => expect(button).not.toHaveAttribute('data-bad'))
  })

  it('× 로 칩을 빼고 ＋ 로 종류마다 더하며, 단순 매수는 하나만 둔다', async () => {
    const props = renderForm()

    await userEvent.click(chips().getByRole('button', { name: '적립식 빼기' }))
    expect(props.onRemove).toHaveBeenCalledWith('s2')
    expect(chips().queryByRole('button', { name: '＋ 단순 매수' })).toBeNull()
    await userEvent.click(chips().getByRole('button', { name: '＋ 적립식' }))
    await userEvent.click(chips().getByRole('button', { name: '＋ 조건부 매수' }))
    await userEvent.click(chips().getByRole('button', { name: '＋ 이동평균' }))

    expect(props.onAdd).toHaveBeenNthCalledWith(1, 'DCA')
    expect(props.onAdd).toHaveBeenNthCalledWith(2, 'CONDITIONAL_PURCHASE')
    expect(props.onAdd).toHaveBeenNthCalledWith(3, 'MA_CROSS')
  })

  it('단순 매수 칩이 없으면 ＋ 단순 매수를 보인다', () => {
    renderForm({ chips: [chip('s2', 'DCA', '적립식')] })

    expect(chips().getByRole('button', { name: '＋ 단순 매수' })).toBeInTheDocument()
  })

  it('고칠 칩은 ! 표시와 고칠 것을 이름에 담고, 원금이 모자란 칩은 점선', () => {
    renderForm({
      chips: [
        chip('s1', 'SIMPLE', '단순 매수'),
        chip('s2', 'DCA', '적립식', { short: true }),
        chip('s3', 'CONDITIONAL_PURCHASE', '조건부 매수', { error: '회당 금액이 원금보다 큽니다.' }),
      ],
      chipsMessage: { kind: 'error', text: '고칠 칩 1개(! 표시)는 눌러서 고치기' },
    })

    const bad = chips().getByRole('button', { name: '조건부 매수 (고칠 것: 회당 금액이 원금보다 큽니다.)' })
    expect(bad).toHaveAttribute('data-bad', 'true')
    expect(bad.parentElement).toHaveClass('border-danger')
    expect(chips().getByRole('button', { name: '적립식' }).parentElement).toHaveClass('border-dashed')
    expect(screen.getByText('고칠 칩 1개(! 표시)는 눌러서 고치기')).toBeInTheDocument()
  })
})
