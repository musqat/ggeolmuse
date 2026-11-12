import React from 'react';
import type { PortfolioBacktestResponse, TradeReason } from '../../../../services/api';
import { gainLossClass } from '../../../../utils/gainLoss';
import { formatKrw, formatSigned } from '../../../../utils/format';
import { StatCard } from '../StatCard';

// 포트폴리오에서 판 것이 있을 때 카드 줄: 실현 손익(판 돈) · 낸 양도세(남은 양도세) · 매수 · 매도 수
export const PortfolioTradeCards: React.FC<{ result: PortfolioBacktestResponse }> = ({ result }) => {
  const tax = result.capitalGainsTax;
  const sold = result.assets.reduce((sum, asset) => sum + asset.soldKrw, 0);
  const count = (reason: TradeReason) => result.transactions.filter((tx) => tx.reason === reason).length;
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
        sub={others.length > 0 && <p>{others.join(' · ')}</p>}
      />
    </div>
  );
};
