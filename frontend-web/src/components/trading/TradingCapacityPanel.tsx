import React from "react";
import { useQuery } from "@tanstack/react-query";
import { TrendingUp, TrendingDown, DollarSign, Package } from "lucide-react";
import { tradeApi } from "../../services/api";
import { toBackendPriceType, type PriceType } from "@/utils/priceUtils";

interface TradingCapacityPanelProps {
  accountId: number | null;
  symbol: string;
  tradeDate: string;
  orderType: "buy" | "sell";
  priceType: PriceType;
  currentPrice: number;
}

const TradingCapacityPanel: React.FC<TradingCapacityPanelProps> = ({
  accountId,
  symbol,
  tradeDate,
  orderType,
  priceType,
  currentPrice,
}) => {
  const isLimit = priceType === "limit";

  // React Query: 거래 가능 수량 조회. 매수 수량은 주문과 같은 가격 유형으로 센다
  const { data: capacity = null, isLoading: loading } = useQuery({
    queryKey: [
      "trade", "capacity", accountId, symbol, tradeDate, orderType, priceType,
      isLimit ? currentPrice : null,
    ],
    queryFn: async () => {
      const payload = {
        accountId: String(accountId),
        symbol,
        tradeDate: tradeDate,
        priceType: toBackendPriceType(priceType),
        manualPrice: isLimit ? currentPrice : undefined,
      };

      if (orderType === "buy") {
        const response = await tradeApi.canBuy(payload);
        return response.data;
      } else {
        const response = await tradeApi.canSell(payload);
        return response.data;
      }
    },
    enabled: !!accountId && !!symbol && !!tradeDate && currentPrice > 0,
    staleTime: 30 * 1000, // 30초 (실시간 데이터이므로 짧게)
  });

  if (!accountId || !capacity) {
    return null;
  }

  if (loading) {
    return (
      <div className="bg-elevated border border-brand/25 rounded-lg p-4 mb-4">
        <div className="flex items-center justify-center">
          <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-brand"></div>
          <span className="ml-2 text-sm text-brand">
            거래 가능 수량 계산 중...
          </span>
        </div>
      </div>
    );
  }

  if (orderType === "buy") {
    return (
      <div className="bg-gain/10 border border-gain/25 rounded-lg p-4 mb-4">
        <div className="flex items-center mb-3">
          <TrendingUp className="w-5 h-5 text-gain mr-2" />
          <h4 className="font-semibold text-gain">매수 가능 정보</h4>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div className="bg-surface/60 rounded-lg p-3">
            <div className="flex items-center mb-1">
              <Package className="w-4 h-4 text-gain mr-1" />
              <p className="text-xs text-tx-2">최대 매수 가능</p>
            </div>
            <p className="text-lg font-bold text-gain">
              {capacity.maxShares?.toFixed(2) || "0"} 주
            </p>
          </div>

          <div className="bg-surface/60 rounded-lg p-3">
            <div className="flex items-center mb-1">
              <DollarSign className="w-4 h-4 text-gain mr-1" />
              <p className="text-xs text-tx-2">사용 가능 잔액</p>
            </div>
            <p className="text-lg font-bold text-gain">
              ${capacity.availableBalance?.toFixed(2) || "0"}
            </p>
          </div>
        </div>

        <div className="mt-3 p-2 bg-gain/10 rounded text-xs text-gain">
          {`${isLimit ? "지정가" : "기준가"} $${currentPrice.toFixed(2)} · ${
            isLimit ? "수수료" : "슬리피지·수수료"
          } 포함 최대 ${capacity.maxShares?.toFixed(2) || "0"}주 매수 가능`}
        </div>
      </div>
    );
  } else {
    // 매도인 경우
    const hasHoldings = (capacity.currentHoldings ?? 0) > 0;

    return (
      <div
        className={`border rounded-lg p-4 mb-4 ${
          hasHoldings
            ? "bg-loss/10 border-loss/25"
            : "bg-elevated border-line-strong"
        }`}
      >
        <div className="flex items-center mb-3">
          <TrendingDown
            className={`w-5 h-5 mr-2 ${hasHoldings ? "text-loss" : "text-tx-2"}`}
          />
          <h4
            className={`font-semibold ${hasHoldings ? "text-loss" : "text-tx-1"}`}
          >
            매도 가능 정보
          </h4>
        </div>

        {hasHoldings ? (
          <>
            <div className="grid grid-cols-2 gap-3">
              <div className="bg-surface/60 rounded-lg p-3">
                <div className="flex items-center mb-1">
                  <Package className="w-4 h-4 text-loss mr-1" />
                  <p className="text-xs text-tx-2">보유 수량</p>
                </div>
                <p className="text-lg font-bold text-loss">
                  {capacity.currentHoldings?.toFixed(2) || "0"} 주
                </p>
              </div>

              <div className="bg-surface/60 rounded-lg p-3">
                <div className="flex items-center mb-1">
                  <TrendingDown className="w-4 h-4 text-loss mr-1" />
                  <p className="text-xs text-tx-2">매도 가능</p>
                </div>
                <p className="text-lg font-bold text-loss">
                  {capacity.maxSellableShares?.toFixed(2) || "0"} 주
                </p>
              </div>
            </div>

            {capacity.totalValue !== undefined &&
              capacity.totalValue !== null && (
                <div className="mt-3 grid grid-cols-1 gap-2">
                  <div className="bg-surface/60 rounded-lg p-3">
                    <p className="text-xs text-tx-2 mb-1">현재 평가액</p>
                    <p className="text-xl font-bold text-loss">
                      ${capacity.totalValue.toFixed(2)}
                    </p>
                  </div>
                </div>
              )}

            <div className="mt-3 p-2 bg-loss/10 rounded text-xs text-loss">
              {tradeDate} 이전에 매수한{" "}
              {capacity.maxSellableShares?.toFixed(2) || "0"}주만 매도 가능
              (FIFO)
            </div>
          </>
        ) : (
          <div className="bg-surface/60 rounded-lg p-4 text-center">
            <Package className="w-8 h-8 text-tx-3 mx-auto mb-2" />
            <p className="text-sm text-tx-2 font-medium">보유 종목 없음</p>
            <p className="text-xs text-tx-2 mt-1">
              {symbol} 종목을 보유하고 있지 않습니다
            </p>
          </div>
        )}
      </div>
    );
  }
};

export default TradingCapacityPanel;
