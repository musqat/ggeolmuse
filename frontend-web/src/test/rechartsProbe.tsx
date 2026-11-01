import type { ReactNode } from 'react';

// 테스트용 recharts 대역(jsdom 은 크기가 0 이라 안 그려지는 차트의 데이터 · 선 · 마커 · Y축 범위를 data 속성으로, readCharts 로 읽음)
interface ChildrenProps {
  children?: ReactNode;
}

export const ResponsiveContainer = ({ children }: ChildrenProps) => <>{children}</>;

export const LineChart = ({ data, children }: ChildrenProps & { data?: unknown[] }) => (
  <div data-testid="line-chart" data-points={JSON.stringify(data ?? [])}>
    {children}
  </div>
);

export const Line = ({ dataKey, name }: { dataKey: string; name?: string }) => (
  <i data-testid="line" data-key={dataKey} data-name={name ?? ''} />
);

export const ReferenceDot = ({ x, y, fill }: { x?: string; y?: number; fill?: string }) => (
  <i data-testid="dot" data-dot={JSON.stringify({ x, y, fill })} />
);

export const YAxis = ({ domain }: { domain?: unknown }) => (
  <i data-testid="y-axis" data-domain={JSON.stringify(domain ?? null)} />
);

// 툴팁 서식에 긴 소수 하나를 넣어 본 값, readTooltips 로 읽음
export const Tooltip = ({ formatter }: { formatter?: (value: number, name: string) => unknown }) => (
  <i data-testid="tooltip" data-sample={JSON.stringify(formatter ? formatter(1234567.891, '샘플') : null)} />
);

// 날짜 축 눈금과 첫 눈금 글자, readXAxes 로 읽음
export const XAxis = ({ ticks, tickFormatter }: { ticks?: string[]; tickFormatter?: (value: string) => string }) => (
  <i
    data-testid="x-axis"
    data-ticks={JSON.stringify(ticks ?? null)}
    data-first={ticks?.length && tickFormatter ? tickFormatter(ticks[0]) : ''}
  />
);

const Nothing = () => null;
export const CartesianGrid = Nothing;
export const Legend = Nothing;
