import React from 'react';
import type { StrategyTransaction } from '../../../services/api';
import { gainLossClass } from '../../../utils/gainLoss';
import { formatShares, formatSigned, formatUsd } from '../../../utils/format';
import { isSale } from './resultValues';
import { ROW_CARD_BODY, ROW_CARD_CELL, ROW_CARD_HEAD, ROW_CARD_ROW, ROW_CARD_TABLE, ROW_CARD_TITLE } from '../../common/rowCards';

const HEAD = 'px-3 py-2 text-xs font-medium text-tx-2';
const TITLE = `${ROW_CARD_TITLE} font-medium sm:px-3 sm:py-2 sm:font-normal`;
const TEXT = `${ROW_CARD_CELL} sm:px-3 sm:py-2 sm:text-left`;
const NUMBER = `${ROW_CARD_CELL} sm:px-3 sm:py-2 tabular-nums`;
const Dash: React.FC = () => <span className="text-tx-3">-</span>;

// 단순 결과의 연말 정리 매도 · 재매수와 기간 끝 매도 표(휴대폰 너비는 줄마다 카드, 넓은 화면은 카드 안에서만 가로 스크롤)
export const HarvestTrades: React.FC<{ transactions: StrategyTransaction[] }> = ({ transactions }) => (
  <div className="overflow-x-auto">
    <table className={`${ROW_CARD_TABLE} text-sm text-tx-1 sm:whitespace-nowrap`}>
      <thead className={ROW_CARD_HEAD}>
        <tr className="border-b border-line/50">
          <th scope="col" className={`${HEAD} text-left`}>날짜</th>
          <th scope="col" className={`${HEAD} text-left`}>이유</th>
          <th scope="col" className={`${HEAD} text-right`}>수량</th>
          <th scope="col" className={`${HEAD} text-right`}>가격</th>
          <th scope="col" className={`${HEAD} text-right`}>금액</th>
          <th scope="col" className={`${HEAD} text-right`}>실현 손익</th>
        </tr>
      </thead>
      <tbody className={`${ROW_CARD_BODY} sm:divide-y sm:divide-line/50`}>
        {transactions.map((tx, index) => (
          <tr key={`${tx.date}-${index}`} className={ROW_CARD_ROW}>
            <td className={TITLE}>{tx.actualDate ?? tx.date}</td>
            <td className={TEXT} data-label="이유">{tx.trigger}</td>
            <td className={NUMBER} data-label="수량">{formatShares(Math.abs(tx.shares))}</td>
            <td className={NUMBER} data-label="가격">{formatUsd(tx.price)}</td>
            <td className={NUMBER} data-label="금액">{formatUsd((isSale(tx) ? tx.proceeds : tx.cashUsed) ?? 0)}</td>
            <td className={NUMBER} data-label="실현 손익">
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
