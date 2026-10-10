import React, { useEffect, useRef, useState } from 'react';
import { init, dispose, LineType, TooltipShowRule, type Chart } from 'klinecharts';
import type { CandlestickChartData } from '../../types/ohlc';
import { IndicatorPanel } from './indicators/IndicatorPanel';
import { IndicatorSheet } from './indicators/IndicatorSheet';
import {
  DEFAULT_INDICATORS,
  MA_LINES,
  type IndicatorKey,
  type IndicatorState,
} from './indicators/indicators';

interface KLineChartComponentProps {
  data: CandlestickChartData[];
  showIndicatorPanel?: boolean;
  height?: number;
}

const MA_PANE_ID = 'candle_pane';

// 아래에 패널을 따로 붙이는 지표와 klinecharts 이름
const PANE_INDICATORS = { rsi: 'RSI', macd: 'MACD', kdj: 'KDJ', vol: 'VOL' } as const;
type PaneKey = keyof typeof PANE_INDICATORS;

// 아래 패널 하나 높이. 좁은 화면은 줄인다
const PANE_HEIGHT = 100;
const NARROW_PANE_HEIGHT = 80;
// 아래 패널 위 여백(px). 범례 한 줄이 막대 · 선과 겹치지 않게 비운다
const PANE_GAP = { top: 32 };

function convertData(data: CandlestickChartData[]) {
  return data.map(d => ({
    timestamp: new Date(d.time).getTime(),
    open: d.open,
    high: d.high,
    low: d.low,
    close: d.close,
    volume: d.volume ?? 0,
  }));
}

