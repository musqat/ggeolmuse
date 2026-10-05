import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { BacktestHistoryPanel } from './BacktestHistoryPanel'

function renderPanel(overrides = {}) {
  render(
    <BacktestHistoryPanel
      historyData={[]}
      historyLoading={false}
      isAuthenticated={false}
      historyPage={0}
      setHistoryPage={vi.fn()}
      historyTotalPages={0}
      {...overrides}
    />
  )
}

describe('BacktestHistoryPanel', () => {
  it('불러오는 중이면 로딩 문구를 보여준다', () => {
    renderPanel({ historyLoading: true })

    expect(screen.getByText('로딩 중...')).toBeInTheDocument()
  })

  it('내역이 없으면 비어 있다고 알려준다', () => {
    renderPanel()

    expect(screen.getByText('아직 백테스트 히스토리가 없습니다.')).toBeInTheDocument()
  })

  it('로그인 안 했으면 이 기기에 저장된다고 안내한다', () => {
    renderPanel()

    expect(screen.getByText(/이 기기에 저장됩니다/)).toBeInTheDocument()
  })
})
