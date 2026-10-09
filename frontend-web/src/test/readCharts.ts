import { screen, within } from '@testing-library/react';

// rechartsProbe 가 남긴 차트마다 데이터 · 선 · 마커 · Y축 범위
export function readCharts() {
  return screen.getAllByTestId('line-chart').map((chart) => ({
    points: JSON.parse(chart.dataset.points ?? '[]'),
    lines: within(chart)
      .queryAllByTestId('line')
      .map((line) => `${line.dataset.key}:${line.dataset.name}`),
    dots: within(chart)
      .queryAllByTestId('dot')
      .map((dot) => JSON.parse(dot.dataset.dot ?? '{}')),
    yDomains: within(chart)
      .queryAllByTestId('y-axis')
      .map((axis) => JSON.parse(axis.dataset.domain ?? 'null')),
  }));
}
