import React from 'react';
import { ArrowUpCircle, ArrowDownCircle } from 'lucide-react';
import type { HoldingResponse } from '../../services/api';
import { formatPercent, formatShares, formatSigned, formatUsd } from '../../utils/format';
import { gainLossTone } from '../../utils/gainLoss';
import {
  ROW_CARD_BODY,
  ROW_CARD_CELL,
  ROW_CARD_HEAD,
  ROW_CARD_PLAIN_CELL,
  ROW_CARD_PLAIN_ROW,
  ROW_CARD_ROW,
  ROW_CARD_TABLE,
  ROW_CARD_TITLE,
} from '../common/rowCards';

const COLUMNS = ['종목', '보유량', '평균단가', '현재가', '평가금액', '손익'];

const TITLE = `${ROW_CARD_TITLE} sm:px-6 sm:py-4 sm:whitespace-nowrap`;
const CELL = `${ROW_CARD_CELL} text-sm text-tx-1 sm:px-6 sm:py-4 sm:text-left sm:whitespace-nowrap`;

// 보유 종목 표(휴대폰 너비는 줄마다 카드)
export const HoldingsTable: React.FC<{ holdings: HoldingResponse[] }> = ({ holdings }) => (
  <div className="bg-surface rounded-xl shadow-sm border border-line/50 overflow-hidden">
    <div className="px-6 py-4 border-b border-line">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold text-tx-1">보유 종목</h3>
        <p className="text-xs text-tx-2">* 수수료가 포함되지 않은 수익률입니다</p>
      </div>
    </div>

    <div className="overflow-x-auto">
      <table className={ROW_CARD_TABLE}>
        <thead className={`${ROW_CARD_HEAD} bg-surface/50`}>
          <tr>
            {COLUMNS.map((column) => (
              <th key={column} className="px-6 py-3 text-left text-xs font-medium text-tx-2 uppercase tracking-wider">
                {column}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className={`${ROW_CARD_BODY} bg-surface p-3 sm:p-0 sm:divide-y sm:divide-line`}>
          {holdings.length > 0 ? (
            holdings.map((holding) => (
              <tr key={holding.holdingId} className={`${ROW_CARD_ROW} hover:bg-surface/50`}>
                <td className={TITLE}>
                  <div className="text-sm font-medium text-tx-1">{holding.symbol}</div>
                </td>
                <td className={CELL} data-label="보유량">
                  {formatShares(holding.totalQuantity)}
                </td>
                <td className={CELL} data-label="평균단가">
                  {formatUsd(holding.avgPurchasePrice)}
                </td>
                <td className={CELL} data-label="현재가">
                  {holding.currentPrice ? formatUsd(holding.currentPrice) : '-'}
                </td>
                <td className={CELL} data-label="평가금액">
                  {holding.currentValue ? formatUsd(holding.currentValue) : '-'}
                </td>
                <td className={CELL} data-label="손익">
                  {holding.unrealizedPnL !== null && holding.unrealizedPnL !== undefined &&
                   holding.returnRate !== null && holding.returnRate !== undefined ? (
                    <div className={`font-medium flex items-center justify-end sm:justify-start ${
                      gainLossTone(holding.unrealizedPnL) === 'loss' ? 'text-loss' : 'text-gain'
                    }`}>
                      {gainLossTone(holding.unrealizedPnL) === 'loss' ? (
                        <ArrowDownCircle className="w-4 h-4 mr-1" />
                      ) : (
                        <ArrowUpCircle className="w-4 h-4 mr-1" />
                      )}
                      {formatSigned(holding.unrealizedPnL, formatUsd)}
                      <span className="ml-1">({formatSigned(holding.returnRate, formatPercent)})</span>
                    </div>
                  ) : (
                    <span className="text-tx-3">-</span>
                  )}
                </td>
              </tr>
            ))
          ) : (
            <tr className={ROW_CARD_PLAIN_ROW}>
              <td colSpan={COLUMNS.length} className={`${ROW_CARD_PLAIN_CELL} px-6 py-8 text-center text-tx-2`}>
                <p>보유 종목이 없습니다</p>
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  </div>
);
