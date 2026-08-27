import React, { useEffect, useRef, useState, useCallback } from 'react';
import { init, dispose, type Chart } from 'klinecharts';

// 목 데이터 생성 (데이터 없을 때 UI 확인용)
function generateMockData(days = 365): OHLCData[] {
  const result: OHLCData[] = [];
  let price = 180;
  const now = new Date();
  for (let i = days; i >= 0; i--) {
    const date = new Date(now);
    date.setDate(date.getDate() - i);
    const day = date.getDay();
    if (day === 0 || day === 6) continue;
    const change = (Math.random() - 0.48) * 4;
    const open = price;
    const close = Math.max(10, price + change);
    const high = Math.max(open, close) + Math.random() * 2;
    const low = Math.min(open, close) - Math.random() * 2;
    price = close;
    result.push({
      time: date.toISOString().split('T')[0],
      open: +open.toFixed(2),
      high: +high.toFixed(2),
      low: +low.toFixed(2),
      close: +close.toFixed(2),
      volume: Math.floor(Math.random() * 80000000 + 20000000),
    });
  }
  return result;
}

interface OHLCData {
  time: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

interface KLineChartComponentProps {
  data: OHLCData[];
  showIndicatorPanel?: boolean;
  height?: number;
}

interface IndicatorState {
  ma5: boolean;
  ma20: boolean;
  ma60: boolean;
  ma120: boolean;
  ma200: boolean;
  ema: boolean;
  boll: boolean;
  rsi: boolean;
  macd: boolean;
  kdj: boolean;
  vol: boolean;
}

const MA_PANE_ID = 'candle_pane';

function convertData(data: OHLCData[]) {
  return data.map(d => ({
    timestamp: new Date(d.time).getTime(),
    open: d.open,
    high: d.high,
    low: d.low,
    close: d.close,
    volume: d.volume,
  }));
}

function getDarkStyles() {
  return {
    grid: {
      horizontal: { color: '#334155' },
      vertical: { color: '#334155' },
    },
    candle: {
      bar: {
        upColor: '#ef4444',
        downColor: '#3b82f6',
        noChangeColor: '#94a3b8',
        upBorderColor: '#ef4444',
        downBorderColor: '#3b82f6',
        noChangeBorderColor: '#94a3b8',
        upWickColor: '#ef4444',
        downWickColor: '#3b82f6',
        noChangeWickColor: '#94a3b8',
      },
      tooltip: {
        labels: ['T', 'O', 'H', 'L', 'C', 'V'],
        values: null,
        defaultValue: 'n/a',
      },
    },
    indicator: {
      ohlc: {
        upColor: '#ef4444',
        downColor: '#3b82f6',
        noChangeColor: '#94a3b8',
      },
    },
    xAxis: {
      axisLine: { color: '#475569' },
      tickLine: { color: '#475569' },
      tickText: { color: '#94a3b8' },
    },
    yAxis: {
      axisLine: { color: '#475569' },
      tickLine: { color: '#475569' },
      tickText: { color: '#94a3b8' },
    },
    separator: {
      color: '#334155',
    },
    crosshair: {
      horizontal: { line: { color: '#475569' }, text: { backgroundColor: '#334155', color: '#e2e8f0' } },
      vertical: { line: { color: '#475569' }, text: { backgroundColor: '#334155', color: '#e2e8f0' } },
    },
  };
}

function getLightStyles() {
  return {
    grid: {
      horizontal: { color: '#e2e8f0' },
      vertical: { color: '#e2e8f0' },
    },
    candle: {
      bar: {
        upColor: '#ef4444',
        downColor: '#3b82f6',
        noChangeColor: '#64748b',
        upBorderColor: '#ef4444',
        downBorderColor: '#3b82f6',
        noChangeBorderColor: '#64748b',
        upWickColor: '#ef4444',
        downWickColor: '#3b82f6',
        noChangeWickColor: '#64748b',
      },
    },
    xAxis: {
      axisLine: { color: '#cbd5e1' },
      tickLine: { color: '#cbd5e1' },
      tickText: { color: '#64748b' },
    },
    yAxis: {
      axisLine: { color: '#cbd5e1' },
      tickLine: { color: '#cbd5e1' },
      tickText: { color: '#64748b' },
    },
    separator: {
      color: '#e2e8f0',
    },
    crosshair: {
      horizontal: { line: { color: '#94a3b8' }, text: { backgroundColor: '#f1f5f9', color: '#1e293b' } },
      vertical: { line: { color: '#94a3b8' }, text: { backgroundColor: '#f1f5f9', color: '#1e293b' } },
    },
  };
}

const KLineChartComponent: React.FC<KLineChartComponentProps> = ({
  data: rawData,
  showIndicatorPanel = false,
  height = 600,
}) => {
  const data = rawData.length > 0 ? rawData : generateMockData();
  const isMock = rawData.length === 0;

  // 모바일에서는 600px 차트가 화면을 다 먹는다. 폭 기준으로 줄인다.
  const [isNarrow, setIsNarrow] = useState(
    typeof window !== 'undefined' && window.innerWidth < 768
  );
  useEffect(() => {
    const onResize = () => setIsNarrow(window.innerWidth < 768);
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);
  const chartHeight = isNarrow ? Math.min(height, 380) : height;

  const chartContainerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<Chart | null>(null);
  const panelIdsRef = useRef<Record<string, string | null>>({
    rsi: null,
    macd: null,
    kdj: null,
    vol: null,
  });

  const [activeTool, setActiveTool] = useState<string | null>(null);

  const DRAW_TOOLS = [
    { name: 'straightLine',           label: '╱', title: '추세선' },
    { name: 'horizontalStraightLine', label: '—', title: '수평선' },
    { name: 'verticalStraightLine',   label: '|', title: '수직선' },
    { name: 'fibonacciLine',          label: 'Fib', title: '피보나치' },
    { name: 'arrow',                  label: '↗', title: '화살표' },
  ];

  const startDrawing = (toolName: string) => {
    if (!chartRef.current) return;
    if (activeTool === toolName) {
      chartRef.current.removeOverlay();
      setActiveTool(null);
    } else {
      chartRef.current.createOverlay({ name: toolName });
      setActiveTool(toolName);
    }
  };

  const clearDrawings = () => {
    if (!chartRef.current) return;
    chartRef.current.removeOverlay();
    setActiveTool(null);
  };

  const [indicators, setIndicators] = useState<IndicatorState>({
    ma5: false,
    ma20: true,
    ma60: false,
    ma120: false,
    ma200: false,
    ema: false,
    boll: false,
    rsi: false,
    macd: false,
    kdj: false,
    vol: true,
  });

  const isDark = () => document.documentElement.dataset.theme !== 'light';

  // MA periods currently active
  const getActiveMaPeriods = useCallback((state: IndicatorState) => {
    const periods: number[] = [];
    if (state.ma5) periods.push(5);
    if (state.ma20) periods.push(20);
    if (state.ma60) periods.push(60);
    if (state.ma120) periods.push(120);
    if (state.ma200) periods.push(200);
    return periods;
  }, []);

  const applyMAIndicator = useCallback((chart: Chart, state: IndicatorState) => {
    chart.removeIndicator(MA_PANE_ID, 'MA');
    const periods = getActiveMaPeriods(state);
    if (periods.length > 0) {
      chart.createIndicator(
        { name: 'MA', calcParams: periods },
        true,
        { id: MA_PANE_ID }
      );
    }
  }, [getActiveMaPeriods]);

  // Initialize chart
  useEffect(() => {
    if (!chartContainerRef.current) return;

    // cleanup 시점에는 ref 가 이미 바뀌어 있을 수 있다. 지금 값을 붙잡아 둔다.
    const container = chartContainerRef.current;

    const originalLog = console.log;
    console.log = () => {};
    let chart;
    try {
      chart = init(container);
    } finally {
      console.log = originalLog;
    }

    if (!chart) {
      console.error('[KLineChart] init() returned null');
      return;
    }
    chartRef.current = chart;

    try {
      chart.setStyles(isDark() ? getDarkStyles() : getLightStyles());
    } catch (e) {
      console.warn('[KLineChart] setStyles failed:', e);
    }

    // 초기 데이터는 아래 "Update data when it changes" effect 가 마운트 직후에도
    // 한 번 돌면서 넣는다. 여기서 또 넣으면 data 가 의존성이 되어 데이터가 바뀔 때마다
    // 차트를 다시 만들게 된다.

    // Default: MA20 + VOL (서브패널)
    try {
      chart.createIndicator({ name: 'MA', calcParams: [20] }, true, { id: MA_PANE_ID });
      const volId = chart.createIndicator('VOL', false, { height: 80 });
      panelIdsRef.current.vol = volId;
    } catch (e) {
      console.warn('[KLineChart] createIndicator failed:', e);
    }

    // 컨테이너 폭이 바뀌면 캔버스를 다시 잡아야 한다. 안 부르면 화면을 돌리거나
    // 지표 패널이 사라져도 캔버스가 옛 크기로 남는다.
    const observer = new ResizeObserver(() => {
      chartRef.current?.resize();
    });
    observer.observe(container);

    return () => {
      observer.disconnect();
      dispose(container);
      chartRef.current = null;
    };
  }, []);

  // Update data when it changes
  useEffect(() => {
    if (chartRef.current && data.length > 0) {
      chartRef.current.applyNewData(convertData(data));
    }
  }, [data]);

  // 높이가 바뀌면(모바일 <-> 데스크탑) 캔버스를 다시 잡는다.
  // 위 ResizeObserver 가 폭을 맡고, 이쪽이 높이를 맡는다.
  useEffect(() => {
    chartRef.current?.resize();
  }, [chartHeight]);


  // Update theme
  useEffect(() => {
    const observer = new MutationObserver(() => {
      if (chartRef.current) {
        chartRef.current.setStyles(isDark() ? getDarkStyles() : getLightStyles());
      }
    });
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
    return () => observer.disconnect();
  }, []);

  const toggleIndicator = (key: keyof IndicatorState) => {
    if (!chartRef.current) return;
    const chart = chartRef.current;

    setIndicators(prev => {
      const next = { ...prev, [key]: !prev[key] };

      // MA 계열 — 한 번에 재생성
      if (['ma5', 'ma20', 'ma60', 'ma120', 'ma200'].includes(key)) {
        applyMAIndicator(chart, next);
        return next;
      }

      if (key === 'ema') {
        if (next.ema) {
          chart.createIndicator({ name: 'EMA', calcParams: [12, 26] }, true, { id: MA_PANE_ID });
        } else {
          chart.removeIndicator(MA_PANE_ID, 'EMA');
        }
      }

      if (key === 'boll') {
        if (next.boll) {
          chart.createIndicator('BOLL', true, { id: MA_PANE_ID });
        } else {
          chart.removeIndicator(MA_PANE_ID, 'BOLL');
        }
      }

      if (key === 'rsi') {
        if (next.rsi) {
          const id = chart.createIndicator('RSI', false, { height: 80 });
          panelIdsRef.current.rsi = id;
        } else if (panelIdsRef.current.rsi) {
          chart.removeIndicator(panelIdsRef.current.rsi, 'RSI');
          panelIdsRef.current.rsi = null;
        }
      }

      if (key === 'macd') {
        if (next.macd) {
          const id = chart.createIndicator('MACD', false, { height: 80 });
          panelIdsRef.current.macd = id;
        } else if (panelIdsRef.current.macd) {
          chart.removeIndicator(panelIdsRef.current.macd, 'MACD');
          panelIdsRef.current.macd = null;
        }
      }

      if (key === 'kdj') {
        if (next.kdj) {
          const id = chart.createIndicator('KDJ', false, { height: 80 });
          panelIdsRef.current.kdj = id;
        } else if (panelIdsRef.current.kdj) {
          chart.removeIndicator(panelIdsRef.current.kdj, 'KDJ');
          panelIdsRef.current.kdj = null;
        }
      }

      if (key === 'vol') {
        if (next.vol) {
          const id = chart.createIndicator('VOL', false, { height: 80 });
          panelIdsRef.current.vol = id;
        } else if (panelIdsRef.current.vol) {
          chart.removeIndicator(panelIdsRef.current.vol, 'VOL');
          panelIdsRef.current.vol = null;
        }
      }

      return next;
    });
  };

  const IndicatorCheckbox = ({
    label,
    indicatorKey,
    color,
  }: {
    label: string;
    indicatorKey: keyof IndicatorState;
    color?: string;
  }) => (
    <label className="flex items-center gap-2 py-1 px-2 rounded cursor-pointer hover:bg-hover/50 transition-colors">
      <input
        type="checkbox"
        checked={indicators[indicatorKey]}
        onChange={() => toggleIndicator(indicatorKey)}
        className="w-3.5 h-3.5 accent-brand"
      />
      <span className="text-xs text-tx-2 select-none" style={color ? { color } : undefined}>
        {label}
      </span>
    </label>
  );

  return (
    <div className="flex w-full relative">
      {isMock && (
        <div className="absolute top-2 left-2 z-10 bg-amber-500/20 border border-amber-500/40 text-amber-400 text-[10px] font-medium px-2 py-0.5 rounded pointer-events-none">
          목 데이터 (백엔드 연결 필요)
        </div>
      )}
      {/* 드로잉 툴바 (왼쪽 세로) */}
      <div
        className="flex-shrink-0 w-9 bg-surface border-r border-line/60 flex flex-col items-center py-2 gap-1"
        style={{ height: chartHeight }}
      >
        {DRAW_TOOLS.map(tool => (
          <button
            key={tool.name}
            title={tool.title}
            onClick={() => startDrawing(tool.name)}
            className={`w-7 h-7 rounded text-[12px] font-mono flex items-center justify-center transition-colors
              ${activeTool === tool.name
                ? 'bg-brand text-white'
                : 'text-tx-3 hover:bg-hover hover:text-tx-1'
              }`}
          >
            {tool.label}
          </button>
        ))}
        <div className="border-t border-line/60 w-5 my-1" />
        <button
          title="전체 삭제"
          onClick={clearDrawings}
          className="w-7 h-7 rounded text-[11px] flex items-center justify-center text-tx-3 hover:bg-red-500/15 hover:text-red-400 transition-colors"
        >
          ✕
        </button>
      </div>

      {/* 차트 영역 */}
      <div className="flex-1 min-w-0">
        <div ref={chartContainerRef} style={{ width: '100%', height: chartHeight }} />
      </div>

      {/* 지표 사이드 패널. */}
      {showIndicatorPanel && (
        <div
          className="hidden md:block w-[160px] flex-shrink-0 bg-surface border-l border-line/60 overflow-y-auto"
          style={{ height: chartHeight }}
        >
          <div className="p-3">
            <p className="text-[11px] font-semibold text-tx-3 uppercase tracking-wider mb-2">지표 설정</p>

            {/* 이동평균 */}
            <div className="mb-3">
              <p className="text-[10px] font-medium text-tx-3 mb-1 px-2">이동평균선</p>
              <IndicatorCheckbox label="MA 5" indicatorKey="ma5" color="#60a5fa" />
              <IndicatorCheckbox label="MA 20" indicatorKey="ma20" color="#f59e0b" />
              <IndicatorCheckbox label="MA 60" indicatorKey="ma60" color="#a855f7" />
              <IndicatorCheckbox label="MA 120" indicatorKey="ma120" color="#22c55e" />
              <IndicatorCheckbox label="MA 200" indicatorKey="ma200" color="#f97316" />
            </div>

            <div className="border-t border-line/60 mb-3" />

            {/* EMA / 볼린저 */}
            <div className="mb-3">
              <p className="text-[10px] font-medium text-tx-3 mb-1 px-2">오버레이</p>
              <IndicatorCheckbox label="EMA 12·26" indicatorKey="ema" />
              <IndicatorCheckbox label="볼린저밴드" indicatorKey="boll" />
            </div>

            <div className="border-t border-line/60 mb-3" />

            {/* 오실레이터 */}
            <div className="mb-3">
              <p className="text-[10px] font-medium text-tx-3 mb-1 px-2">오실레이터</p>
              <IndicatorCheckbox label="RSI (14)" indicatorKey="rsi" />
              <IndicatorCheckbox label="MACD" indicatorKey="macd" />
              <IndicatorCheckbox label="스토캐스틱" indicatorKey="kdj" />
            </div>

            <div className="border-t border-line/60 mb-3" />

            {/* 거래량 */}
            <div>
              <p className="text-[10px] font-medium text-tx-3 mb-1 px-2">거래량</p>
              <IndicatorCheckbox label="거래량 MA" indicatorKey="vol" />
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default KLineChartComponent;
