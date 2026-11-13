import React, { useMemo, useState } from 'react';
import type { PortfolioBacktestResponse } from '../../../../services/api';
import { gainLossClass, hasValue } from '../../../../utils/gainLoss';
import { formatKrw, formatPercent, formatSigned } from '../../../../utils/format';
import { StatCard } from '../StatCard';
import { AssetChart } from '../AssetChart';
import { CurrencyToggle, type MetricCurrency } from '../CurrencyToggle';
import { DefaultFxNotice } from '../DefaultFxNotice';
import { strategyDeposits } from '../assetSeries';
import { percentOrDash, pickMetrics } from '../resultValues';
import { ResultFold } from '../ResultFold';
import { CapitalGainsTaxFold } from '../CapitalGainsTaxFold';
import { PortfolioAssetTable } from './PortfolioAssetTable';
import { PortfolioTradeCards } from './PortfolioTradeCards';
import { PortfolioTrades } from './PortfolioTrades';

interface PortfolioResultProps {
  result: PortfolioBacktestResponse;
}

// 포트폴리오 결과: 지표 카드 넷(총 평가 · 총 수익률 · 연평균 · 최대 낙폭) · 합친 자산 추이 · 종목별 표, 판 것(연말 정리 포함)이 있으면 매매 카드 줄 · 매매 내역 · 예상 양도세
export const PortfolioResult: React.FC<PortfolioResultProps> = ({ result }) => {
  const [currency, setCurrency] = useState<MetricCurrency>('KRW');
  const { metrics, other, otherLabel, hasMetrics } = pickMetrics(result, currency);
  const deposits = useMemo(() => strategyDeposits(result.transactions ?? []), [result.transactions]);
  const cagr = metrics?.cagr;
  const mdd = metrics?.mdd;
  const sold = (result.transactions ?? []).some((tx) => tx.shares < 0);
  const symbols = useMemo(() => result.assets.map((asset) => asset.symbol), [result.assets]);

  return (
    <div className="space-y-6">
      <DefaultFxNotice dates={result.defaultFxDates} />
      <div className="space-y-4">
        {hasMetrics && (
          <div className="flex items-center justify-end gap-2">
            <span className="text-sm text-tx-2">지표 기준</span>
            <CurrencyToggle value={currency} onChange={setCurrency} />
          </div>
        )}
        <div className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
          <StatCard
            compact
            label="총 평가"
            value={formatKrw(result.totalAssetKrw)}
            sub={<p>넣은 돈 {formatKrw(result.totalInvestedKrw)}</p>}
          />
          <StatCard
            compact
            label="총 수익률"
            value={formatSigned(result.totalReturnPercent, formatPercent)}
            valueClassName={gainLossClass(result.totalReturnPercent) || 'text-tx-1'}
            sub={<p>{formatSigned(result.totalReturnKrw, formatKrw)}</p>}
            testId="backtest-return-rate"
          />
          <StatCard
            compact
            label="연평균 수익률"
            value={hasValue(cagr) ? formatSigned(cagr, formatPercent) : '—'}
            valueClassName={hasValue(cagr) ? gainLossClass(cagr) || 'text-tx-1' : 'text-tx-3'}
            sub={
              <p>
                {!hasMetrics
                  ? '지표 없음'
                  : hasValue(cagr)
                    ? `시간가중 · ${otherLabel} 기준 ${formatSigned(other?.cagr, formatPercent)}`
                    : '1년 미만이면 내지 않음'}
              </p>
            }
          />
          <StatCard
            compact
            label="최대 낙폭"
            value={percentOrDash(mdd)}
            valueClassName={hasValue(mdd) ? (mdd < 0 ? 'text-loss' : 'text-tx-1') : 'text-tx-3'}
            sub={hasMetrics && <p>{`${otherLabel} 기준 ${percentOrDash(other?.mdd)}`}</p>}
          />
        </div>
        <AssetChart dailyValues={result.dailyValues} deposits={deposits} currency={hasMetrics ? currency : 'KRW'} />
      </div>
      <PortfolioAssetTable result={result} />
      {sold && (
        <>
          <PortfolioTradeCards result={result} />
          <ResultFold title="매매 내역" meta={`거래 ${result.transactions.length}건`}>
            <PortfolioTrades transactions={result.transactions} symbols={symbols} />
          </ResultFold>
          {result.capitalGainsTax && <CapitalGainsTaxFold tax={result.capitalGainsTax} returnBasis="넣은 돈 기준" sellsForTax />}
        </>
      )}
    </div>
  );
};
