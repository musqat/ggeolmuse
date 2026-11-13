import React from 'react';
import type { PortfolioAssetResult, PortfolioBacktestResponse, PortfolioRebalance } from '../../../../services/api';
import { gainLossClass } from '../../../../utils/gainLoss';
import { formatFxRate, formatKrw, formatPercent, formatSigned } from '../../../../utils/format';
import { STRATEGY_LABELS } from '../../shared/portfolioRows';

const HEAD = 'px-3 py-2 text-xs font-medium text-tx-2';
const NAME = 'px-3 py-2 text-left font-medium text-tx-1';
const NUMBER = 'px-3 py-2 text-right tabular-nums';
const TEXT = 'px-3 py-2 text-left';
const EMPTY = 'text-tx-3';

const percentOrDash = (value: number | null) => (value == null ? '-' : formatPercent(value));

const REBALANCE_LABELS: Record<PortfolioRebalance, string> = {
  NONE: '리밸런싱 없음',
  YEARLY: '매년 리밸런싱',
  QUARTERLY: '분기마다 리밸런싱',
  MONTHLY: '매달 리밸런싱',
};

// 지금 가치 아래 작은 줄(판 돈 · 배당, 0 이면 뺌)
const besides = (asset: PortfolioAssetResult) =>
  [asset.soldKrw > 0 && `판 돈 ${formatKrw(asset.soldKrw)}`, asset.dividendKrw > 0 && `배당 ${formatKrw(asset.dividendKrw)}`]
    .filter(Boolean)
    .join(' · ');

const Dash: React.FC = () => <span className={EMPTY}>-</span>;

const isSignal = (asset: PortfolioAssetResult) => asset.strategyType === 'MA_CROSS' || asset.strategyType === 'MA_BAND';

const AssetRow: React.FC<{ asset: PortfolioAssetResult }> = ({ asset }) => {
  const extra = besides(asset);
  return (
    <tr>
      <th scope="row" className={NAME}>
        {asset.symbol}
      </th>
      <td className={TEXT}>
        <div>{STRATEGY_LABELS[asset.strategyType]}</div>
        {isSignal(asset) && asset.buyCount === 0 && <div className="text-xs text-tx-2">매수 신호 없음</div>}
      </td>
      <td className={NUMBER}>{formatKrw(asset.investedKrw)}</td>
      <td className={NUMBER}>
        <div>{formatKrw(asset.valueKrw)}</div>
        {extra && <div className="text-xs text-tx-2">{extra}</div>}
      </td>
      <td className={NUMBER}>
        {asset.returnPercent == null ? (
          <Dash />
        ) : (
          <span className={gainLossClass(asset.returnPercent)}>{formatSigned(asset.returnPercent, formatPercent)}</span>
        )}
      </td>
      <td className={NUMBER}>
        <span className={gainLossClass(asset.profitKrw)}>{formatSigned(asset.profitKrw, formatKrw)}</span>
        <span className="text-tx-2"> · {percentOrDash(asset.contributionPercent)}</span>
      </td>
      <td className={`${NUMBER} text-tx-1`}>
        {formatPercent(asset.startWeightPercent)} → {formatPercent(asset.endWeightPercent)}
      </td>
    </tr>
  );
};

// 포트폴리오 종목별 표: 제목 줄 오른쪽 리밸런싱 주기 · 평가일 환율, 줄마다 전략(이동평균 · 밴드 줄이 사지 않았으면 매수 신호 없음) · 원금 · 지금 가치 · 수익률 · 기여(이익 · 전체 이익 중 몫) · 비중 처음 → 지금, 맨 아래 현금 줄과 종목 밖 이익(0 이 아닐 때) 줄
export const PortfolioAssetTable: React.FC<{ result: PortfolioBacktestResponse }> = ({ result }) => {
  const cashWeight = result.totalAssetKrw > 0 ? Math.round((result.cashKrw / result.totalAssetKrw) * 10000) / 100 : 0;
  return (
    <section className="bg-surface rounded-xl shadow-sm border border-line/50 p-4 sm:p-5">
      <div className="mb-3 flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
        <h3 className="text-lg font-semibold text-tx-1">종목별</h3>
        <span className="text-sm text-tx-2">
          {REBALANCE_LABELS[result.rebalance]} · 평가일 환율 {formatFxRate(result.currentFxRate)}
        </span>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-sm whitespace-nowrap">
          <thead>
            <tr className="border-b border-line">
              <th scope="col" className={`${HEAD} text-left`}>종목</th>
              <th scope="col" className={`${HEAD} text-left`}>전략</th>
              <th scope="col" className={`${HEAD} text-right`}>원금</th>
              <th scope="col" className={`${HEAD} text-right`}>지금 가치</th>
              <th scope="col" className={`${HEAD} text-right`}>수익률</th>
              <th scope="col" className={`${HEAD} text-right`}>기여</th>
              <th scope="col" className={`${HEAD} text-right`}>비중 처음 → 지금</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line/50 text-tx-1">
            {result.assets.map((asset) => (
              <AssetRow key={asset.symbol} asset={asset} />
            ))}
            <tr>
              <th scope="row" className={NAME}>
                현금
              </th>
              <td className={TEXT}>
                <Dash />
              </td>
              <td className={NUMBER}>
                <Dash />
              </td>
              <td className={NUMBER}>{formatKrw(result.cashKrw)}</td>
              <td className={NUMBER}>
                <Dash />
              </td>
              <td className={NUMBER}>
                <Dash />
              </td>
              <td className={NUMBER}>
                <span className={EMPTY}>-</span> → {formatPercent(cashWeight)}
              </td>
            </tr>
            {result.otherProfitKrw !== 0 && (
              <tr>
                <th scope="row" className={`${NAME} font-normal text-tx-2`}>
                  현금 환율 차 · 양도세
                </th>
                <td className={TEXT}>
                  <Dash />
                </td>
                <td className={NUMBER}>
                  <Dash />
                </td>
                <td className={NUMBER}>
                  <Dash />
                </td>
                <td className={NUMBER}>
                  <Dash />
                </td>
                <td className={NUMBER}>
                  <span className={gainLossClass(result.otherProfitKrw)}>{formatSigned(result.otherProfitKrw, formatKrw)}</span>
                </td>
                <td className={NUMBER}>
                  <Dash />
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
};
