import type { DailyValue } from '../../../services/api';
import type { MetricCurrency } from './CurrencyToggle';

// 고른 통화의 평가 가치와 고점 대비 낙폭(없으면 0)
export const riskChartData = (values: DailyValue[], currency: MetricCurrency) =>
  values.map((value) => ({
    date: value.date,
    value: currency === 'KRW' ? value.valueKrw : value.valueUsd,
    drawdown: (currency === 'KRW' ? value.drawdownKrw : value.drawdownUsd) ?? 0,
  }));
