import React, { useId, useState } from 'react';
import type { PortfolioTransaction, TradeReason } from '../../../../services/api';
import { gainLossClass } from '../../../../utils/gainLoss';
import { formatNumber, formatSigned, formatUsd } from '../../../../utils/format';

interface PortfolioTradesProps {
  transactions: PortfolioTransaction[];
  // 종목 고르기 순서(요청 순서)
  symbols: string[];
}

// 거래 이유 고르기 이름(이 순서로)
const REASON_LABELS: Record<TradeReason, string> = {
  BUY: '한 번에 매수',
  DCA_BUY: '적립식 매수',
  PERIOD_END_SALE: '기간 끝 매도',
  DIVIDEND_REINVESTMENT: '배당 재투자',
  TAX_PAYMENT: '양도세 납부',
  TAX_SALE: '양도세 납부용 매도',
  REBALANCE_BUY: '리밸런싱 매수',
  REBALANCE_SELL: '리밸런싱 매도',
  TAKE_PROFIT: '익절',
  STOP_LOSS: '손절',
  STRATEGY_BUY: '전략 매수',
  STRATEGY_SELL: '전략 매도',
};

const HEAD = 'px-3 py-2 text-xs font-medium text-tx-2';
const TEXT = 'px-3 py-2 text-left';
const NUMBER = 'px-3 py-2 text-right tabular-nums';
const SELECT = 'bg-surface border border-line rounded-btn px-3 py-2 text-sm text-tx-1';
const Dash: React.FC = () => <span className="text-tx-3">-</span>;

const isPayment = (tx: PortfolioTransaction) => tx.reason === 'TAX_PAYMENT';
const isSale = (tx: PortfolioTransaction) => tx.shares < 0;

// 금액(USD): 매도는 받은 돈, 양도세 납부는 낸 돈을 뺀 값, 매수는 넣은 원화를 그 날짜 환율로 바꾼 값 + 쓴 현금
const amountUsd = (tx: PortfolioTransaction) => {
  if (isPayment(tx)) return formatSigned(-(tx.taxPaid ?? 0), formatUsd);
  if (isSale(tx)) return formatUsd(tx.proceeds ?? 0);
  const deposit = tx.amount > 0 && tx.fxRate > 0 ? tx.amount / tx.fxRate : 0;
  return formatUsd(deposit + (tx.cashUsed ?? 0));
};

const TradeRow: React.FC<{ tx: PortfolioTransaction }> = ({ tx }) => {
  const tradedOn = tx.actualDate ?? tx.date;
  return (
    <tr>
      <td className={TEXT}>
        <div>{tradedOn}</div>
        {tradedOn !== tx.date && <div className="text-xs text-tx-3">계획 {tx.date}</div>}
      </td>
      <td className={`${TEXT} font-medium`}>{tx.symbol ?? <Dash />}</td>
      <td className={TEXT}>{tx.trigger ?? (tx.reason ? REASON_LABELS[tx.reason] : <Dash />)}</td>
      <td className={NUMBER}>
        {isPayment(tx) ? <Dash /> : `${formatNumber(Math.abs(tx.shares), { maximumFractionDigits: 6 })}주`}
      </td>
      <td className={NUMBER}>{amountUsd(tx)}</td>
      <td className={NUMBER}>
        {isSale(tx) && tx.realizedProfit != null ? (
          <span className={gainLossClass(tx.realizedProfit)}>{formatSigned(tx.realizedProfit, formatUsd)}</span>
        ) : (
          <Dash />
        )}
      </td>
    </tr>
  );
};

// 포트폴리오 매매 내역: 종목 · 이유 고르기와 거래 표(이유 칸은 거래 글, 금액 · 실현 손익은 달러)
export const PortfolioTrades: React.FC<PortfolioTradesProps> = ({ transactions, symbols }) => {
  const [symbol, setSymbol] = useState('');
  const [reason, setReason] = useState('');
  const symbolId = useId();
  const reasonId = useId();
  const symbolOptions = symbols.filter((s) => transactions.some((tx) => tx.symbol === s));
  const reasonOptions = (Object.keys(REASON_LABELS) as TradeReason[]).filter((r) => transactions.some((tx) => tx.reason === r));
  const shown = transactions.filter((tx) => (!symbol || tx.symbol === symbol) && (!reason || tx.reason === reason));

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-tx-2">
        <div className="flex items-center gap-2">
          <label htmlFor={symbolId}>종목</label>
          <select id={symbolId} value={symbol} onChange={(e) => setSymbol(e.target.value)} className={SELECT}>
            <option value="">모든 종목</option>
            {symbolOptions.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </div>
        <div className="flex items-center gap-2">
          <label htmlFor={reasonId}>이유</label>
          <select id={reasonId} value={reason} onChange={(e) => setReason(e.target.value)} className={SELECT}>
            <option value="">모든 이유</option>
            {reasonOptions.map((r) => (
              <option key={r} value={r}>
                {REASON_LABELS[r]}
              </option>
            ))}
          </select>
        </div>
        <span className="ml-auto">{shown.length}건</span>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-sm whitespace-nowrap">
          <thead>
            <tr className="border-b border-line">
              <th scope="col" className={`${HEAD} text-left`}>날짜</th>
              <th scope="col" className={`${HEAD} text-left`}>종목</th>
              <th scope="col" className={`${HEAD} text-left`}>이유</th>
              <th scope="col" className={`${HEAD} text-right`}>수량</th>
              <th scope="col" className={`${HEAD} text-right`}>금액</th>
              <th scope="col" className={`${HEAD} text-right`}>실현 손익</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line/50 text-tx-1">
            {shown.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-3 py-6 text-center text-tx-2">
                  고른 종목 · 이유의 거래가 없습니다
                </td>
              </tr>
            ) : (
              shown.map((tx, index) => <TradeRow key={index} tx={tx} />)
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};
