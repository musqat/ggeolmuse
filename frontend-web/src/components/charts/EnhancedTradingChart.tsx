import React from 'react';
import {
  ComposedChart,
  Bar,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  ReferenceLine
} from 'recharts';

interface OHLCData {
  time: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

interface EnhancedTradingChartProps {
  data: OHLCData[];
  symbol: string;
  showMA5?: boolean;
  showMA20?: boolean;
  showMA60?: boolean;
}

// 이동평균 계산
const calculateMA = (data: OHLCData[], period: number): number[] => {
  const result: number[] = [];
  for (let i = 0; i < data.length; i++) {
    if (i < period - 1) {
      result.push(NaN);
    } else {
      const sum = data.slice(i - period + 1, i + 1).reduce((acc, item) => acc + item.close, 0);
      result.push(sum / period);
    }
  }
  return result;
};

// 커스텀 캔들스틱 모양
const Candlestick = (props: any) => {
  const { x, y, width, height, open, close, high, low, index, fill } = props;

  const isGrowing = close >= open;
  const color = isGrowing ? '#ef4444' : '#3b82f6'; // red for up, blue for down
  const yTop = isGrowing ? y + height : y;
  const yBottom = isGrowing ? y : y + height;
  const candleHeight = Math.abs(height);

  // 심지 위치 계산
  const wickX = x + width / 2;
  const highY = y - (high - Math.max(open, close)) * (height / (open - close || 1));
  const lowY = y + height + (Math.min(open, close) - low) * (height / (open - close || 1));

  return (
    <g>
      {/* 고가 심지 */}
      <line
        x1={wickX}
        y1={highY}
        x2={wickX}
        y2={yTop}
        stroke={color}
        strokeWidth={1}
      />
      {/* 저가 심지 */}
      <line
        x1={wickX}
        y1={yBottom}
        x2={wickX}
        y2={lowY}
        stroke={color}
        strokeWidth={1}
      />
      {/* 캔들 몸통 */}
      <rect
        x={x}
        y={yTop}
        width={width}
        height={Math.max(candleHeight, 1)}
        fill={color}
        stroke={color}
        strokeWidth={1}
      />
    </g>
  );
};

const EnhancedTradingChart: React.FC<EnhancedTradingChartProps> = ({
  data,
  symbol,
  showMA5 = false,
  showMA20 = false,
  showMA60 = false
}) => {
  if (data.length === 0) {
    return <div className="flex items-center justify-center h-full text-tx-2">No data available</div>;
  }

  // 차트 데이터 준비
  const chartData = data.map((item, index) => {
    const ma5 = showMA5 ? calculateMA(data, 5) : [];
    const ma20 = showMA20 ? calculateMA(data, 20) : [];
    const ma60 = showMA60 ? calculateMA(data, 60) : [];

    return {
      ...item,
      time: new Date(item.time).toLocaleDateString('ko-KR', { month: '2-digit', day: '2-digit' }),
      ma5: ma5[index],
      ma20: ma20[index],
      ma60: ma60[index],
      candle: item.close - item.open, // For candlestick rendering
    };
  });

  const latestData = data[data.length - 1];

  // 커스텀 툴팁
  const CustomTooltip = ({ active, payload }: any) => {
    if (active && payload && payload.length) {
      const data = payload[0].payload;
      return (
        <div className="bg-surface/95 backdrop-blur-sm border border-line rounded-lg shadow-lg p-4 text-sm">
          <p className="font-semibold text-tx-1 mb-2">{data.time}</p>
          <div className="grid grid-cols-2 gap-x-4 gap-y-1">
            <span className="text-tx-2">시가:</span>
            <span className="text-tx-1 font-medium">${data.open.toFixed(2)}</span>
            <span className="text-green-600">고가:</span>
            <span className="text-green-600 font-medium">${data.high.toFixed(2)}</span>
            <span className="text-red-600">저가:</span>
            <span className="text-red-600 font-medium">${data.low.toFixed(2)}</span>
            <span className="text-tx-2">종가:</span>
            <span className="text-tx-1 font-medium">${data.close.toFixed(2)}</span>
            <span className="text-tx-2">거래량:</span>
            <span className="text-tx-1 font-medium">{data.volume.toLocaleString()}</span>
          </div>
          {showMA5 && !isNaN(data.ma5) && (
            <div className="mt-2 pt-2 border-t border-line">
              <span className="text-blue-600">MA5: ${data.ma5.toFixed(2)}</span>
            </div>
          )}
          {showMA20 && !isNaN(data.ma20) && (
            <div>
              <span className="text-orange-600">MA20: ${data.ma20.toFixed(2)}</span>
            </div>
          )}
          {showMA60 && !isNaN(data.ma60) && (
            <div>
              <span className="text-purple-600">MA60: ${data.ma60.toFixed(2)}</span>
            </div>
          )}
        </div>
      );
    }
    return null;
  };

  return (
    <div className="relative w-full h-full bg-surface rounded-lg">
      {/* 가격 정보 - 상단 헤더 */}
      <div className="px-6 py-4 border-b border-line">
        <div className="flex items-center space-x-6">
          <div className="font-bold text-2xl text-tx-1">{symbol}</div>
          <div className="flex space-x-4 text-sm">
            <div><span className="text-tx-2">시가:</span><span className="font-semibold">${latestData.open.toFixed(2)}</span></div>
            <div><span className="text-tx-2">고가:</span><span className="font-semibold text-green-600">${latestData.high.toFixed(2)}</span></div>
            <div><span className="text-tx-2">저가:</span><span className="font-semibold text-red-600">${latestData.low.toFixed(2)}</span></div>
            <div><span className="text-tx-2">종가:</span><span className={`font-semibold ${latestData.close >= latestData.open ? 'text-red-600':'text-blue-600'}`}>${latestData.close.toFixed(2)}</span></div>
            <div><span className="text-tx-2">거래량:</span><span className="font-semibold">{latestData.volume.toLocaleString()}</span></div>
          </div>
        </div>
      </div>

      {/* 메인 차트 */}
      <ResponsiveContainer width="100%" height="65%">
        <ComposedChart data={chartData} margin={{ top: 20, right: 50, left: 10, bottom: 5 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" vertical={false} />
          <XAxis
            dataKey="time"
            tick={{ fontSize: 11, fill: '#666' }}
            stroke="#e0e0e0"
            interval="preserveStartEnd"
            minTickGap={50}
          />
          <YAxis
            domain={['dataMin - 5', 'dataMax + 5']}
            tick={{ fontSize: 11, fill: '#666' }}
            stroke="#e0e0e0"
            tickFormatter={(value) => `$${value.toFixed(0)}`}
            orientation="right"
          />
          <Tooltip content={<CustomTooltip />} cursor={{ stroke: '#999', strokeDasharray: '3 3' }} />
          <Legend
            wrapperStyle={{ paddingTop: '10px' }}
            iconType="line"
            verticalAlign="top"
          />

          {/* 캔들스틱 - Bar를 플레이스홀더로 사용 */}
          <Bar
            dataKey="candle"
            fill="#8884d8"
            shape={<Candlestick />}
            name="Price"
            maxBarSize={12}
          />

          {/* 이동평균선 */}
          {showMA5 && (
            <Line
              type="monotone"
              dataKey="ma5"
              stroke="#3b82f6"
              strokeWidth={2}
              dot={false}
              name="MA5"
              connectNulls
            />
          )}
          {showMA20 && (
            <Line
              type="monotone"
              dataKey="ma20"
              stroke="#f59e0b"
              strokeWidth={2}
              dot={false}
              name="MA20"
              connectNulls
            />
          )}
          {showMA60 && (
            <Line
              type="monotone"
              dataKey="ma60"
              stroke="#a855f7"
              strokeWidth={2}
              dot={false}
              name="MA60"
              connectNulls
            />
          )}
        </ComposedChart>
      </ResponsiveContainer>

      {/* 거래량 차트 */}
      <ResponsiveContainer width="100%" height="28%">
        <ComposedChart data={chartData} margin={{ top: 0, right: 30, left: 0, bottom: 5 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
          <XAxis
            dataKey="time"
            tick={{ fontSize: 12, fill: '#666' }}
            stroke="#e0e0e0"
          />
          <YAxis
            tick={{ fontSize: 12, fill: '#666' }}
            stroke="#e0e0e0"
            tickFormatter={(value) => `${(value / 1000000).toFixed(1)}M`}
          />
          <Tooltip
            formatter={(value: any) => [value.toLocaleString(), '거래량']}
            labelFormatter={(label) => `날짜: ${label}`}
          />
          <Bar
            dataKey="volume"
            fill="#26a69a"
            opacity={0.7}
            name="거래량"
          />
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
};

export default EnhancedTradingChart;
