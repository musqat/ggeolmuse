import { describe, it, expect, vi } from 'vitest'
import { useState, type ComponentProps } from 'react'
import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { DetailSettings } from './DetailSettings'
import { useBacktestOptions, type BacktestOptionsState } from '../shared/backtestOptions'
import { portfolioTaxSale, singleTaxSale } from '../shared/taxSale'

type Props = Omit<ComponentProps<typeof DetailSettings>, 'options' | 'open' | 'onToggle'>

// 실제 옵션 상태로 그리고 바뀐 값을 밖에서 봄
function Harness({
  startOpen = false,
  seen,
  single = false,
  ...props
}: Props & { startOpen?: boolean; seen?: (options: BacktestOptionsState) => void; single?: boolean }) {
  const options = useBacktestOptions()
  const [open, setOpen] = useState(startOpen)
  seen?.(options)
  return (
    <DetailSettings
      options={options}
      open={open}
      onToggle={() => setOpen(!open)}
      {...(single && { taxSale: singleTaxSale(options) })}
      {...props}
    />
  )
}

const taxSaleGroup = () => screen.queryByRole('radiogroup', { name: '현금이 모자라면' })
const taxSaleSelect = () => screen.queryByRole('combobox', { name: '현금이 모자라면' })
const saleList = () => screen.queryByRole('list', { name: '먼저 팔 종목부터' })
const noMove = { names: ['SPY'], onMove: () => {} }

const toggle = () => screen.getByRole('button', { name: /세부 설정/ })

