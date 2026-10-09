// 백테스트 결과 차트가 같이 쓰는 recharts 설정

export const CHART_HEIGHT = 300;
export const CHART_MARGIN = { top: 5, right: 30, left: 20, bottom: 5 };
export const GRID_PROPS = { strokeDasharray: '3 3', stroke: '#f0f0f0' };
export const TOOLTIP_STYLE = { backgroundColor: 'rgba(255, 255, 255, 0.95)', border: '1px solid #ccc' };

// 매수 마커 기본 모양. 색은 쓰는 쪽이 정한다
export const MARKER_PROPS = { r: 4, stroke: '#fff', strokeWidth: 2 };

export const formatTooltipDate = (label: string) => `날짜: ${label}`;
export const formatUsdTick = (value: number) => `$${Math.round(value)}`;
export const formatManwonTick = (value: number) => `₩${(value / 10000).toFixed(0)}만`;

// 3/2 꼴 날짜 눈금
export const formatDayTick = (value: string) => {
  const date = new Date(value);
  return `${date.getMonth() + 1}/${date.getDate()}`;
};
