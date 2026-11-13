import type { PerformanceMetrics, StrategyResponse } from '../../../services/api';
import { hasValue } from '../../../utils/gainLoss';
import { formatPercent } from '../../../utils/format';
import type { MetricCurrency } from './CurrencyToggle';

// 배당 재투자 거래의 trigger 값
export const DIVIDEND_REINVEST = '배당 재투자';

// 양도세를 낸 줄의 trigger 값
export const TAX_PAYMENT = '양도세 납부';

// 연말 정리 매도 · 재매수의 trigger 값
export const YEAR_END_HARVEST = ['공제 채우기 매도', '공제 채우기 재매수', '손실 상계 매도', '손실 상계 재매수'];

// 매도 거래(수량이 음수)
export const isSale = (tx: { shares: number }) => tx.shares < 0;

// 연말 정리 매도 · 재매수
export const isYearEndHarvest = (tx: { trigger?: string }) => tx.trigger !== undefined && YEAR_END_HARVEST.includes(tx.trigger);

// 배당 재투자 · 매도 · 양도세 납부 · 연말 정리를 뺀 매수 거래
export const isPurchase = (tx: { trigger?: string; shares: number }) =>
  tx.trigger !== DIVIDEND_REINVEST && tx.trigger !== TAX_PAYMENT && !isYearEndHarvest(tx) && !isSale(tx);

export const purchaseCount = (transactions: { trigger?: string; shares: number }[]) => transactions.filter(isPurchase).length;

// 연말 정리를 뺀 매도 횟수
export const sellCount = (transactions: { trigger?: string; shares: number }[]) =>
  transactions.filter((tx) => isSale(tx) && !isYearEndHarvest(tx)).length;

// 고른 통화의 지표와 그 반대 통화 지표 · 이름
export const pickMetrics = (result: Pick<StrategyResponse, 'metricsKrw' | 'metricsUsd'>, currency: MetricCurrency) => {
  const krw = currency === 'KRW';
  return {
    metrics: krw ? result.metricsKrw : result.metricsUsd,
    other: krw ? result.metricsUsd : result.metricsKrw,
    otherLabel: krw ? '달러' : '원화',
    hasMetrics: result.metricsKrw != null || result.metricsUsd != null,
  };
};

export const percentOrDash = (value: number | null | undefined) => (hasValue(value) ? formatPercent(value) : '—');
export const ratioOrDash = (value: number | null | undefined) => (hasValue(value) ? value.toFixed(2) : '—');

// 상세 칸 제목 옆 요약(변동성 · 샤프 · 환차익)
export const detailMeta = (metrics: PerformanceMetrics | null | undefined, fxReturnText: string) =>
  `변동성 ${percentOrDash(metrics?.volatility)} · 샤프 ${ratioOrDash(metrics?.sharpe)} · 환차익 ${fxReturnText}`;

// 0 위 · 아래로 색이 갈리는 지점(위쪽 비율)
export const zeroOffset = (min: number, max: number) => {
  if (max <= 0) return 0;
  if (min >= 0) return 1;
  return max / (max - min);
};
