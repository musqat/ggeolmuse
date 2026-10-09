import React from 'react';
import KLineChartComponent from '../KLineChartComponent';
import type { CandlestickChartData } from '../../../types/ohlc';

interface CandlestickChartProps {
  data: CandlestickChartData[];
  className?: string;
}

const CandlestickChart: React.FC<CandlestickChartProps> = ({ data, className = '' }) => {
  if (!data || data.length === 0) {
    return (
      <div className={`flex items-center justify-center h-[400px] text-tx-2 ${className}`}>
        데이터 없음
      </div>
    );
  }

  return (
    <div className={className}>
      {/* 받은 배열을 그대로 넘긴다. 새 배열을 만들면 렌더마다 차트를 다시 그린다 */}
      <KLineChartComponent
        data={data}
        showIndicatorPanel={false}
        height={400}
      />
    </div>
  );
};

export default CandlestickChart;
