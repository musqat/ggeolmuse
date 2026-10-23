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

// 차트마다 날짜 축 눈금과 첫 눈금 글자
export function readXAxes() {
  return screen.getAllByTestId('line-chart').map((chart) => {
    const axis = within(chart).queryByTestId('x-axis');
    return { ticks: JSON.parse(axis?.dataset.ticks ?? 'null'), first: axis?.dataset.first ?? '' };
  });
}

// 차트마다 툴팁 서식에 긴 소수를 넣은 값
export function readTooltips() {
  return screen
    .getAllByTestId('line-chart')
    .map((chart) => JSON.parse(within(chart).queryByTestId('tooltip')?.dataset.sample ?? 'null'));
}
