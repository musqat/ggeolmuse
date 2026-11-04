import { Component, type ErrorInfo, type ReactNode } from 'react'

type Props = {
  // 바뀌면(다른 화면으로 옮기면) 오류 안내를 지우고 다시 그림
  resetKey: string
  children: ReactNode
}

type State = { error: Error | null }

// 화면 하나의 렌더 예외를 그 자리에서 받아 안내와 다시 시도를 보이는 경계
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('화면 렌더 오류', error, info.componentStack)
  }

  componentDidUpdate(prev: Props) {
    if (prev.resetKey !== this.props.resetKey && this.state.error) {
      this.setState({ error: null })
    }
  }

  render() {
    if (!this.state.error) {
      return this.props.children
    }
    return (
      <div role="alert" className="flex flex-col items-center justify-center gap-4 min-h-[50vh] text-center px-4">
        <p className="text-lg font-semibold text-tx-1">문제가 생겼습니다</p>
        <p className="text-sm text-tx-2">잠시 뒤 다시 시도해 주세요.</p>
        <button
          type="button"
          onClick={() => this.setState({ error: null })}
          className="px-4 py-2 rounded-lg bg-brand text-brand-ink text-sm font-medium hover:bg-brand-dark transition-colors"
        >
          다시 시도
        </button>
      </div>
    )
  }
}
