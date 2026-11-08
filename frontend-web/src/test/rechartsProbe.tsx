import type { ReactNode } from 'react';

// 테스트용 recharts 대역(jsdom 은 크기가 0 이라 안 그려지는 차트의 데이터 · 선 · 마커 · Y축 범위를 data 속성으로, readCharts 로 읽음)
interface ChildrenProps {
  children?: ReactNode;
}

export const ResponsiveContainer = ({ children }: ChildrenProps) => <>{children}</>;

// 차트를 누르는 처리가 있으면 점마다 단추(이름 "점 날짜", 누르면 그 날짜를 activeLabel 로 넘김)
export const LineChart = ({
  data,
  children,
  onClick,
}: ChildrenProps & { data?: { date?: string }[]; onClick?: (state: { activeLabel?: string }) => void }) => (
  <div data-testid="line-chart" data-points={JSON.stringify(data ?? [])}>
    {children}
    {onClick &&
      (data ?? []).map((point) => (
        <button
          key={point.date}
          type="button"
          aria-label={`점 ${point.date}`}
          onClick={() => onClick({ activeLabel: point.date })}
        />
      ))}
  </div>
);

export const Line = ({ dataKey, name, strokeOpacity }: { dataKey: string; name?: string; strokeOpacity?: number }) => (
  <i data-testid="line" data-key={dataKey} data-name={name ?? ''} data-opacity={strokeOpacity ?? ''} />
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

// 영역 차트도 선 차트와 같이 읽음
export const AreaChart = LineChart;
export const Area = Line;

// 가로 기준선의 값 · 축 · 넘칠 때 처리
export const ReferenceLine = ({ y, yAxisId, ifOverflow }: { y?: number; yAxisId?: number | string; ifOverflow?: string }) => (
  <i data-testid="ref-line" data-ref={JSON.stringify({ y, yAxisId, ifOverflow })} />
);

const Nothing = () => null;
export const CartesianGrid = Nothing;
export const Legend = Nothing;
