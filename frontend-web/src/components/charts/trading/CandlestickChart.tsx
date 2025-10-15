import React, { useMemo } from 'react';
import {
  ComposedChart,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Cell,
} from 'recharts';

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

  // 캔들스틱 차트 데이터 준비
  const chartData = useMemo(() => {
    if (!data || data.length === 0) return [];

    return data.map((item) => {
      const open = item.open || 0;
      const close = item.close || 0;
      const high = item.high || 0;
      const low = item.low || 0;
      const isPositive = close >= open;

      return {
        date: new Date(item.time).toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' }),
        fullDate: item.time,
        open,
        high,
        low,
        close,
        volume: item.volume || 0,
        isPositive,
      };
    });
  }, [data]);

  const formatPrice = (price: number | undefined): string => {
    if (price === undefined || price === null || isNaN(price)) return '0.00';
    return price.toFixed(2);
  };

  const CustomTooltip = ({ active, payload }: any) => {
    if (!active || !payload || !payload.length) return null;

    const d = payload[0].payload;
    if (!d) return null;

    const priceChange = d.close - d.open;
    const priceChangePercent = d.open ? ((priceChange / d.open) * 100).toFixed(2) : '0.00';

    return (
      <div className="bg-white p-3 border border-gray-200 rounded-lg shadow-lg">
        <p className="font-medium text-sm mb-2">{d.fullDate}</p>
        <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
          <div>시가: <span className="font-medium">${formatPrice(d.open)}</span></div>
          <div>종가: <span className="font-medium">${formatPrice(d.close)}</span></div>
          <div>고가: <span className="text-green-600 font-medium">${formatPrice(d.high)}</span></div>
          <div>저가: <span className="text-red-600 font-medium">${formatPrice(d.low)}</span></div>
        </div>
        <div
          className={`mt-2 pt-2 border-t text-xs ${
            d.isPositive ? 'text-red-600' : 'text-blue-600'
          }`}
        >
          <span className="font-medium">
            {priceChange >= 0 ? '+' : ''}
            {priceChange.toFixed(2)} ({priceChangePercent}%)
          </span>
        </div>
        {d.volume > 0 && (
          <div className="mt-2 pt-2 border-t text-xs">
            거래량: <span className="font-medium">{d.volume.toLocaleString()}</span>
          </div>
        )}
      </div>
    );
  };

  const formatVolume = (value: number): string => {
    if (!value || isNaN(value)) return '0';
    if (value >= 1000000000) return `${(value / 1000000000).toFixed(1)}B`;
    if (value >= 1000000) return `${(value / 1000000).toFixed(1)}M`;
    if (value >= 1000) return `${(value / 1000).toFixed(1)}K`;
    return value.toString();
  };

  // 커스텀 캔들스틱 렌더러
  const renderCandlestick = (props: any) => {
    const { x, y, width, height, index } = props;
    if (!chartData[index]) return null;

    const item = chartData[index];
    const { open, high, low, close, isPositive } = item;

    // Y축 범위 가져오기
    const allPrices = chartData.flatMap(d => [d.high, d.low]);
    const minPrice = Math.min(...allPrices);
    const maxPrice = Math.max(...allPrices);
    const priceRange = maxPrice - minPrice;

    if (priceRange === 0) return null;

    // Y 좌표 계산
    const getY = (price: number) => {
      return y + height - ((price - minPrice) / priceRange) * height;
    };

    const highY = getY(high);
    const lowY = getY(low);
    const openY = getY(open);
    const closeY = getY(close);

    const bodyTop = Math.min(openY, closeY);
    const bodyBottom = Math.max(openY, closeY);
    const bodyHeight = Math.max(bodyBottom - bodyTop, 1);

    const centerX = x + width / 2;
    const candleWidth = Math.min(width * 0.7, 10);
    const wickWidth = 1.5;

    const bodyColor = isPositive ? '#ef4444' : '#3b82f6';
    const wickColor = isPositive ? '#dc2626' : '#2563eb';

    return (
      <g key={`candle-${index}`}>
        {/* 고가 심지 */}
        <line
          x1={centerX}
          y1={highY}
          x2={centerX}
          y2={bodyTop}
          stroke={wickColor}
          strokeWidth={wickWidth}
        />
        {/* 저가 심지 */}
        <line
          x1={centerX}
          y1={bodyBottom}
          x2={centerX}
          y2={lowY}
          stroke={wickColor}
          strokeWidth={wickWidth}
        />
        {/* 캔들 몸통 */}
        <rect
          x={centerX - candleWidth / 2}
          y={bodyTop}
          width={candleWidth}
          height={bodyHeight}
          fill={bodyColor}
          stroke={wickColor}
          strokeWidth={1}
        />
      </g>
    );
  };

  if (!chartData || chartData.length === 0) {
    return (
      <div className={className}>
        <div className="h-full flex items-center justify-center text-gray-400">
          차트 데이터가 없습니다
        </div>
      </div>
    );
  }

  return (
    <div className={`${className} flex flex-col h-full`}>
      {/* 메인 캔들스틱 차트 */}
      <div style={{ flex: 3, minHeight: 0 }}>
        <ResponsiveContainer width="100%" height="100%">
          <ComposedChart
            data={chartData}
            margin={{ top: 5, right: 30, left: 20, bottom: 0 }}
          >
            <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />

            <XAxis
              dataKey="date"
              tick={{ fontSize: 11, fill: '#6b7280' }}
              stroke="#9ca3af"
              tickLine={false}
            />

            <YAxis
              domain={['auto', 'auto']}
              tick={{ fontSize: 11, fill: '#6b7280' }}
              stroke="#9ca3af"
              tickLine={false}
              tickFormatter={(value) => `$${value.toFixed(0)}`}
            />

            <Tooltip content={<CustomTooltip />} />

            {/* 캔들스틱 */}
            <Bar
              dataKey="close"
              shape={renderCandlestick}
              isAnimationActive={false}
            />
          </ComposedChart>
        </ResponsiveContainer>
      </div>

      {/* 거래량 차트 */}
      <div style={{ flex: 1, minHeight: 0 }} className="mt-2">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart
            data={chartData}
            margin={{ top: 0, right: 30, left: 20, bottom: 5 }}
          >
            <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
            <XAxis dataKey="date" hide />
            <YAxis
              tick={{ fontSize: 10, fill: '#6b7280' }}
              stroke="#9ca3af"
              tickLine={false}
              tickFormatter={formatVolume}
            />
            <Tooltip
              content={({ active, payload }: any) => {
                if (active && payload && payload.length) {
                  const d = payload[0].payload;
                  return (
                    <div className="bg-white px-3 py-2 border border-gray-200 rounded shadow-lg">
                      <p className="text-xs text-gray-900 font-medium">
                        거래량: {d.volume.toLocaleString()}
                      </p>
                    </div>
                  );
                }
                return null;
              }}
            />
            <Bar dataKey="volume">
              {chartData.map((entry, index) => (
                <Cell
                  key={`volume-${index}`}
                  fill={entry.isPositive ? '#10b98160' : '#ef444460'}
                />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
};

export default CandlestickChart;
