import React from 'react';
import type { PortfolioBacktestResponse, TradeReason } from '../../../../services/api';
import { gainLossClass } from '../../../../utils/gainLoss';
import { formatKrw, formatSigned } from '../../../../utils/format';
import { StatCard } from '../StatCard';

// 매도 이유와 카드 아래 줄의 짧은 이름(보이는 차례)
const SELL_REASONS: [TradeReason, string][] = [
  ['STRATEGY_SELL', '전략'],
  ['TAKE_PROFIT', '익절'],
  ['STOP_LOSS', '손절'],
  ['REBALANCE_SELL', '리밸런싱'],
  ['PERIOD_END_SALE', '기간 끝'],
  ['TAX_SALE', '납부용'],
];

// 포트폴리오에서 판 것이 있을 때 카드 줄: 실현 손익(판 돈) · 낸 양도세(남은 양도세) · 매수 · 매도 수(아래 이유별 매도 수 · 배당 재투자 · 양도세 납부)
export const PortfolioTradeCards: React.FC<{ result: PortfolioBacktestResponse }> = ({ result }) => {
  const tax = result.capitalGainsTax;
  const sold = result.assets.reduce((sum, asset) => sum + asset.soldKrw, 0);
  const count = (reason: TradeReason) => result.transactions.filter((tx) => tx.reason === reason).length;
  const sells = SELL_REASONS.filter(([reason]) => count(reason) > 0).map(([reason, name]) => `${name} ${count(reason)}번`);
  const others = [
    count('DIVIDEND_REINVESTMENT') > 0 && `배당 재투자 ${count('DIVIDEND_REINVESTMENT')}번`,
    count('TAX_PAYMENT') > 0 && `양도세 납부 ${count('TAX_PAYMENT')}번`,
  ].filter(Boolean);

  return (
    <div className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-3">
      <StatCard
        compact
        className="col-span-2 lg:col-span-1"
        label="실현 손익"
        value={formatSigned(result.realizedProfitKrw, formatKrw)}
        valueClassName={gainLossClass(result.realizedProfitKrw) || 'text-tx-1'}
        sub={<p>판 돈 {formatKrw(sold)}</p>}
      />
      <StatCard
        compact
        label="낸 양도세"
        value={tax ? formatKrw(tax.paidTaxKrw) : '-'}
        valueClassName={tax ? 'text-tx-1' : 'text-tx-3'}
        sub={<p>{tax ? `남은 양도세 ${formatKrw(tax.unpaidTaxKrw)}` : '양도세 계산 끔'}</p>}
      />
      <StatCard
        compact
        label="매수 · 매도"
        value={`${result.buyCount}번 · ${result.sellCount}번`}
        sub={
          (sells.length > 0 || others.length > 0) && (
            <>
              {sells.length > 0 && <p>{sells.join(' · ')}</p>}
              {others.length > 0 && <p>{others.join(' · ')}</p>}
            </>
          )
        }
      />
    </div>
  );
};
