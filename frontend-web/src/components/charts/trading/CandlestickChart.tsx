import React from 'react';
import KLineChartComponent from '../KLineChartComponent';

interface CandlestickData {
  time: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume?: number;
}

interface CandlestickChartProps {
  data: CandlestickData[];
  symbol: string;
  className?: string;
}

const CandlestickChart: React.FC<CandlestickChartProps> = ({ data, symbol, className = '' }) => {
  if (!data || data.length === 0) {
    return (
      <div className={`flex items-center justify-center h-[400px] text-tx-2 ${className}`}>
        데이터 없음
      </div>
    );
  }

  return (
    <div className={className}>
      <KLineChartComponent
        data={data.map(d => ({
          time: d.time,
          open: d.open,
          high: d.high,
          low: d.low,
          close: d.close,
          volume: d.volume || 0,
        }))}
        symbol={symbol}
        showIndicatorPanel={false}
        height={400}
      />
    </div>
  );
};

export default CandlestickChart;
