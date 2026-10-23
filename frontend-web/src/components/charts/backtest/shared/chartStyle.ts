// 백테스트 결과 차트가 같이 쓰는 recharts 설정
// 색은 index.css 테마 변수(다크 · 라이트가 거기서 갈림)

export const CHART_HEIGHT = 300;
export const CHART_MARGIN = { top: 5, right: 30, left: 20, bottom: 5 };
export const GRID_PROPS = { strokeDasharray: '3 3', stroke: 'rgb(var(--line))' };

// 축 선과 눈금 글자(XAxis · YAxis 에 펼쳐 넣음)
export const AXIS_PROPS = {
  stroke: 'rgb(var(--line-strong))',
  tick: { fontSize: 12, fill: 'rgb(var(--text-2))' },
};

// 툴팁 항목도 본문 글자색(선 색 글자는 어두운 바탕에서 대비 4.5 미만)
export const TOOLTIP_PROPS = {
  contentStyle: { backgroundColor: 'rgb(var(--bg-surface))', border: '1px solid rgb(var(--line-strong))' },
  labelStyle: { color: 'rgb(var(--text-1))' },
  itemStyle: { color: 'rgb(var(--text-1))' },
};

// 차트 위 점 색(같은 뜻은 어느 차트에서나 같은 색)
export const MARKER_COLORS = {
  purchase: '#8b5cf6',
  dividend: '#10b981',
  optimal: '#fbbf24',
};

// 마커 기본 모양(색은 따로)
export const MARKER_PROPS = { r: 4, stroke: '#fff', strokeWidth: 2 };
// 최적 시점 마커(밝은 금색이라 진한 테두리)
export const OPTIMAL_MARKER_PROPS = { r: 5, fill: MARKER_COLORS.optimal, stroke: '#78350f', strokeWidth: 2 };

// 마커 위 글자
export const MARKER_LABEL_STYLE = { fill: 'rgb(var(--text-1))', fontSize: 12, fontWeight: 'bold' };

export const formatTooltipDate = (label: string) => `날짜: ${label}`;
export const formatUsdTick = (value: number) => `$${Math.round(value)}`;
// 원화 축 눈금, 1억부터 억 단위(₩1.2억) · 그 아래 만 단위(₩140만)
export const formatKrwTick = (value: number) =>
  Math.abs(value) >= 100000000 ? `₩${Number((value / 100000000).toFixed(1))}억` : `₩${(value / 10000).toFixed(0)}만`;

// "2026-03-02" → "3/2"(Date 로 바꾸지 않아 시간대로 하루 밀리지 않음)
export const formatDayTick = (value: string) => {
  const [, month, day] = value.split('-');
  return `${Number(month)}/${Number(day)}`;
};

// "2023-01-03" → "23.1"
export const formatMonthTick = (value: string) => {
  const [year, month] = value.split('-');
  return `${year.slice(2)}.${Number(month)}`;
};

const ONE_YEAR_MS = 365 * 24 * 60 * 60 * 1000;

// 날짜 축 눈금(날짜순 입력), 1년이 넘으면 달마다 첫 날에 연.월 · 아니면 날짜마다 월/일
export const dateAxis = (dates: string[]): { ticks?: string[]; tickFormatter: (value: string) => string } => {
  if (dates.length < 2 || Date.parse(dates[dates.length - 1]) - Date.parse(dates[0]) <= ONE_YEAR_MS) {
    return { tickFormatter: formatDayTick };
  }
  const ticks = dates.filter((date, i) => i === 0 || date.slice(0, 7) !== dates[i - 1].slice(0, 7));
  return { ticks, tickFormatter: formatMonthTick };
};
