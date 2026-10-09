import type { ReactNode } from 'react';

// 테스트에서 recharts 대신 쓴다. jsdom 은 크기가 0 이라 차트가 안 그려지므로,
// 차트에 넘긴 데이터 · 선 · 마커 · Y축 범위를 data 속성으로 남긴다. readCharts 로 읽는다
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

const Nothing = () => null;
export const XAxis = Nothing;
export const CartesianGrid = Nothing;
export const Tooltip = Nothing;
export const Legend = Nothing;
