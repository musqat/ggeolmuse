package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.constants.BacktestConstants.MovingAverage;
import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.domain.dto.request.MaBandStrategyRequest;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

// 이동평균 밴드 전략: 들고 있지 않을 때 수정 종가가 N 거래일 평균의 위 선보다 높으면 그 날짜 종가에 가진 돈 전부로 사고,
// 들고 있을 때 아래 선보다 낮으면 전량 팜(처음은 원금, 그 뒤는 판 돈, 시작일 전 일봉을 더 받아 시작일부터 밴드를 봄)
@Component
public class MaBandStrategy extends SignalStrategy<MaBandStrategyRequest> {

  public MaBandStrategy(MarketDataClientWrapper marketData, ResponseMapper responseMapper) {
    super(marketData, responseMapper, MaBandStrategyRequest.class);
  }

  @Override
  public StrategyType getStrategyType() {
    return StrategyType.MA_BAND;
  }

  @Override
  String label() {
    return "이동평균 밴드";
  }

  // 2 ~ 250 거래일, 위 · 아래 비율 0 이상 1 미만
  @Override
  void validateSignal(MaBandStrategyRequest request) {
    int window = request.bandWindowOrDefault();
    if (window < MovingAverage.MIN_WINDOW || window > MovingAverage.MAX_WINDOW
      || !isRate(request.upperRateOrDefault()) || !isRate(request.lowerRateOrDefault())) {
      throw new BacktestException(BacktestResponse.INVALID_BAND);
    }
  }

  private static boolean isRate(BigDecimal rate) {
    return rate.signum() >= 0 && rate.compareTo(BigDecimal.ONE) < 0;
  }

  @Override
  int window(MaBandStrategyRequest request) {
    return request.bandWindowOrDefault();
  }

  @Override
  TradeSignal signal(MaBandStrategyRequest request, SignalPrices prices) {
    return new MaBandSignal(prices, request.bandWindowOrDefault(), request.upperRateOrDefault(),
      request.lowerRateOrDefault());
  }
}
