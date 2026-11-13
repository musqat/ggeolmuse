import React from 'react';
import type { StrategyTransaction } from '../../../services/api';
import { gainLossClass } from '../../../utils/gainLoss';
import { formatNumber, formatSigned, formatUsd } from '../../../utils/format';
import { isSale } from './resultValues';

const HEAD = 'px-3 py-2 text-xs font-medium text-tx-2';
const TEXT = 'px-3 py-2 text-left whitespace-nowrap';
const NUMBER = 'px-3 py-2 text-right tabular-nums whitespace-nowrap';
const Dash: React.FC = () => <span className="text-tx-3">-</span>;

// 단순 결과의 연말 정리 매도 · 재매수와 기간 끝 매도 표(카드 안에서만 가로 스크롤)
export const HarvestTrades: React.FC<{ transactions: StrategyTransaction[] }> = ({ transactions }) => (
  <div className="overflow-x-auto">
    <table className="w-full text-sm text-tx-1">
      <thead>
        <tr className="border-b border-line/50">
          <th scope="col" className={`${HEAD} text-left`}>날짜</th>
          <th scope="col" className={`${HEAD} text-left`}>이유</th>
          <th scope="col" className={`${HEAD} text-right`}>수량</th>
          <th scope="col" className={`${HEAD} text-right`}>가격</th>
          <th scope="col" className={`${HEAD} text-right`}>금액</th>
          <th scope="col" className={`${HEAD} text-right`}>실현 손익</th>
        </tr>
      </thead>
      <tbody>
        {transactions.map((tx, index) => (
          <tr key={`${tx.date}-${index}`} className="border-b border-line/50 last:border-0">
            <td className={TEXT}>{tx.actualDate ?? tx.date}</td>
            <td className={TEXT}>{tx.trigger}</td>
            <td className={NUMBER}>{`${formatNumber(Math.abs(tx.shares), { maximumFractionDigits: 6 })}주`}</td>
            <td className={NUMBER}>{formatUsd(tx.price)}</td>
            <td className={NUMBER}>{formatUsd((isSale(tx) ? tx.proceeds : tx.cashUsed) ?? 0)}</td>
            <td className={NUMBER}>
              {isSale(tx) && tx.realizedProfit != null ? (
                <span className={gainLossClass(tx.realizedProfit)}>{formatSigned(tx.realizedProfit, formatUsd)}</span>
              ) : (
                <Dash />
              )}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  </div>
);
