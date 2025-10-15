import React, { useState } from 'react';

interface PieChartData {
  symbol: string;
  value: number;
  color: string;
  quantity?: number;
  currentPrice?: number;
}

interface PortfolioPieChartProps {
  data: PieChartData[];
}

const PortfolioPieChart: React.FC<PortfolioPieChartProps> = ({ data }) => {
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);
  const [mousePosition, setMousePosition] = useState({ x: 0, y: 0 });

  if (!data || data.length === 0) {
    return (
      <div className="flex items-center justify-center h-64 text-gray-400">
        <div className="text-center">
          <div className="text-4xl mb-2">📊</div>
          <p>데이터가 없습니다</p>
        </div>
      </div>
    );
  }

  // 총합 계산
  const total = data.reduce((sum, item) => sum + item.value, 0);

  if (total === 0) {
    return (
      <div className="flex items-center justify-center h-64 text-gray-400">
        <div className="text-center">
          <div className="text-4xl mb-2">📊</div>
          <p>데이터가 없습니다</p>
        </div>
      </div>
    );
  }

  // 데이터를 value 기준으로 정렬하고 상위 5개와 나머지 분리
  const sortedData = [...data].sort((a, b) => b.value - a.value);
  const top5 = sortedData.slice(0, 5);
  const others = sortedData.slice(5);

  // 기타(ETC) 항목이 있으면 추가
  const chartData = [...top5];
  if (others.length > 0) {
    const othersTotalValue = others.reduce((sum, item) => sum + item.value, 0);
    chartData.push({
      symbol: 'ETC',
      value: othersTotalValue,
      color: '#9ca3af', // gray-400
    });
  }

  // SVG 원형 차트 생성
  const size = 280;
  const center = size / 2;
  const radius = size / 2 - 20;

  let currentAngle = -90; // 12시 방향부터 시작

  const slices = chartData.map((item, index) => {
    const percentage = (item.value / total) * 100;
    const angle = (percentage / 100) * 360;

    // 원호 그리기
    const startAngle = currentAngle;
    const endAngle = currentAngle + angle;

    // 좌표 계산
    const x1 = center + radius * Math.cos((startAngle * Math.PI) / 180);
    const y1 = center + radius * Math.sin((startAngle * Math.PI) / 180);
    const x2 = center + radius * Math.cos((endAngle * Math.PI) / 180);
    const y2 = center + radius * Math.sin((endAngle * Math.PI) / 180);

    // 큰 호인지 확인
    const largeArcFlag = angle > 180 ? 1 : 0;

    // Path 생성
    const pathData = [
      `M ${center} ${center}`,
      `L ${x1} ${y1}`,
      `A ${radius} ${radius} 0 ${largeArcFlag} 1 ${x2} ${y2}`,
      'Z'
    ].join(' ');

    currentAngle = endAngle;

    return {
      path: pathData,
      color: item.color,
      symbol: item.symbol,
      value: item.value,
      quantity: item.quantity,
      currentPrice: item.currentPrice,
      percentage: percentage.toFixed(1)
    };
  });

  const handleMouseMove = (e: React.MouseEvent<SVGPathElement>) => {
    const svg = e.currentTarget.ownerSVGElement;
    if (svg) {
      const rect = svg.getBoundingClientRect();
      setMousePosition({
        x: e.clientX - rect.left,
        y: e.clientY - rect.top
      });
    }
  };

  return (
    <div className="flex flex-col md:flex-row gap-8 items-center md:items-start">
      {/* 왼쪽: 파이 차트 */}
      <div className="relative flex-shrink-0">
        <svg
          width={size}
          height={size}
          viewBox={`0 0 ${size} ${size}`}
          className="drop-shadow-md"
        >
          {slices.map((slice, index) => (
            <g key={index}>
              <path
                d={slice.path}
                fill={slice.color}
                className="transition-all duration-200 cursor-pointer"
                style={{
                  opacity: hoveredIndex === null || hoveredIndex === index ? 1 : 0.5,
                  transform: hoveredIndex === index ? 'scale(1.05)' : 'scale(1)',
                  transformOrigin: 'center',
                }}
                onMouseEnter={() => setHoveredIndex(index)}
                onMouseLeave={() => setHoveredIndex(null)}
                onMouseMove={handleMouseMove}
              />
            </g>
          ))}

          {/* 툴팁 */}
          {hoveredIndex !== null && (
            <g>
              <foreignObject
                x={mousePosition.x + 10}
                y={mousePosition.y - 40}
                width="200"
                height="100"
                style={{ pointerEvents: 'none' }}
              >
                <div className="bg-gray-900 text-white px-3 py-2 rounded-lg shadow-lg text-sm">
                  <div className="font-semibold">{slices[hoveredIndex].symbol}</div>
                  <div className="text-xs mt-1">
                    <div>금액: ${slices[hoveredIndex].value.toFixed(2)}</div>
                    {slices[hoveredIndex].quantity && (
                      <div>수량: {slices[hoveredIndex].quantity}주</div>
                    )}
                    {slices[hoveredIndex].currentPrice && (
                      <div>가격: ${slices[hoveredIndex].currentPrice.toFixed(2)}</div>
                    )}
                    <div>비중: {slices[hoveredIndex].percentage}%</div>
                  </div>
                </div>
              </foreignObject>
            </g>
          )}
        </svg>
      </div>

      {/* 오른쪽: 자산 목록 (상위 5개 + ETC) */}
      <div className="flex-1 w-full">
        <div className="space-y-3">
          {chartData.map((item, index) => {
            const percentage = ((item.value / total) * 100).toFixed(1);
            const isHovered = hoveredIndex === index;

            return (
              <div
                key={index}
                className={`flex items-center justify-between p-3 rounded-lg transition-all duration-200 ${
                  isHovered ? 'bg-gray-100' : 'bg-white'
                }`}
              >
                <div className="flex items-center gap-3 flex-1 mr-6">
                  <div
                    className="w-4 h-4 rounded-full flex-shrink-0"
                    style={{ backgroundColor: item.color }}
                  ></div>
                  <div className="font-medium text-gray-900">{item.symbol}</div>
                </div>
                <div className="font-semibold text-gray-900 flex-shrink-0">{percentage}%</div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};

export default PortfolioPieChart;
