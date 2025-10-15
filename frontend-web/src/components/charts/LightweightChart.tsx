import React, { useEffect, useRef } from 'react';
import { createChart, ColorType, type CandlestickData, type Time } from 'lightweight-charts';

interface OHLCData {
  time: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

interface LightweightChartProps {
  data: OHLCData[];
  symbol: string;
  showMA5?: boolean;
  showMA20?: boolean;
  showMA60?: boolean;
  onToggleMA5?: () => void;
  onToggleMA20?: () => void;
  onToggleMA60?: () => void;
}

const calculateMA = (data: OHLCData[], period: number): { time: Time; value: number }[] => {
  const result: { time: Time; value: number }[] = [];
  for (let i = 0; i < data.length; i++) {
    if (i < period - 1) {
      continue;
    }
    const sum = data.slice(i - period + 1, i + 1).reduce((acc, item) => acc + item.close, 0);
    result.push({
      time: data[i].time as Time,
      value: sum / period
    });
  }
  return result;
};

const LightweightChart: React.FC<LightweightChartProps> = ({
  data,
  symbol,
  showMA5 = false,
  showMA20 = false,
  showMA60 = false,
  onToggleMA5,
  onToggleMA20,
  onToggleMA60,
}) => {
  const chartContainerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<any>(null);

  useEffect(() => {
    if (!chartContainerRef.current || data.length === 0) return;

    // TradingView 속성 링크 숨김
    const hideAttribution = () => {
      const tvLink = chartContainerRef.current?.querySelector('a[href*="tradingview.com"]');
      if (tvLink) {
        (tvLink as HTMLElement).style.display = 'none';
      }
    };

    // 차트 생성
    const chart = createChart(chartContainerRef.current, {
      layout: {
        background: { type: ColorType.Solid, color: '#ffffff' },
        textColor: '#333',
      },
      grid: {
        vertLines: { color: '#f0f0f0' },
        horzLines: { color: '#f0f0f0' },
      },
      width: chartContainerRef.current.clientWidth,
      height: 600,
      rightPriceScale: {
        borderColor: '#e0e0e0',
        scaleMargins: {
          top: 0.1,
          bottom: 0.25,
        },
      },
      timeScale: {
        borderColor: '#e0e0e0',
        timeVisible: true,
        secondsVisible: false,
      },
      crosshair: {
        mode: 1,
      },
      watermark: {
        visible: false,
      },
    });

    chartRef.current = chart;

    // 캔들스틱 시리즈 추가
    const candlestickSeries = chart.addCandlestickSeries({
      upColor: '#ef4444',
      downColor: '#3b82f6',
      borderUpColor: '#ef4444',
      borderDownColor: '#3b82f6',
      wickUpColor: '#ef4444',
      wickDownColor: '#3b82f6',
    });

    // lightweight-charts용 데이터 포맷
    const candleData: CandlestickData[] = data.map(item => ({
      time: item.time as Time,
      open: item.open,
      high: item.high,
      low: item.low,
      close: item.close,
    }));

    candlestickSeries.setData(candleData);

    // 이동평균선 추가
    if (showMA5) {
      const ma5Series = chart.addLineSeries({
        color: '#3b82f6',
        lineWidth: 2,
        title: 'MA5',
      });
      ma5Series.setData(calculateMA(data, 5));
    }

    if (showMA20) {
      const ma20Series = chart.addLineSeries({
        color: '#f59e0b',
        lineWidth: 2,
        title: 'MA20',
      });
      ma20Series.setData(calculateMA(data, 20));
    }

    if (showMA60) {
      const ma60Series = chart.addLineSeries({
        color: '#a855f7',
        lineWidth: 2,
        title: 'MA60',
      });
      ma60Series.setData(calculateMA(data, 60));
    }

    // 거래량 시리즈 추가
    const volumeSeries = chart.addHistogramSeries({
      color: '#26a69a',
      priceFormat: {
        type: 'volume',
      },
      priceScaleId: 'volume',
    });

    chart.priceScale('volume').applyOptions({
      scaleMargins: {
        top: 0.75,
        bottom: 0,
      },
    });

    const volumeData = data.map(item => ({
      time: item.time as Time,
      value: item.volume,
      color: item.close >= item.open ? '#ef444480' : '#3b82f680',
    }));

    volumeSeries.setData(volumeData);

    // 콘텐츠 맞춤
    chart.timeScale().fitContent();

    // 차트 렌더링 후 속성 숨김
    setTimeout(hideAttribution, 100);

    // 크기 조정 처리
    const handleResize = () => {
      if (chartContainerRef.current) {
        chart.applyOptions({
          width: chartContainerRef.current.clientWidth,
        });
      }
    };

    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
      chart.remove();
    };
  }, [data, showMA5, showMA20, showMA60]);

  if (data.length === 0) {
    return (
      <div className="flex items-center justify-center h-[600px] text-gray-500">
        No data available
      </div>
    );
  }

  const latestData = data[data.length - 1];
  const priceChange = latestData.close - latestData.open;
  const priceChangePercent = (priceChange / latestData.open) * 100;

  return (
    <div className="w-full bg-white rounded-lg relative">
      {/* 차트 컨테이너 - 깔끔하게, 오버레이 없음 */}
      <div ref={chartContainerRef} className="w-full" />
    </div>
  );
};

export default LightweightChart;
