import React, { useState } from 'react';
import type { StrategyResponse } from '../../../services/api';
import { CurrencyToggle, type MetricCurrency } from './CurrencyToggle';
import { RiskCards } from './RiskCards';
import { RiskChart } from './RiskChart';
import { SummaryCards } from './SummaryCards';

type OverviewValues = Pick<
  StrategyResponse,
  | 'totalAssetKrw'
  | 'currentValueKrw'
  | 'totalReturnKrw'
  | 'totalReturnPercent'
  | 'metricsKrw'
  | 'metricsUsd'
  | 'dailyValues'
>;

interface ResultOverviewProps {
  result: OverviewValues;
  investedLabel: string;
  invested: number | null | undefined;
  returnRateTestId?: string;
  // 입금을 뺀 시간가중 지표(적립식 · 조건부)
  timeWeighted?: boolean;
}

// 결과 위쪽: 지표 기준 통화 · 수익 줄 · 위험 줄 · 자산 추이와 낙폭 차트
export const ResultOverview: React.FC<ResultOverviewProps> = ({
  result,
  investedLabel,
  invested,
  returnRateTestId,
  timeWeighted,
}) => {
  const [currency, setCurrency] = useState<MetricCurrency>('KRW');
  const krw = currency === 'KRW';
  const metrics = krw ? result.metricsKrw : result.metricsUsd;
  const other = krw ? result.metricsUsd : result.metricsKrw;
  const otherLabel = krw ? '달러' : '원화';
  const hasMetrics = result.metricsKrw != null || result.metricsUsd != null;

  return (
    <div className="space-y-4">
      {hasMetrics && (
        <div className="flex items-center justify-end gap-2">
          <span className="text-sm text-tx-2">지표 기준</span>
          <CurrencyToggle value={currency} onChange={setCurrency} />
        </div>
      )}
      <SummaryCards
        result={result}
        investedLabel={investedLabel}
        invested={invested}
        metrics={metrics}
        other={other}
        otherLabel={otherLabel}
        noMetrics={!hasMetrics}
        totalInKrw={!krw}
        timeWeighted={timeWeighted}
        returnRateTestId={returnRateTestId}
      />
      {hasMetrics && (
        <>
          <RiskCards metrics={metrics} other={other} otherLabel={otherLabel} />
          <RiskChart
            dailyValues={result.dailyValues}
            metrics={metrics}
            currency={currency}
            markers={timeWeighted ? 'drawdown' : 'value'}
            subtitle={timeWeighted ? '낙폭 · 지표는 매수 입금을 뺀 수익률로 셉니다' : undefined}
          />
        </>
      )}
    </div>
  );
};
