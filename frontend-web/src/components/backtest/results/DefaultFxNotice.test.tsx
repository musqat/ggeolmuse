import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { DefaultFxNotice } from './DefaultFxNotice'

describe('DefaultFxNotice', () => {
  it('기본 환율로 계산한 날이 없으면 아무것도 그리지 않는다', () => {
    const { container: none } = render(<DefaultFxNotice dates={undefined} />)
    const { container: empty } = render(<DefaultFxNotice dates={[]} />)

    expect(none).toBeEmptyDOMElement()
    expect(empty).toBeEmptyDOMElement()
  })

  it('그런 날이 있으면 기본 환율과 앞의 날짜 셋을 보이고 나머지는 날 수로 줄인다', () => {
    render(<DefaultFxNotice dates={['2013-06-03', '2013-07-01', '2013-08-01', '2013-09-02', '2013-10-01']} />)

    const note = screen.getByRole('note')
    expect(note).toHaveTextContent('환율 기록이 없어 기본 환율 ₩1,350 으로 계산한 날이 있습니다')
    expect(note).toHaveTextContent('2013-06-03 · 2013-07-01 · 2013-08-01 외 2일')
  })

  it('셋 이하면 날짜를 모두 보인다', () => {
    render(<DefaultFxNotice dates={['2013-06-03']} />)

    expect(screen.getByRole('note')).toHaveTextContent('2013-06-03')
    expect(screen.getByRole('note')).not.toHaveTextContent('외')
  })
})
