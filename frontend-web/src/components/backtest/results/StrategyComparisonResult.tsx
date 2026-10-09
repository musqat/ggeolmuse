import React from 'react';
import { CompareStrategiesChart } from '@components/charts/backtest/CompareStrategiesChart';
import type { ComparisonItem, ComparisonResponse } from '../../../services/api';
import { STRATEGY_NAMES } from '../shared/backtestDisplay';
import { formatKrw, formatPercent, formatSigned, gainLossClass } from '../../../utils/gainLoss';

interface StrategyComparisonResultProps {
  result: ComparisonResponse;
}

export const StrategyComparisonResult: React.FC<StrategyComparisonResultProps> = ({ result }) => (
  <div className="space-y-6">
    <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-6">
      <h3 className="text-lg font-semibold text-tx-1 mb-4">
        전략 비교 결과
      </h3>

      {/* Best Performer Highlight */}
      {result.bestPerformer && (
        <div className="mb-6 p-4 bg-warning-soft/10 border border-warning-soft/40 rounded-lg">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-tx-2">
                최고 성과
              </p>
              <p className="text-xl font-bold text-warning mt-1">
                {STRATEGY_NAMES[result.bestPerformer.name] ||
                  result.bestPerformer.name}
              </p>
            </div>
            <div className="text-right">
              <p className="text-sm text-tx-2">수익률</p>
              <p className="text-2xl font-bold text-warning">
                {formatSigned(result.bestPerformer.totalReturnPercent, formatPercent)}
              </p>
            </div>
          </div>
        </div>
      )}

      {/* 비교 Table */}
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b border-line">
              <th className="text-left py-3 px-4 font-semibold text-tx-1">
                전략
              </th>
              <th className="text-right py-3 px-4 font-semibold text-tx-1">
                투자금
              </th>
              <th className="text-right py-3 px-4 font-semibold text-tx-1">
                최종 가치
              </th>
              <th className="text-right py-3 px-4 font-semibold text-tx-1">
                총 수익
              </th>
              <th className="text-right py-3 px-4 font-semibold text-tx-1">
                수익률
              </th>
            </tr>
          </thead>
          <tbody>
            {result.items?.map((item: ComparisonItem, index: number) => {
              const isBest = item.name === result.bestPerformer?.name;
              return (
                <tr
                  key={index}
                  className={`border-b border-line/50 ${
                    isBest ? "bg-yellow-500/10" : "hover:bg-surface/50"
                  }`}
                >
                  <td className="py-3 px-4 font-medium text-tx-1">
                    {STRATEGY_NAMES[item.name] || item.name}
                  </td>
                  <td className="py-3 px-4 text-right text-tx-1">
                    ₩{item.totalInvested?.toLocaleString()}
                  </td>
                  <td className="py-3 px-4 text-right text-tx-1 font-medium">
                    ₩{item.currentValueKrw?.toLocaleString()}
                  </td>
                  <td
                    className={`py-3 px-4 text-right font-medium ${gainLossClass(item.totalReturnKrw)}`}
                  >
                    {formatSigned(item.totalReturnKrw, formatKrw)}
                  </td>
                  <td
                    className={`py-3 px-4 text-right font-bold ${gainLossClass(item.totalReturnPercent)}`}
                  >
                    {formatSigned(item.totalReturnPercent, formatPercent)}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>

    {/* Strategy Comparison Charts */}
    <CompareStrategiesChart
      strategies={result.items || []}
      strategyNames={STRATEGY_NAMES}
    />
  </div>
);