// 켜진 이동평균을 MA 지표 하나로 묶는다. 선 색을 지표 목록 색에 맞춘다
function maIndicator(state: IndicatorState) {
  const active = MA_LINES.filter(({ key }) => state[key]);
  return {
    name: 'MA',
    calcParams: active.map(({ period }) => period),
    styles: {
      lines: active.map(({ color }) => ({
        style: LineType.Solid,
        smooth: false,
        size: 1,
        color,
        dashedValue: [2, 2],
      })),
    },
  };
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

const DRAW_TOOLS = [
  { name: 'straightLine',           label: '╱', title: '추세선' },
  { name: 'horizontalStraightLine', label: '—', title: '수평선' },
  { name: 'verticalStraightLine',   label: '|', title: '수직선' },
  { name: 'fibonacciLine',          label: 'Fib', title: '피보나치' },
  { name: 'arrow',                  label: '↗', title: '화살표' },
];

const isDark = () => document.documentElement.dataset.theme !== 'light';

const KLineChartComponent: React.FC<KLineChartComponentProps> = ({
  data,
  showIndicatorPanel = false,
  height = 600,
}) => {
  // 모바일에서는 600px 차트가 화면을 다 먹는다. 폭 기준으로 줄인다.
  const [isNarrow, setIsNarrow] = useState(
    typeof window !== 'undefined' && window.innerWidth < 768
  );
  useEffect(() => {
    const onResize = () => setIsNarrow(window.innerWidth < 768);
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  const chartContainerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<Chart | null>(null);
  const panelIdsRef = useRef<Record<PaneKey, string | null>>({
    rsi: null,
    macd: null,
    kdj: null,
    vol: null,
  });

  const [activeTool, setActiveTool] = useState<string | null>(null);
  const [indicators, setIndicators] = useState<IndicatorState>(DEFAULT_INDICATORS);

  // height 는 거래량 패널 하나가 붙은 높이. 아래 패널이 늘면 그만큼 키워 캔들 영역이 줄지 않게 한다
  const paneHeight = isNarrow ? NARROW_PANE_HEIGHT : PANE_HEIGHT;
  const baseHeight = isNarrow ? Math.min(height, 380) : height;
  const paneCount = (Object.keys(PANE_INDICATORS) as PaneKey[]).filter((key) => indicators[key]).length;
  const chartHeight = baseHeight + paneHeight * (paneCount - 1);

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
      chart.createIndicator(maIndicator(DEFAULT_INDICATORS), true, { id: MA_PANE_ID });
      const volHeight = window.innerWidth < 768 ? NARROW_PANE_HEIGHT : PANE_HEIGHT;
      panelIdsRef.current.vol = chart.createIndicator('VOL', false, { height: volHeight, gap: PANE_GAP });
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

  // Update data when it changes. 쓰는 쪽이 같은 배열을 넘기면 다시 그리지 않는다
  useEffect(() => {
    chartRef.current?.applyNewData(convertData(data));
  }, [data]);

  // 좁은 화면은 범례 글자가 여러 줄로 감겨 캔들을 덮는다. 차트를 눌러 십자선이 뜰 때만 보인다
  useEffect(() => {
    const showRule = isNarrow ? TooltipShowRule.FollowCross : TooltipShowRule.Always;
    chartRef.current?.setStyles({ candle: { tooltip: { showRule } }, indicator: { tooltip: { showRule } } });
  }, [isNarrow]);

  // 높이가 바뀌면(모바일 <-> 데스크탑, 아래 패널 수) 캔버스를 다시 잡는다.
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

  // 지표 하나를 켜거나 끈다. next 는 바꾼 뒤 상태
  const applyIndicator = (chart: Chart, key: IndicatorKey, next: IndicatorState) => {
    // MA 계열 — 한 번에 재생성
    if (MA_LINES.some((line) => line.key === key)) {
      chart.removeIndicator(MA_PANE_ID, 'MA');
      const ma = maIndicator(next);
      if (ma.calcParams.length > 0) {
        chart.createIndicator(ma, true, { id: MA_PANE_ID });
      }
      return;
    }

    if (key === 'ema') {
      if (next.ema) {
        chart.createIndicator({ name: 'EMA', calcParams: [12, 26] }, true, { id: MA_PANE_ID });
      } else {
        chart.removeIndicator(MA_PANE_ID, 'EMA');
      }
      return;
    }

    if (key === 'boll') {
      if (next.boll) {
        chart.createIndicator('BOLL', true, { id: MA_PANE_ID });
      } else {
        chart.removeIndicator(MA_PANE_ID, 'BOLL');
      }
      return;
    }

    const paneKey = key as PaneKey;
    const name = PANE_INDICATORS[paneKey];
    if (next[paneKey]) {
      panelIdsRef.current[paneKey] = chart.createIndicator(name, false, { height: paneHeight, gap: PANE_GAP });
    } else if (panelIdsRef.current[paneKey]) {
      chart.removeIndicator(panelIdsRef.current[paneKey]!, name);
      panelIdsRef.current[paneKey] = null;
    }
  };

  // 다음 상태를 먼저 정하고 차트는 setState 밖에서 바꾼다.
  // 업데이터 함수는 StrictMode 에서 두 번 불려 그 안에서 바꾸면 패널이 두 번 생긴다.
  const toggleIndicator = (key: IndicatorKey) => {
    const chart = chartRef.current;
    if (!chart) return;
    const next = { ...indicators, [key]: !indicators[key] };
    applyIndicator(chart, key, next);
    setIndicators(next);
  };

  return (
    <div className="w-full">
      {showIndicatorPanel && <IndicatorSheet indicators={indicators} onToggle={toggleIndicator} />}
      <div className="flex w-full relative">
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
                  ? 'bg-brand text-brand-ink'
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
            className="w-7 h-7 rounded text-[11px] flex items-center justify-center text-tx-3 hover:bg-danger/15 hover:text-danger transition-colors"
          >
            ✕
          </button>
        </div>

        {/* 차트 영역 */}
        <div className="flex-1 min-w-0">
          <div ref={chartContainerRef} style={{ width: '100%', height: chartHeight }} />
        </div>

        {showIndicatorPanel && (
          <IndicatorPanel indicators={indicators} onToggle={toggleIndicator} height={chartHeight} />
        )}
      </div>
    </div>
  );
};

export default KLineChartComponent;