describe('DetailSettings', () => {
  it('접힌 줄에 기본값도 값으로 보이고, 열면 기간 끝은 끝까지 보유 · 세금 칩 둘은 켜져 있다', async () => {
    render(<Harness />)

    expect(toggle()).toHaveAttribute('aria-expanded', 'false')
    expect(toggle()).toHaveTextContent('세부 설정끝까지 보유 · 배당세 15% · 양도세 22% · 배당 현금 · 수수료 0% · 환율 자동')
    await userEvent.click(toggle())

    expect(toggle()).toHaveAttribute('aria-expanded', 'true')
    expect(within(screen.getByRole('radiogroup', { name: '기간이 끝나면' })).getByRole('radio', { name: '끝까지 보유' })).toHaveAttribute('aria-checked', 'true')
    expect(screen.getByRole('button', { name: '배당세 15%' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: '양도세 22%(연 250만원 공제)' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('바꾼 값은 접힌 줄에서 진하게 보인다', async () => {
    render(<Harness startOpen />)
    await userEvent.click(screen.getByRole('button', { name: '배당세 15%' }))
    await userEvent.click(within(screen.getByRole('radiogroup', { name: '배당' })).getByRole('radio', { name: '재투자' }))
    await userEvent.click(toggle())

    expect(toggle()).toHaveTextContent('끝까지 보유 · 배당세 끔 · 양도세 22% · 배당 재투자 · 수수료 0% · 환율 자동')
    expect(within(toggle()).getByText('배당세 끔').tagName).toBe('B')
    expect(within(toggle()).getByText('수수료 0%').tagName).not.toBe('B')
  })

  it('마지막 거래일에 팔기 · 양도세 끔을 고르면 값이 바뀌고 접힌 줄에서 진하게 보인다', async () => {
    let options!: BacktestOptionsState
    render(<Harness startOpen seen={(o) => (options = o)} />)
    await userEvent.click(within(screen.getByRole('radiogroup', { name: '기간이 끝나면' })).getByRole('radio', { name: '마지막 거래일에 팔기' }))
    await userEvent.click(screen.getByRole('button', { name: '양도세 22%(연 250만원 공제)' }))

    expect(options).toMatchObject({ periodEnd: 'SELL_AT_END', capitalGainsTax: false })
    expect(screen.getByRole('button', { name: '양도세 22%(연 250만원 공제)' })).toHaveAttribute('aria-pressed', 'false')
    await userEvent.click(toggle())
    expect(toggle()).toHaveTextContent('마지막 거래일에 팔기 · 배당세 15% · 양도세 끔')
    expect(within(toggle()).getByText('마지막 거래일에 팔기').tagName).toBe('B')
    expect(within(toggle()).getByText('양도세 끔').tagName).toBe('B')
  })

  it('파는 조건으로 재투자가 막히면 재투자는 aria-disabled 와 안내로 이어지고 고른 값은 기억한다', async () => {
    let options!: BacktestOptionsState
    const { rerender } = render(<Harness startOpen seen={(o) => (options = o)} />)
    const reinvest = () => within(screen.getByRole('radiogroup', { name: '배당' })).getByRole('radio', { name: '재투자' })
    await userEvent.click(reinvest())
    rerender(<Harness startOpen seen={(o) => (options = o)} reinvestLocked />)

    expect(reinvest()).toHaveAttribute('aria-disabled', 'true')
    expect(reinvest()).toHaveAttribute('aria-checked', 'false')
    expect(reinvest()).toHaveAccessibleDescription('익절 · 손절 · 고점 대비 하락 매도 · 부분 익절이 있으면 배당은 현금으로')
    expect(options.reinvestDividends).toBe(true)
    rerender(<Harness startOpen seen={(o) => (options = o)} />)
    expect(reinvest()).toHaveAttribute('aria-checked', 'true')
  })

  it('모드 안내와 함께 막히면 재투자는 그 안내로 이어진다', () => {
    render(<Harness startOpen reinvestLocked reinvestNote="이동평균 교차는 배당을 현금으로" />)

    expect(screen.getByRole('radio', { name: '재투자' })).toHaveAttribute('aria-disabled', 'true')
    expect(screen.getByRole('radio', { name: '재투자' })).toHaveAccessibleDescription('이동평균 교차는 배당을 현금으로')
    expect(screen.queryByText('익절 · 손절 · 고점 대비 하락 매도 · 부분 익절이 있으면 배당은 현금으로')).toBeNull()
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
    expect(toggle()).toHaveTextContent('고칠 칸 있음 · 끝까지 보유 · 배당세 15%')
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

  it('판매 설정을 주면 양도세를 켰을 때 세금 칸 아래에 현금이 모자라면 고르기를 보이고, 고르면 접힌 줄에 진하게 적는다', async () => {
    let options!: BacktestOptionsState
    render(<Harness startOpen single seen={(o) => (options = o)} />)

    const group = within(taxSaleGroup()!)
    expect(group.getAllByRole('radio').map((radio) => radio.textContent)).toEqual(['현금이 생길 때 냄', '주식을 팔아 냄'])
    expect(group.getByRole('radio', { name: '현금이 생길 때 냄' })).toHaveAttribute('aria-checked', 'true')
    await userEvent.click(group.getByRole('radio', { name: '주식을 팔아 냄' }))

    expect(options.taxSale).toBe('SELL')
    await userEvent.click(toggle())
    expect(toggle()).toHaveTextContent('끝까지 보유 · 배당세 15% · 양도세 22% · 모자라면 팔아 냄 · 배당 현금')
    expect(within(toggle()).getByText('모자라면 팔아 냄').tagName).toBe('B')
  })

  it('양도세를 끄면 현금이 모자라면 고르기를 숨기고 접힌 줄에서도 뺀다', async () => {
    render(<Harness startOpen single />)
    await userEvent.click(within(taxSaleGroup()!).getByRole('radio', { name: '주식을 팔아 냄' }))
    await userEvent.click(screen.getByRole('button', { name: '양도세 22%(연 250만원 공제)' }))

    expect(taxSaleGroup()).toBeNull()
    await userEvent.click(toggle())
    expect(toggle()).not.toHaveTextContent('모자라면')
  })

  it('판매 설정을 주지 않으면 현금이 모자라면 고르기가 없다', () => {
    render(<Harness startOpen />)

    expect(taxSaleGroup()).toBeNull()
  })

  it('포트폴리오 판매 방식은 다섯 가지 드롭다운이고 고른 방식 이름을 접힌 줄에 적으며, 정한 순서가 아니면 순서 목록이 없다', async () => {
    const onChange = vi.fn()
    render(<Harness taxSale={portfolioTaxSale('LOSS_FIRST', onChange, noMove)} />)

    expect(toggle()).toHaveTextContent('양도세 22% · 모자라면 손실 종목부터 팖 · 배당 현금')
    await userEvent.click(toggle())
    expect(taxSaleGroup()).toBeNull()
    expect(within(taxSaleSelect()!).getAllByRole('option').map((option) => option.textContent)).toEqual([
      '현금이 생길 때 냄',
      '목표 비중에 맞춰 팖',
      '정한 순서로 팖',
      '손실 종목부터 팖',
      '이익 · 손실 섞어 새 이익 0',
    ])
    expect(taxSaleSelect()).toHaveValue('LOSS_FIRST')
    expect(saleList()).toBeNull()
    await userEvent.selectOptions(taxSaleSelect()!, '정한 순서로 팖')
    expect(onChange).toHaveBeenCalledWith('CUSTOM_ORDER')
  })

  it('정한 순서로 팔면 드롭다운 아래 먼저 팔 종목부터 목록에 줄마다 위 · 아래 단추를 두고, 맨 위는 위 · 맨 아래는 아래 단추를 막는다', async () => {
    const onMove = vi.fn()
    render(<Harness startOpen taxSale={portfolioTaxSale('CUSTOM_ORDER', vi.fn(), { names: ['QQQ', 'SPY', '3번 종목'], onMove })} />)

    const list = within(saleList()!)
    expect(list.getAllByRole('listitem')).toHaveLength(3)
    expect(list.getAllByRole('button').map((button) => button.getAttribute('aria-label'))).toEqual([
      'QQQ 위로',
      'QQQ 아래로',
      'SPY 위로',
      'SPY 아래로',
      '3번 종목 위로',
      '3번 종목 아래로',
    ])
    expect(list.getByRole('button', { name: 'QQQ 위로' })).toBeDisabled()
    expect(list.getByRole('button', { name: '3번 종목 아래로' })).toBeDisabled()
    expect(list.getByRole('button', { name: 'SPY 위로' })).toBeEnabled()
    await userEvent.click(list.getByRole('button', { name: 'SPY 위로' }))
    expect(onMove).toHaveBeenLastCalledWith(1, -1)
    await userEvent.click(list.getByRole('button', { name: 'QQQ 아래로' }))
    expect(onMove).toHaveBeenLastCalledWith(0, 1)
  })

  it('위 · 아래 단추로 옮기면 옮긴 종목의 같은 쪽 단추에 포커스를 두고, 끝에 닿아 막히면 다른 쪽 단추로', async () => {
    const Ordered = () => {
      const [names, setNames] = useState(['QQQ', 'SPY', 'IAU'])
      const onMove = (position: number, step: -1 | 1) =>
        setNames((before) => before.map((name, i) => (i === position ? before[position + step] : i === position + step ? before[position] : name)))
      return <Harness startOpen taxSale={portfolioTaxSale('CUSTOM_ORDER', vi.fn(), { names, onMove })} />
    }
    render(<Ordered />)
    const button = (name: string) => within(saleList()!).getByRole('button', { name })

    await userEvent.click(button('IAU 위로'))
    expect(button('IAU 위로')).toHaveFocus()
    await userEvent.click(button('IAU 위로'))
    expect(button('IAU 아래로')).toHaveFocus()
    await userEvent.click(button('IAU 아래로'))
    expect(button('IAU 아래로')).toHaveFocus()
  })

  it('연말 정리를 주면 양도세를 켰을 때 세금 칸에 연말 정리 칩과 안내를 보이고, 켜면 접힌 줄에 진하게 적는다', async () => {
    let options!: BacktestOptionsState
    render(<Harness startOpen harvest seen={(o) => (options = o)} />)

    const chip = screen.getByRole('button', { name: '연말 정리' })
    expect(chip).toHaveAttribute('aria-pressed', 'false')
    expect(chip).toHaveAccessibleDescription(
      '결제가 그해 안에 끝나는 마지막 거래일(T+1, 2024-05-28 전은 T+2)에 250만원 공제를 채우거나 손실로 상계하도록 팔고 같은 값에 다시 삼',
    )
    await userEvent.click(chip)

    expect(options.yearEndHarvest).toBe(true)
    expect(screen.getByRole('button', { name: '연말 정리' })).toHaveAttribute('aria-pressed', 'true')
    await userEvent.click(toggle())
    expect(toggle()).toHaveTextContent('양도세 22% · 연말 정리 · 배당 현금')
    expect(within(toggle()).getByText('연말 정리').tagName).toBe('B')
  })

  it('연말 정리를 주지 않으면(비교 모드) 칩이 없고, 양도세를 끄면 칩을 숨기고 접힌 줄에서도 뺀다', async () => {
    const { unmount } = render(<Harness startOpen />)
    expect(screen.queryByRole('button', { name: '연말 정리' })).toBeNull()
    unmount()

    render(<Harness startOpen harvest />)
    await userEvent.click(screen.getByRole('button', { name: '연말 정리' }))
    await userEvent.click(screen.getByRole('button', { name: '양도세 22%(연 250만원 공제)' }))
    expect(screen.queryByRole('button', { name: '연말 정리' })).toBeNull()
    await userEvent.click(toggle())
    expect(toggle()).not.toHaveTextContent('연말 정리')
  })

  it('세금 칩은 누를 때마다 켜고 끈다', async () => {
    const seen = vi.fn()
    render(<Harness startOpen seen={seen} />)
    await userEvent.click(screen.getByRole('button', { name: '배당세 15%' }))

    expect(screen.getByRole('button', { name: '배당세 15%' })).toHaveAttribute('aria-pressed', 'false')
    expect(seen).toHaveBeenLastCalledWith(expect.objectContaining({ dividendTax: false }))
  })
})
