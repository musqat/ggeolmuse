import React from 'react';
import { ArrowUpCircle, ArrowDownCircle } from 'lucide-react';
import type { HoldingResponse } from '../../services/api';

const COLUMNS = ['종목', '보유량', '평균단가', '현재가', '평가금액', '손익'];

// 보유 종목 표
export const HoldingsTable: React.FC<{ holdings: HoldingResponse[] }> = ({ holdings }) => (
  <div className="bg-surface rounded-xl shadow-sm border border-line/50 overflow-hidden">
    <div className="px-6 py-4 border-b border-line">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold text-tx-1">보유 종목</h3>
        <p className="text-xs text-tx-2">* 수수료가 포함되지 않은 수익률입니다</p>
      </div>
    </div>

    <div className="overflow-x-auto">
      <table className="w-full">
        <thead className="bg-surface/50">
          <tr>
            {COLUMNS.map((column) => (
              <th key={column} className="px-6 py-3 text-left text-xs font-medium text-tx-2 uppercase tracking-wider">
                {column}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="bg-surface divide-y divide-line">
          {holdings.length > 0 ? (
            holdings.map((holding) => (
              <tr key={holding.holdingId} className="hover:bg-surface/50">
                <td className="px-6 py-4 whitespace-nowrap">
                  <div>
                    <div className="text-sm font-medium text-tx-1">{holding.symbol}</div>
                  </div>
                </td>
                <td className="px-6 py-4 whitespace-nowrap">
                  <div className="text-sm text-tx-1">{holding.totalQuantity}주</div>
                </td>
                <td className="px-6 py-4 whitespace-nowrap">
                  <div className="text-sm text-tx-1">${holding.avgPurchasePrice.toFixed(2)}</div>
                </td>
                <td className="px-6 py-4 whitespace-nowrap">
                  <div className="text-sm text-tx-1">
                    {holding.currentPrice ? `$${holding.currentPrice.toFixed(2)}` : '-'}
                  </div>
                </td>
                <td className="px-6 py-4 whitespace-nowrap">
                  <div className="text-sm text-tx-1">
                    {holding.currentValue ? `$${holding.currentValue.toFixed(2)}` : '-'}
                  </div>
                </td>
                <td className="px-6 py-4 whitespace-nowrap">
                  {holding.unrealizedPnL !== null && holding.unrealizedPnL !== undefined &&
                   holding.returnRate !== null && holding.returnRate !== undefined ? (
                    <div className={`text-sm font-medium flex items-center ${
                      holding.unrealizedPnL >= 0 ? 'text-gain' : 'text-loss'
                    }`}>
                      {holding.unrealizedPnL >= 0 ? (
                        <ArrowUpCircle className="w-4 h-4 mr-1" />
                      ) : (
                        <ArrowDownCircle className="w-4 h-4 mr-1" />
                      )}
                      {holding.unrealizedPnL >= 0 ? '+' : ''}${holding.unrealizedPnL.toFixed(2)}
                      <span className="ml-1">({holding.returnRate >= 0 ? '+' : ''}{holding.returnRate.toFixed(2)}%)</span>
                    </div>
                  ) : (
                    <div className="text-sm text-tx-3">-</div>
                  )}
                </td>
              </tr>
            ))
          ) : (
            <tr>
              <td colSpan={COLUMNS.length} className="px-6 py-8 text-center text-tx-2">
                <p>보유 종목이 없습니다</p>
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  </div>
);
