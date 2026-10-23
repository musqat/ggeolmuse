export type IndicatorKey =
  | 'ma5'
  | 'ma20'
  | 'ma60'
  | 'ma120'
  | 'ma200'
  | 'ema'
  | 'boll'
  | 'rsi'
  | 'macd'
  | 'kdj'
  | 'vol';

export type IndicatorState = Record<IndicatorKey, boolean>;

// 아래 패널로 따로 그리는 지표와 klinecharts 이름
export const PANE_INDICATORS = { rsi: 'RSI', macd: 'MACD', kdj: 'KDJ', vol: 'VOL' } as const;
export type PaneKey = keyof typeof PANE_INDICATORS;

// 패널 지표 계산 기간(RSI 는 목록 라벨대로 14 하나, klinecharts 기본은 6 · 12 · 24)
export const PANE_CALC_PARAMS: Partial<Record<PaneKey, number[]>> = { rsi: [14] };

export interface IndicatorDef {
  key: IndicatorKey;
  label: string;
  // 차트 선 색(이동평균에만)
  color?: string;
}

// 이동평균 기간과 선 색(차트 선 · 목록 점 · 글자 색을 이 값 하나로)
export const MA_LINES: { key: IndicatorKey; period: number; color: string }[] = [
  { key: 'ma5', period: 5, color: '#60a5fa' },
  { key: 'ma20', period: 20, color: '#f59e0b' },
  { key: 'ma60', period: 60, color: '#a855f7' },
  { key: 'ma120', period: 120, color: '#22c55e' },
  { key: 'ma200', period: 200, color: '#f97316' },
];

// 데스크톱 옆 패널과 휴대폰 시트가 같이 쓰는 지표 목록
export const INDICATOR_GROUPS: { title: string; items: IndicatorDef[] }[] = [
  {
    title: '이동평균선',
    items: MA_LINES.map(({ key, period, color }) => ({ key, label: `MA ${period}`, color })),
  },
  {
    title: '오버레이',
    items: [
      { key: 'ema', label: 'EMA 12·26' },
      { key: 'boll', label: '볼린저밴드' },
    ],
  },
  {
    title: '오실레이터',
    items: [
      { key: 'rsi', label: 'RSI (14)' },
      { key: 'macd', label: 'MACD' },
      { key: 'kdj', label: '스토캐스틱' },
    ],
  },
  {
    title: '거래량',
    items: [{ key: 'vol', label: '거래량 MA' }],
  },
];

export const DEFAULT_INDICATORS: IndicatorState = {
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
};
