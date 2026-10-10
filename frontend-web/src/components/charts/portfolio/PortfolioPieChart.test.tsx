import { describe, it, expect } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import PortfolioPieChart from './PortfolioPieChart'

/** 파이 조각만 골라낸다. 툴팁이나 아이콘 path 는 fill 이 없다. */
function slicePaths(container: HTMLElement) {
  return [...container.querySelectorAll('svg path[fill]')]
}

describe('PortfolioPieChart', () => {
  it('한 항목이 100% 여도 조각을 그린다', () => {
    const { container } = render(
      <PortfolioPieChart data={[{ symbol: 'KRW 현금', value: 1000000, color: '#6366f1' }]} />
    )

    const paths = slicePaths(container)
    expect(paths).toHaveLength(1)

    // 원 하나를 호 두 개로 그린다. 시작점으로 돌아와야 닫힌 원이다.
    const d = paths[0].getAttribute('d') ?? ''
    expect(d.match(/A /g)).toHaveLength(2)
    expect(d.endsWith('Z')).toBe(true)

    expect(screen.getByText('KRW 현금')).toBeInTheDocument()
    expect(screen.getByText('100.0%')).toBeInTheDocument()
  })

  it('여러 항목이면 항목 수만큼 조각을 그린다', () => {
    const { container } = render(
      <PortfolioPieChart
        data={[
          { symbol: 'AAPL', value: 600, color: '#6366f1' },
          { symbol: 'MSFT', value: 400, color: '#3b82f6' },
        ]}
      />
    )

    expect(slicePaths(container)).toHaveLength(2)
    expect(screen.getByText('60.0%')).toBeInTheDocument()
    expect(screen.getByText('40.0%')).toBeInTheDocument()
  })

  it('여섯 개를 넘으면 나머지를 ETC 로 묶는다', () => {
    const data = Array.from({ length: 7 }, (_, i) => ({
      symbol: `S${i}`,
      value: 100 - i * 10,
      color: '#6366f1',
    }))

    const { container } = render(<PortfolioPieChart data={data} />)

    // 상위 5개 + ETC = 6조각
    expect(slicePaths(container)).toHaveLength(6)
    expect(screen.getByText('ETC')).toBeInTheDocument()
  })

  it('데이터가 없으면 안내를 보여준다', () => {
    const { container } = render(<PortfolioPieChart data={[]} />)

    expect(slicePaths(container)).toHaveLength(0)
    expect(screen.getByText('데이터가 없습니다')).toBeInTheDocument()
  })

  it('합계가 0 이면 안내를 보여준다', () => {
    // 잔액이 전부 0 인 계좌. 0 으로 나누면 NaN 이 되어 path 가 깨진다.
    const { container } = render(
      <PortfolioPieChart data={[{ symbol: 'KRW 현금', value: 0, color: '#6366f1' }]} />
    )

    expect(slicePaths(container)).toHaveLength(0)
    expect(screen.getByText('데이터가 없습니다')).toBeInTheDocument()
  })
  // SVG(280 × 280)가 overflow hidden 이라 툴팁(200 × 120)이 밖으로 나가면 잘린다
  it('툴팁을 SVG 안에 둔다', () => {
    const { container } = render(
      <PortfolioPieChart
        data={[
          { symbol: 'AAPL', value: 600, color: '#6366f1' },
          { symbol: 'MSFT', value: 400, color: '#3b82f6' },
        ]}
      />
    )
    const slice = slicePaths(container)[0]
    const box = () => {
      const fo = container.querySelector('foreignObject')!
      return [Number(fo.getAttribute('x')), Number(fo.getAttribute('y'))]
    }

    fireEvent.mouseEnter(slice)
    // 왼쪽 위: 커서 오른쪽 10 · 위 40 자리. 위로 넘치면 0 으로
    fireEvent.mouseMove(slice, { clientX: 20, clientY: 20 })
    expect(box()).toEqual([30, 0])

    // 오른쪽 아래: 커서 왼쪽으로 돌리고 안쪽으로 자른다
    fireEvent.mouseMove(slice, { clientX: 270, clientY: 270 })
    expect(box()).toEqual([60, 160])

    // 가운데 오른쪽: 왼쪽으로 돌려도 넘쳐 0 까지
    fireEvent.mouseMove(slice, { clientX: 150, clientY: 140 })
    expect(box()).toEqual([0, 100])
  })
})
