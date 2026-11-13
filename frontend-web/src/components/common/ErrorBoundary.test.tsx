import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { rootErrorOptions } from '@/utils/clientErrors'
import { notify } from './notify'
import { ErrorBoundary } from './ErrorBoundary'

let shouldThrow = true

function Flaky() {
  if (shouldThrow) {
    throw new Error('렌더 실패')
  }
  return <p>페이지 내용</p>
}

describe('ErrorBoundary', () => {
  beforeEach(() => {
    shouldThrow = true
    // React 가 잡은 렌더 예외를 콘솔에 찍는 것만 막음
    vi.spyOn(console, 'error').mockImplementation(() => {})
    vi.spyOn(notify, 'error').mockImplementation(() => {})
  })
  afterEach(() => vi.restoreAllMocks())

  it('자식 렌더 중 오류가 나면 그 자리에 안내와 다시 시도 버튼을 보이고 바깥은 그대로 둔다', () => {
    render(
      <div>
        <h1>제목</h1>
        <ErrorBoundary resetKey="/a"><Flaky /></ErrorBoundary>
      </div>
    )

    expect(screen.getByRole('heading', { name: '제목' })).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('문제가 생겼습니다')
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument()
  })

  it('다시 시도를 누르면 자식을 다시 그린다', async () => {
    render(<ErrorBoundary resetKey="/a"><Flaky /></ErrorBoundary>)
    shouldThrow = false

    await userEvent.click(screen.getByRole('button', { name: '다시 시도' }))

    expect(screen.getByText('페이지 내용')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('앱 뿌리의 오류 옵션이면 잡은 렌더 오류를 컴포넌트 위치와 함께 콘솔에 한 번만 남긴다', () => {
    render(<ErrorBoundary><Flaky /></ErrorBoundary>, { onCaughtError: rootErrorOptions.onCaughtError })

    expect(console.error).toHaveBeenCalledOnce()
    expect(console.error).toHaveBeenCalledWith('화면 렌더 오류', expect.objectContaining({ message: '렌더 실패' }), expect.stringContaining('Flaky'))
  })

  it('다른 화면으로 옮기면(resetKey 가 바뀌면) 안내를 지우고 다시 그린다', () => {
    const { rerender } = render(<ErrorBoundary resetKey="/a"><Flaky /></ErrorBoundary>)
    shouldThrow = false

    rerender(<ErrorBoundary resetKey="/b"><Flaky /></ErrorBoundary>)

    expect(screen.getByText('페이지 내용')).toBeInTheDocument()
  })
})
