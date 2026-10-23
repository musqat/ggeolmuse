import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { accountsApi } from '@/services/api'
import ExchangeModal from './ExchangeModal'
import { notify } from '@/components/common/notify'

vi.mock('@/services/api', () => ({ accountsApi: { getExchangeRateRange: vi.fn() } }))

const BALANCE = { balanceKrw: 1_000_000, balanceUsd: 0 }

function renderModal() {
  const onSubmit = vi.fn().mockResolvedValue(undefined)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ExchangeModal isOpen onClose={vi.fn()} onSubmit={onSubmit} accountBalance={BALANCE as never} currentExchangeRate={1339.2} />
    </QueryClientProvider>
  )
  return onSubmit
}

// 금액을 넣고 수동 환율을 켜서 값을 넣는다
async function typeManualRate(rate: string) {
  await userEvent.type(screen.getByPlaceholderText('환전할 KRW 금액'), '100000')
  await userEvent.click(screen.getByLabelText('수동 환율 입력'))
  await userEvent.type(screen.getByPlaceholderText('원하는 환율 입력 (예: 1350)'), rate)
}

beforeEach(() => {
  vi.mocked(accountsApi.getExchangeRateRange).mockResolvedValue({ data: { min: 1000, max: 1633 } } as never)
})
afterEach(() => vi.restoreAllMocks())

describe('ExchangeModal 수동 환율 범위', () => {
  it('수동 환율 칸 아래에 서버가 준 가능 범위를 보여 준다', async () => {
    renderModal()
    await userEvent.click(screen.getByLabelText('수동 환율 입력'))

    expect(await screen.findByText('가능 범위: ₩1,000 ~ ₩1,633')).toBeInTheDocument()
  })

  it('범위 밖 환율은 제출하지 않고 범위를 알려 준다', async () => {
    const alert = vi.spyOn(notify, 'error').mockImplementation(() => {})
    const onSubmit = renderModal()
    await typeManualRate('1')
    await screen.findByText(/가능 범위/)

    await userEvent.click(screen.getByRole('button', { name: '환전하기' }))

    expect(alert).toHaveBeenCalledWith('환율은 1000 ~ 1633원 사이로 넣어 주세요.')
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('범위 안 환율은 그대로 제출한다', async () => {
    const onSubmit = renderModal()
    await typeManualRate('1350.5')
    await screen.findByText(/가능 범위/)

    await userEvent.click(screen.getByRole('button', { name: '환전하기' }))

    expect(onSubmit).toHaveBeenCalledWith({ fromCurrency: 'KRW', toCurrency: 'USD', originalAmount: 100000, exchangeRate: 1350.5 })
  })
})

// 환율 · 제출 결과를 정해 창을 그림
function renderWith({ onSubmit = vi.fn().mockResolvedValue(undefined), rate = 1339.2 } = {}) {
  const onClose = vi.fn()
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ExchangeModal isOpen onClose={onClose} onSubmit={onSubmit} accountBalance={BALANCE as never} currentExchangeRate={rate} />
    </QueryClientProvider>
  )
  return { onClose, onSubmit }
}

describe('ExchangeModal 현재 환율', () => {
  it('원화를 달러로 바꿀 때 예상 금액을 보이고 현재 환율로 넘긴 뒤 닫는다', async () => {
    const { onClose, onSubmit } = renderWith({ rate: 1350 })

    await userEvent.type(screen.getByPlaceholderText('환전할 KRW 금액'), '135000')
    expect(screen.getByText('예상 환전 금액: $100.00')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '환전하기' }))

    expect(onSubmit).toHaveBeenCalledWith({ fromCurrency: 'KRW', toCurrency: 'USD', originalAmount: 135000, exchangeRate: 1350 })
    expect(onClose).toHaveBeenCalled()
  })

  it('환율을 아직 못 받았으면 막는다', async () => {
    const alert = vi.spyOn(notify, 'error').mockImplementation(() => {})
    const { onSubmit } = renderWith({ rate: 0 })

    await userEvent.type(screen.getByPlaceholderText('환전할 KRW 금액'), '135000')
    await userEvent.click(screen.getByRole('button', { name: '환전하기' }))

    expect(alert).toHaveBeenCalledWith('환율 정보를 불러오는 중입니다. 잠시 후 다시 시도해주세요.')
    expect(onSubmit).not.toHaveBeenCalled()
  })
})

describe('ExchangeModal 실패', () => {
  it('환전이 실패하면 창과 금액을 그대로 둔다', async () => {
    const { onClose, onSubmit } = renderWith({ onSubmit: vi.fn().mockRejectedValue(new Error('down')) })

    await userEvent.type(screen.getByPlaceholderText('환전할 KRW 금액'), '100000')
    await userEvent.click(screen.getByRole('button', { name: '환전하기' }))

    expect(onSubmit).toHaveBeenCalledWith({ fromCurrency: 'KRW', toCurrency: 'USD', originalAmount: 100000, exchangeRate: 1339.2 })
    expect(screen.getByRole('heading', { name: '환전' })).toBeInTheDocument()
    expect(screen.getByPlaceholderText('환전할 KRW 금액')).toHaveValue(100000)
    expect(onClose).not.toHaveBeenCalled()
  })
})
