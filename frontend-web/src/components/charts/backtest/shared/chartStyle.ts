// 백테스트 결과 차트가 같이 쓰는 recharts 설정
// 색은 index.css 의 테마 변수를 쓴다. 다크 · 라이트가 거기서 갈린다

export const CHART_HEIGHT = 300;
export const CHART_MARGIN = { top: 5, right: 30, left: 20, bottom: 5 };
export const GRID_PROPS = { strokeDasharray: '3 3', stroke: 'rgb(var(--line))' };

// 축 선과 눈금 글자. XAxis · YAxis 에 펼쳐 넣는다
export const AXIS_PROPS = {
  stroke: 'rgb(var(--line-strong))',
  tick: { fontSize: 12, fill: 'rgb(var(--text-2))' },
};

// 선 색 글자는 어두운 배경에서 대비가 4.5 를 못 넘어 항목도 본문 글자색으로 쓴다
export const TOOLTIP_PROPS = {
  contentStyle: { backgroundColor: 'rgb(var(--bg-surface))', border: '1px solid rgb(var(--line-strong))' },
  labelStyle: { color: 'rgb(var(--text-1))' },
  itemStyle: { color: 'rgb(var(--text-1))' },
};

// 차트 위 점 색. 같은 뜻은 어느 차트에서나 같은 색이다
export const MARKER_COLORS = {
  purchase: '#8b5cf6',
  dividend: '#10b981',
  optimal: '#fbbf24',
};

// 마커 기본 모양. 색은 쓰는 쪽이 정한다
export const MARKER_PROPS = { r: 4, stroke: '#fff', strokeWidth: 2 };
// 최적 시점 마커는 금색이 밝아 진한 테두리를 두른다
export const OPTIMAL_MARKER_PROPS = { r: 5, fill: MARKER_COLORS.optimal, stroke: '#78350f', strokeWidth: 2 };

// 마커 위 글자
export const MARKER_LABEL_STYLE = { fill: 'rgb(var(--text-1))', fontSize: 12, fontWeight: 'bold' };

export const formatTooltipDate = (label: string) => `날짜: ${label}`;
export const formatUsdTick = (value: number) => `$${Math.round(value)}`;
export const formatManwonTick = (value: number) => `₩${(value / 10000).toFixed(0)}만`;

// "2026-03-02" → "3/2". Date 로 바꾸지 않아 시간대에 따라 하루 밀리지 않는다
export const formatDayTick = (value: string) => {
  const [, month, day] = value.split('-');
  return `${Number(month)}/${Number(day)}`;
};
