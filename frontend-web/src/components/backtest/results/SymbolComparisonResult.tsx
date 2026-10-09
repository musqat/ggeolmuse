import React from 'react';
import { TrendingUp, DollarSign, Zap, Activity } from 'lucide-react';
import {
  formatKrw,
  formatPercent,
  formatSigned,
  gainLossClass,
  gainLossTone,
  type GainLossTone,
} from '../../../utils/gainLoss';
import type { ComparisonItem, ComparisonResponse } from '../../../services/api';
import type { OptimalPointsBySymbol } from '../shared/backtestDisplay';
import { CompareSymbolsChartMemoized } from './CompareSymbolsChartMemoized';
import { getTodayString } from '../../../utils/dateUtils';

// 종목 카드의 수익 칸 배경. 값이 없으면 중립 배경
const SOFT_BOX: Record<GainLossTone, string> = {
  gain: "bg-gain/10",
  loss: "bg-loss/10",
  none: "bg-brand-bg",
};

interface SymbolComparisonResultProps {
  result: ComparisonResponse;
  comparePurchaseDate: string;
  compareSaleDate: string;
  symbolOptimalPoints: OptimalPointsBySymbol;
  setSymbolOptimalPoints: React.Dispatch<React.SetStateAction<OptimalPointsBySymbol>>;
}

