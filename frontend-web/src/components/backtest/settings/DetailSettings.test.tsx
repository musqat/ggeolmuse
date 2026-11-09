import { describe, it, expect, vi } from 'vitest'
import { useState, type ComponentProps } from 'react'
import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { DetailSettings } from './DetailSettings'
import { useBacktestOptions, type BacktestOptionsState } from '../shared/backtestOptions'

type Props = Omit<ComponentProps<typeof DetailSettings>, 'options' | 'open' | 'onToggle'>

// 실제 옵션 상태로 그리고 바뀐 값을 밖에서 봄
function Harness({ startOpen = false, seen, ...props }: Props & { startOpen?: boolean; seen?: (options: BacktestOptionsState) => void }) {
  const options = useBacktestOptions()
  const [open, setOpen] = useState(startOpen)
  seen?.(options)
  return <DetailSettings options={options} open={open} onToggle={() => setOpen(!open)} {...props} />
}

const toggle = () => screen.getByRole('button', { name: /세부 설정/ })

describe('DetailSettings', () => {
  it('접힌 줄에 기본값도 값으로 보이고, 양도세 · 기간이 끝나면은 없다', async () => {
    render(<Harness />)

    expect(toggle()).toHaveAttribute('aria-expanded', 'false')
    expect(toggle()).toHaveTextContent('세부 설정배당세 15% · 배당 현금 · 수수료 0% · 환율 자동')
    await userEvent.click(toggle())

    expect(toggle()).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByRole('button', { name: '배당세 15%' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.queryByText(/양도세/)).toBeNull()
    expect(screen.queryByText('기간이 끝나면')).toBeNull()
  })

  it('바꾼 값은 접힌 줄에서 진하게 보인다', async () => {
    render(<Harness startOpen />)
    await userEvent.click(screen.getByRole('button', { name: '배당세 15%' }))
    await userEvent.click(within(screen.getByRole('radiogroup', { name: '배당' })).getByRole('radio', { name: '재투자' }))
    await userEvent.click(toggle())

    expect(toggle()).toHaveTextContent('배당세 끔 · 배당 재투자 · 수수료 0% · 환율 자동')
    expect(within(toggle()).getByText('배당세 끔').tagName).toBe('B')
    expect(within(toggle()).getByText('수수료 0%').tagName).not.toBe('B')
  })

  it('익절 · 손절로 재투자가 막히면 재투자는 aria-disabled 와 안내로 이어지고 고른 값은 기억한다', async () => {
    let options!: BacktestOptionsState
    const { rerender } = render(<Harness startOpen seen={(o) => (options = o)} />)
    const reinvest = () => within(screen.getByRole('radiogroup', { name: '배당' })).getByRole('radio', { name: '재투자' })
    await userEvent.click(reinvest())
    rerender(<Harness startOpen seen={(o) => (options = o)} reinvestLocked />)

    expect(reinvest()).toHaveAttribute('aria-disabled', 'true')
    expect(reinvest()).toHaveAttribute('aria-checked', 'false')
    expect(reinvest()).toHaveAccessibleDescription('익절 · 손절이 있으면 배당은 현금으로')
    expect(options.reinvestDividends).toBe(true)
    rerender(<Harness startOpen seen={(o) => (options = o)} />)
    expect(reinvest()).toHaveAttribute('aria-checked', 'true')
  })

  it('막힌 재투자는 눌러도 고르지 않는다', async () => {
    let options!: BacktestOptionsState
    render(<Harness startOpen seen={(o) => (options = o)} reinvestLocked />)
    await userEvent.click(screen.getByRole('radio', { name: '재투자' }))

    expect(options.reinvestDividends).toBe(false)
  })

  it('전략 비교 안내는 재투자를 막지 않고 배당 칸 아래에 보인다', () => {
    render(<Harness startOpen reinvestNote="익절 · 손절이 있는 조건부 매수는 재투자 안 함" />)

    expect(screen.getByRole('radio', { name: '재투자' })).not.toHaveAttribute('aria-disabled')
    expect(screen.getByText('익절 · 손절이 있는 조건부 매수는 재투자 안 함')).toBeInTheDocument()
  })

  it('수수료는 숫자와 점 하나만 받고, 오류가 있으면 칸 아래와 접힌 줄에 보인다', async () => {
    let options!: BacktestOptionsState
    const { rerender } = render(<Harness startOpen seen={(o) => (options = o)} />)
    const fee = screen.getByRole('textbox', { name: '수수료(퍼센트)' })
    await userEvent.clear(fee)
    await userEvent.type(fee, '0.2.5%')

    expect(options.tradingFeeRate).toBe('0.25')
    rerender(<Harness startOpen seen={(o) => (options = o)} messages={{ fee: { kind: 'error', text: '100 아래' } }} />)
    expect(screen.getByRole('textbox', { name: '수수료(퍼센트)' })).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByRole('textbox', { name: '수수료(퍼센트)' })).toHaveAccessibleDescription('100 아래')
    await userEvent.click(toggle())
    expect(toggle()).toHaveTextContent('고칠 칸 있음 · 배당세 15%')
  })

  it('환율을 직접 입력으로 바꾸면 매수 · 끝 날짜 칸이 열리고 빈 칸은 그 날짜 시장 환율', async () => {
    let options!: BacktestOptionsState
    render(<Harness startOpen seen={(o) => (options = o)} />)
    expect(screen.queryByRole('textbox', { name: '매수 환율(원)' })).toBeNull()
    await userEvent.click(within(screen.getByRole('radiogroup', { name: '환율' })).getByRole('radio', { name: '직접 입력' }))

    expect(screen.getByRole('textbox', { name: '매수 환율(원)' })).toHaveAttribute('placeholder', '1,350')
    expect(screen.getByRole('textbox', { name: '끝 날짜 환율(원)' })).toHaveAttribute('placeholder', '1,380')
    expect(screen.getByText('비우면 그 날짜 시장 환율(흐린 숫자는 예시)')).toBeInTheDocument()
    fireEvent.change(screen.getByRole('textbox', { name: '매수 환율(원)' }), { target: { value: '1,130' } })
    expect(options).toMatchObject({ fxMode: 'manual', manualPurchaseFxRate: '1130', manualCurrentFxRate: '' })
  })

  it('직접 입력 환율이 둘 다 틀리면 두 칸 모두 오류로 표시하고 칸 아래 글과 잇는다', async () => {
    const zero = { kind: 'error' as const, text: '0 보다 크게' }
    render(<Harness startOpen messages={{ buyFx: zero, endFx: zero }} />)
    await userEvent.click(within(screen.getByRole('radiogroup', { name: '환율' })).getByRole('radio', { name: '직접 입력' }))

    for (const name of ['매수 환율(원)', '끝 날짜 환율(원)']) {
      expect(screen.getByRole('textbox', { name })).toHaveAttribute('aria-invalid', 'true')
      expect(screen.getByRole('textbox', { name })).toHaveAccessibleDescription('0 보다 크게')
    }
  })

  it('끝 날짜 환율만 틀리면 끝 날짜 칸만 오류로 표시한다', async () => {
    render(<Harness startOpen messages={{ endFx: { kind: 'error', text: '0 보다 크게' } }} />)
    await userEvent.click(within(screen.getByRole('radiogroup', { name: '환율' })).getByRole('radio', { name: '직접 입력' }))

    expect(screen.getByRole('textbox', { name: '끝 날짜 환율(원)' })).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByRole('textbox', { name: '매수 환율(원)' })).not.toHaveAttribute('aria-invalid')
  })

  it('세금 칩은 누를 때마다 켜고 끈다', async () => {
    const seen = vi.fn()
    render(<Harness startOpen seen={seen} />)
    await userEvent.click(screen.getByRole('button', { name: '배당세 15%' }))

    expect(screen.getByRole('button', { name: '배당세 15%' })).toHaveAttribute('aria-pressed', 'false')
    expect(seen).toHaveBeenLastCalledWith(expect.objectContaining({ dividendTax: false }))
  })
})
