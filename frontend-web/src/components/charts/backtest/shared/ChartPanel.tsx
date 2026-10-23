import React from 'react';
import { CartesianGrid, Legend, LineChart, ResponsiveContainer, XAxis } from 'recharts';
import { ChartPeriodSelector } from '../../common/components/ChartPeriodSelector';
import type { useChartPeriod } from '../../common/hooks/useChartPeriod';
import { AXIS_PROPS, CHART_HEIGHT, CHART_MARGIN, GRID_PROPS, type dateAxis } from './chartStyle';

const CARD_CLASS = 'bg-surface rounded-xl shadow-sm border border-line/50 p-6';

type ChartPeriodState = Pick<
  ReturnType<typeof useChartPeriod>,
  'chartPeriod' | 'customStartDate' | 'setChartPeriod' | 'setCustomStartDate'
>;

interface ChartCardProps {
  title: string;
  subtitle?: string;
  period?: ChartPeriodState;
  className?: string;
  children: React.ReactNode;
}

// 백테스트 결과 차트 카드(제목 · 부제 · 기간 버튼)
export const ChartCard: React.FC<ChartCardProps> = ({ title, subtitle, period, className, children }) => (
  <div className={className ? `${CARD_CLASS} ${className}` : CARD_CLASS}>
    <div className="mb-4">
      <h3 className="text-lg font-semibold text-tx-1">{title}</h3>
      {subtitle && <p className="text-sm text-tx-2 mt-2">{subtitle}</p>}
    </div>
    {period && (
      <ChartPeriodSelector
        chartPeriod={period.chartPeriod}
        customStartDate={period.customStartDate}
        onPeriodChange={period.setChartPeriod}
        onCustomDateChange={period.setCustomStartDate}
      />
    )}
    {children}
  </div>
);

interface ChartPanelProps {
  title?: string;
  data: object[];
  xAxis: ReturnType<typeof dateAxis>;
  className?: string;
  legendFormatter?: (value: string) => string;
  children: React.ReactNode;
}

// 날짜 축 선 차트 판(격자 · X축 · 범례), Y축 · 툴팁 · 선 · 마커는 children 의 recharts 요소
export const ChartPanel: React.FC<ChartPanelProps> = ({ title, data, xAxis, className, legendFormatter, children }) => (
  <div className={className}>
    {title && <h4 className="text-sm font-medium text-tx-1 mb-3">{title}</h4>}
    <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
      <LineChart data={data} margin={CHART_MARGIN}>
        <CartesianGrid {...GRID_PROPS} />
        <XAxis dataKey="date" {...AXIS_PROPS} {...xAxis} />
        <Legend formatter={legendFormatter} />
        {children}
      </LineChart>
    </ResponsiveContainer>
  </div>
);

interface ChartMessageProps {
  tone?: 'muted' | 'error';
  children: React.ReactNode;
}

// 차트 자리 안내 카드(불러오는 중 · 오류 · 데이터 없음)
export const ChartMessage: React.FC<ChartMessageProps> = ({ tone = 'muted', children }) => (
  <div className={CARD_CLASS}>
    <div className="flex items-center justify-center h-64">
      <div className={tone === 'error' ? 'text-danger' : 'text-tx-2'}>{children}</div>
    </div>
  </div>
);