export const SymbolComparisonResult: React.FC<SymbolComparisonResultProps> = ({
  result,
  comparePurchaseDate,
  compareSaleDate,
  symbolOptimalPoints,
  setSymbolOptimalPoints,
}) => {
  return (
    <div className="space-y-6">
      {/* Best Performer Summary */}
      {result.bestPerformer && (
        <div className="bg-warning-soft/10 rounded-xl shadow-sm border border-warning-soft/40 p-6">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-tx-2">
                최고 성과 종목
              </p>
              <p className="text-3xl font-bold text-warning mt-1">
                {result.bestPerformer.name}
              </p>
            </div>
            <div className="text-right">
              <p className="text-sm text-tx-2">수익률</p>
              <p className="text-4xl font-bold text-warning">
                {formatSigned(result.bestPerformer.totalReturnPercent, formatPercent)}
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Each Symbol Results */}
      {result.items?.map((item: ComparisonItem, index: number) => {
        const isBest = item.name === result.bestPerformer?.name;

        // additionalData가 있으면 추출
        const additionalData = item.additionalData || {};
        const displayItem = {
          ...item,
          purchaseDate: additionalData.purchaseDate || item.purchaseDate,
          purchasePrice:
            additionalData.purchasePrice ||
            item.purchasePrice ||
            item.averagePrice,
          currentPrice: additionalData.currentPrice || item.currentPrice,
          shares: additionalData.shares || item.shares || item.totalShares,
          investmentAmount:
            additionalData.investmentAmount ||
            item.investmentAmount ||
            item.totalInvested,
          currentDate: additionalData.currentDate || item.currentDate,
          purchaseFxRate:
            additionalData.purchaseFxRate || item.purchaseFxRate,
          currentFxRate: additionalData.currentFxRate || item.currentFxRate,
        };

        return (
          <div
            key={index}
            className={`${isBest ? "ring-2 ring-yellow-400" : ""}`}
          >
            <div className="bg-surface rounded-xl shadow-sm border border-line/50 p-4 mb-3">
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-xl font-bold text-tx-1">
                  {displayItem.name || displayItem.symbol}
                </h3>
                <p className="text-sm text-tx-2">
                  {displayItem.purchaseDate} →{" "}
                  {displayItem.currentDate ||
                    getTodayString()}
                </p>
              </div>

              {/* Summary Cards */}
              <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-4">
                <div className="bg-elevated/50 rounded-lg p-3">
                  <p className="text-xs text-brand">초기 투자금</p>
                  <p className="text-lg font-bold text-brand-light">
                    ₩{displayItem.investmentAmount?.toLocaleString()}
                  </p>
                </div>
                <div className="bg-brand/10 rounded-lg p-3">
                  <p className="text-xs text-brand">현재 가치</p>
                  <p className="text-lg font-bold text-brand-light">
                    ₩{displayItem.currentValueKrw?.toLocaleString()}
                  </p>
                </div>
                <div
                  className={`${SOFT_BOX[gainLossTone(displayItem.totalReturnKrw)]} rounded-lg p-3`}
                >
                  <p
                    className={`text-xs ${gainLossClass(displayItem.totalReturnKrw)}`}
                  >
                    총 수익
                  </p>
                  <p
                    className={`text-lg font-bold ${gainLossClass(displayItem.totalReturnKrw)}`}
                  >
                    {formatSigned(displayItem.totalReturnKrw, formatKrw)}
                  </p>
                </div>
                <div
                  className={`${SOFT_BOX[gainLossTone(displayItem.totalReturnPercent)]} rounded-lg p-3`}
                >
                  <p
                    className={`text-xs ${gainLossClass(displayItem.totalReturnPercent)}`}
                  >
                    수익률
                  </p>
                  <p
                    className={`text-lg font-bold ${gainLossClass(displayItem.totalReturnPercent)}`}
                  >
                    {formatSigned(displayItem.totalReturnPercent, formatPercent)}
                  </p>
                </div>
              </div>

              {/* Detailed Cards */}
              <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-3">
                {/* Investment Info */}
                <div className="bg-surface rounded-lg border border-line p-3">
                  <h4 className="text-xs font-semibold text-tx-1 mb-2 flex items-center">
                    <DollarSign className="w-3 h-3 mr-1 text-brand" />
                    투자 정보
                  </h4>
                  <div className="space-y-1 text-xs">
                    <div className="flex justify-between">
                      <span className="text-tx-2">종목</span>
                      <span className="font-medium">
                        {displayItem.symbol || displayItem.name}
                      </span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-tx-2">매수일</span>
                      <span className="font-medium">
                        {displayItem.purchaseDate}
                      </span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-tx-2">보유일</span>
                      <span className="font-medium">
                        {displayItem.daysHeld}일
                      </span>
                    </div>
                  </div>
                </div>

                {/* Stock Performance */}
                <div className="bg-surface rounded-lg border border-line p-3">
                  <h4 className="text-xs font-semibold text-tx-1 mb-2 flex items-center">
                    <TrendingUp className="w-3 h-3 mr-1 text-gain" />
                    주식 성과
                  </h4>
                  <div className="space-y-1 text-xs">
                    <div className="flex justify-between">
                      <span className="text-tx-2">매수가</span>
                      <span className="font-medium">
                        ${displayItem.purchasePrice?.toFixed(2)}
                      </span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-tx-2">현재가</span>
                      <span className="font-medium">
                        ${displayItem.currentPrice?.toFixed(2)}
                      </span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-tx-2">보유 주식</span>
                      <span className="font-medium">
                        {displayItem.shares?.toFixed(4)}주
                      </span>
                    </div>
                  </div>
                </div>

                {/* FX Impact */}
                <div className="bg-surface rounded-lg border border-line p-3">
                  <h4 className="text-xs font-semibold text-tx-1 mb-2 flex items-center">
                    <Activity className="w-3 h-3 mr-1 text-brand" />
                    환율 영향
                  </h4>
                  <div className="space-y-1 text-xs">
                    <div className="flex justify-between">
                      <span className="text-tx-2">매수 환율</span>
                      <span className="font-medium">
                        ₩{displayItem.purchaseFxRate?.toLocaleString()}
                      </span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-tx-2">현재 환율</span>
                      <span className="font-medium">
                        ₩{displayItem.currentFxRate?.toLocaleString()}
                      </span>
                    </div>
                    <div className="flex justify-between">
                      <span className={`text-tx-2`}>환차익률</span>
                      <span
                        className={`font-bold ${gainLossClass(displayItem.fxReturnPercent)}`}
                      >
                        {formatSigned(displayItem.fxReturnPercent, formatPercent)}
                      </span>
                    </div>
                  </div>
                </div>

                {/* Optimal Timing */}
                {(() => {
                  // Simple 전략의 최적 시점 확인
                  const hasSimpleOptimal =
                    displayItem.optimalBuyDate ||
                    displayItem.optimalSellDate;
                  // 종목 비교의 최적 시점 확인
                  const symbolKey = item.symbol || item.name;
                  const optimalPoint = symbolOptimalPoints[symbolKey];

                  if (!hasSimpleOptimal && !optimalPoint) return null;

                  return (
                    <div className="bg-surface rounded-lg border border-brand/25 p-3">
                      <h4 className="text-xs font-semibold text-tx-1 mb-2 flex items-center">
                        <Zap className="w-3 h-3 mr-1 text-brand" />
                        최적 타이밍
                      </h4>
                      <div className="space-y-1 text-xs">
                        {/* Simple strategy optimal buy */}
                        {displayItem.optimalBuyDate && (
                          <>
                            <div className="flex justify-between">
                              <span className="text-tx-2">최적 매수일</span>
                              <span className="font-medium">
                                {displayItem.optimalBuyDate}
                              </span>
                            </div>
                            {displayItem.optimalBuyPrice && (
                              <div className="flex justify-between">
                                <span className="text-tx-2">
                                  최적 매수가
                                </span>
                                <span className="font-medium text-gain">
                                  ${displayItem.optimalBuyPrice?.toFixed(2)}
                                </span>
                              </div>
                            )}
                          </>
                        )}
                        {/* Symbol Comparison optimal buy */}
                        {!displayItem.optimalBuyDate && optimalPoint && (
                          <>
                            <div className="flex justify-between">
                              <span className="text-tx-2">최적 매수일</span>
                              <span className="font-medium">
                                {optimalPoint.buyDate}
                              </span>
                            </div>
                            <div className="flex justify-between">
                              <span className="text-tx-2">최적 매수가</span>
                              <span className="font-medium text-gain">
                                ${optimalPoint.minPrice.toFixed(2)}
                              </span>
                            </div>
                          </>
                        )}

                        {/* Simple strategy optimal sell */}
                        {displayItem.optimalSellDate && (
                          <>
                            <div className="flex justify-between">
                              <span className="text-tx-2">최적 매도일</span>
                              <span className="font-medium">
                                {displayItem.optimalSellDate}
                              </span>
                            </div>
                            {displayItem.optimalSellPrice && (
                              <div className="flex justify-between">
                                <span className="text-tx-2">
                                  최적 매도가
                                </span>
                                <span className="font-medium text-loss">
                                  $
                                  {displayItem.optimalSellPrice?.toFixed(2)}
                                </span>
                              </div>
                            )}
                          </>
                        )}
                        {/* Symbol Comparison optimal sell */}
                        {!displayItem.optimalSellDate && optimalPoint && (
                          <>
                            <div className="flex justify-between">
                              <span className="text-tx-2">최적 매도일</span>
                              <span className="font-medium">
                                {optimalPoint.sellDate}
                              </span>
                            </div>
                            <div className="flex justify-between">
                              <span className="text-tx-2">
                                최적 평가금액
                              </span>
                              <span className="font-medium text-loss">
                                ₩
                                {Math.floor(
                                  optimalPoint.maxValue,
                                ).toLocaleString()}
                                <span className="text-xs text-tx-2 ml-1">
                                  (
                                  {(optimalPoint.maxValue / 10000).toFixed(
                                    0,
                                  )}
                                  만원)
                                </span>
                              </span>
                            </div>
                          </>
                        )}
                      </div>
                    </div>
                  );
                })()}
              </div>
            </div>
          </div>
        );
      })}

      {/* Unified Comparison Chart - at the end */}
      {result.items && result.items.length > 0 && (
        <CompareSymbolsChartMemoized
          items={result.items}
          comparePurchaseDate={comparePurchaseDate}
          compareSaleDate={compareSaleDate}
          onOptimalPointsCalculated={setSymbolOptimalPoints}
        />
      )}
    </div>
  );
};
