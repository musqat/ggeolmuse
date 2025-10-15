import React, { useMemo } from 'react';
import {
  ComposedChart,
  Line,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Cell,
} from 'recharts';

interface OHLCData {
  date?: string;
  time?: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume?: number;
}

interface ProfessionalCandlestickChartProps {
  data: OHLCData[];
  symbol: string;
  showMA5?: boolean;
  showMA20?: boolean;
  showMA60?: boolean;
}

// 이동평균 계산
const calculateMA = (data: OHLCData[], period: number): (number | null)[] => {
  const ma: (number | null)[] = [];
  for (let i = 0; i < data.length; i++) {
    if (i < period - 1) {
      ma.push(null);
    } else {
      const sum = data
        .slice(i - period + 1, i + 1)
        .reduce((acc, item) => acc + (item.close || 0), 0);
      ma.push(sum / period);
    }
  }
  return ma;
};

const ProfessionalCandlestickChart: React.FC<ProfessionalCandlestickChartProps> = ({
  data,
  symbol,
  showMA5 = true,
  showMA20 = true,
  showMA60 = true,
}) => {
  const chartData = useMemo(() => {
    if (!data || data.length === 0) return [];

    const ma5 = showMA5 ? calculateMA(data, 5) : [];
    const ma20 = showMA20 ? calculateMA(data, 20) : [];
    const ma60 = showMA60 ? calculateMA(data, 60) : [];

    return data.map((item, index) => {
      const open = item.open || 0;
      const close = item.close || 0;
      const high = item.high || 0;
      const low = item.low || 0;
      const isPositive = close >= open;

      return {
        date: new Date(item.date || item.time || '').toLocaleDateString('ko-KR', {
          month: 'short',
          day: 'numeric',
        }),
        fullDate: item.date || item.time || '',
        open,
        high,
        low,
        close,
        volume: item.volume || 0,
        isPositive,
        ma5: showMA5 && ma5[index] !== null ? ma5[index] : undefined,
        ma20: showMA20 && ma20[index] !== null ? ma20[index] : undefined,
        ma60: showMA60 && ma60[index] !== null ? ma60[index] : undefined,
      };
    });
  }, [data, showMA5, showMA20, showMA60]);

  if (!chartData || chartData.length === 0) {
    return (
      <div className="h-full flex items-center justify-center text-gray-400">
        차트 데이터가 없습니다
      </div>
    );
  }

  const CustomTooltip = ({ active, payload }: any) => {
    if (!active || !payload || !payload.length) return null;

    const d = payload[0].payload;
    if (!d) return null;

    const priceChange = d.close - d.open;
    const priceChangePercent = d.open ? ((priceChange / d.open) * 100).toFixed(2) : '0.00';

    return (
      <div className="bg-white p-4 border border-gray-300 rounded-lg shadow-xl">
        <p className="font-semibold text-sm mb-3 text-gray-900">{d.fullDate}</p>

        <div className="space-y-2">
          <div className="grid grid-cols-2 gap-x-6 gap-y-1.5 text-xs">
            <div className="flex justify-between">
              <span className="text-gray-600">시가:</span>
              <span className="font-semibold text-gray-900">${d.open.toFixed(2)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-600">종가:</span>
              <span className="font-semibold text-gray-900">${d.close.toFixed(2)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-600">고가:</span>
              <span className="font-semibold text-green-600">${d.high.toFixed(2)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-600">저가:</span>
              <span className="font-semibold text-red-600">${d.low.toFixed(2)}</span>
            </div>
          </div>

          <div
            className={`flex justify-between text-xs pt-2 border-t ${
              d.isPositive ? 'text-green-600' : 'text-red-600'
            }`}
          >
            <span className="font-medium">변동:</span>
            <span className="font-semibold">
              {priceChange >= 0 ? '+' : ''}
              {priceChange.toFixed(2)} ({priceChangePercent}%)
            </span>
          </div>

          {(d.ma5 || d.ma20 || d.ma60) && (
            <div className="pt-2 border-t border-gray-200">
              <div className="grid grid-cols-3 gap-3 text-xs">
                {d.ma5 && (
                  <div className="flex flex-col">
                    <span className="text-gray-500 text-[10px]">MA5</span>
                    <span className="font-semibold text-blue-600">${d.ma5.toFixed(2)}</span>
                  </div>
                )}
                {d.ma20 && (
                  <div className="flex flex-col">
                    <span className="text-gray-500 text-[10px]">MA20</span>
                    <span className="font-semibold text-orange-600">${d.ma20.toFixed(2)}</span>
                  </div>
                )}
                {d.ma60 && (
                  <div className="flex flex-col">
                    <span className="text-gray-500 text-[10px]">MA60</span>
                    <span className="font-semibold text-purple-600">${d.ma60.toFixed(2)}</span>
                  </div>
                )}
              </div>
            </div>
          )}

          <div className="pt-2 border-t border-gray-200 text-xs">
            <div className="flex justify-between">
              <span className="text-gray-600">거래량:</span>
              <span className="font-semibold text-gray-900">{d.volume.toLocaleString()}</span>
            </div>
          </div>
        </div>
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

  // 커스텀 캔들스틱 모양
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

    const bodyColor = isPositive ? '#10b981' : '#ef4444';
    const wickColor = isPositive ? '#059669' : '#dc2626';

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

  return (
    <div className="flex flex-col h-full">
      {/* 메인 가격 차트 */}
      <div style={{ flex: 3, minHeight: 0 }}>
        <ResponsiveContainer width="100%" height="100%">
          <ComposedChart
            data={chartData}
            margin={{ top: 10, right: 10, left: 0, bottom: 0 }}
          >
            <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
            <XAxis
              dataKey="date"
              tick={{ fontSize: 11, fill: '#6b7280' }}
              stroke="#d1d5db"
              tickLine={false}
              minTickGap={40}
            />
            <YAxis
              domain={['auto', 'auto']}
              tick={{ fontSize: 11, fill: '#6b7280' }}
              stroke="#d1d5db"
              tickLine={false}
              width={60}
              tickFormatter={(value) => (value ? `$${Number(value).toFixed(0)}` : '$0')}
            />
            <Tooltip content={<CustomTooltip />} />

            {/* 캔들스틱 - 커스텀 렌더링 */}
            <Bar
              dataKey="close"
              shape={renderCandlestick}
              isAnimationActive={false}
            />

            {/* 이동평균선 */}
            {showMA5 && (
              <Line
                type="monotone"
                dataKey="ma5"
                stroke="#3b82f6"
                strokeWidth={1.5}
                dot={false}
                connectNulls
                isAnimationActive={false}
              />
            )}
            {showMA20 && (
              <Line
                type="monotone"
                dataKey="ma20"
                stroke="#f97316"
                strokeWidth={1.5}
                dot={false}
                connectNulls
                isAnimationActive={false}
              />
            )}
            {showMA60 && (
              <Line
                type="monotone"
                dataKey="ma60"
                stroke="#a855f7"
                strokeWidth={1.5}
                dot={false}
                connectNulls
                isAnimationActive={false}
              />
            )}
          </ComposedChart>
        </ResponsiveContainer>
      </div>

      {/* 거래량 차트 */}
      <div style={{ flex: 1, minHeight: 0 }} className="mt-2">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart
            data={chartData}
            margin={{ top: 0, right: 10, left: 0, bottom: 5 }}
          >
            <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
            <XAxis dataKey="date" hide />
            <YAxis
              tick={{ fontSize: 10, fill: '#6b7280' }}
              stroke="#d1d5db"
              tickLine={false}
              width={60}
              tickFormatter={formatVolume}
            />
            <Tooltip
              content={({ active, payload }: any) => {
                if (active && payload && payload.length) {
                  const d = payload[0].payload;
                  return (
                    <div className="bg-white px-3 py-2 border border-gray-300 rounded shadow-lg">
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

export default ProfessionalCandlestickChart;
