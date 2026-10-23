import { useQueryClient } from '@tanstack/react-query';
import { tradeApi } from '../services/api';
import { calculateExecutionPrice, toBackendPriceType, validatePriceRange, type OHLCData, type PriceType } from '../utils/priceUtils';
import { formatTradeResult } from '../utils/tradeResult';
import { getApiErrorMessage } from '../utils/apiError';
import { notify } from '@/components/common/notify';

// 정산 전 매수 뒤 보유 · 거래를 한 번 더 받을 때까지
const SETTLE_REFRESH_MS = 2000;

interface TradeOrderInput {
  accountId: number | null;
  symbol: string;
  orderType: 'buy' | 'sell';
  priceType: PriceType;
  quantity: string;
  limitPrice: string;
  tradeDate: string;
  // 거래일의 봉(없으면 최신 봉)
  ohlc: OHLCData | null;
}

// 체결가와 주문(지정가 범위 검사 · 주문 · 다시 받기 · 정산 전 재조회), 주문이 받아지면 true
export const useTradeOrder = ({
  accountId,
  symbol,
  orderType,
  priceType,
  quantity,
  limitPrice,
  tradeDate,
  ohlc,
}: TradeOrderInput) => {
  const queryClient = useQueryClient();
  const executionPrice = calculateExecutionPrice(priceType, ohlc, Number(limitPrice));

  const submit = async (): Promise<boolean> => {
    if (priceType === 'limit') {
      const result = validatePriceRange(executionPrice, ohlc);
      if (!result.isValid) {
        notify.error(result.message ?? '가격이 허용 범위를 벗어났습니다.');
        return false;
      }
    }

    // 잔액은 서버가 슬리피지 · 수수료까지 넣어 검사
    try {
      const order = {
        accountId: accountId!,
        symbol,
        quantity: Number(quantity),
        tradeDate,
        priceType: toBackendPriceType(priceType),
        manualPrice: priceType === 'limit' ? executionPrice : undefined,
      };

      const { data: trade } = orderType === 'buy'
        ? await tradeApi.buy(order)
        : await tradeApi.sell(order);
      // 잔고와 보유가 바뀌어 매수·매도 가능 수량 · 거래 내역 · 계좌 잔액과 총자산을 다시 받음
      void queryClient.invalidateQueries({ queryKey: ['trade'] });
      void queryClient.invalidateQueries({ queryKey: ['account'] });
      // 진행 중인 계좌 목록 요청은 취소하지 않고 같이 씀
      void queryClient.invalidateQueries({ queryKey: ['accounts'] }, { cancelRefetch: false });
      // 정산 전 매수는 정산이 끝나야 보유에 들어가 잠시 뒤 한 번 더 받음
      if (trade.status === 'PENDING') {
        setTimeout(() => {
          void queryClient.invalidateQueries({ queryKey: ['trade'] });
          void queryClient.invalidateQueries({ queryKey: ['portfolio'] });
          void queryClient.invalidateQueries({ queryKey: ['account'] });
        }, SETTLE_REFRESH_MS);
      }
      notify.success(formatTradeResult(trade));
      return true;
    } catch (error: unknown) {
      notify.error(`주문 실패: ${getApiErrorMessage(error, '알 수 없는 오류')}`);
      return false;
    }
  };

  return { executionPrice, submit };
};
