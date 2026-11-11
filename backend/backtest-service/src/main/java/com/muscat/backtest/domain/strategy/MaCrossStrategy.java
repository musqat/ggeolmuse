package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.constants.BacktestConstants.MovingAverage;
import com.muscat.backtest.common.enums.response.BacktestResponse;
import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.exception.BacktestException;
import com.muscat.backtest.domain.dto.request.MaCrossStrategyRequest;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import org.springframework.stereotype.Component;

// 이동평균 교차 전략: 수정 종가 단순 이동평균의 짧은 선이 긴 선을 아래에서 위로 넘는 날(골든 크로스) 그 날짜 종가에 가진 돈 전부로 사고,
// 위에서 아래로 넘는 날(데드 크로스) 전량 팜(처음은 원금, 그 뒤는 판 돈, 시작일 전 일봉을 더 받아 시작일부터 교차를 봄)
@Component
public class MaCrossStrategy extends SignalStrategy<MaCrossStrategyRequest> {

  public MaCrossStrategy(MarketDataClientWrapper marketData, ResponseMapper responseMapper) {
    super(marketData, responseMapper, MaCrossStrategyRequest.class);
  }

  @Override
  public StrategyType getStrategyType() {
    return StrategyType.MA_CROSS;
  }

  @Override
  String label() {
    return "이동평균 교차";
  }

  // 2 ~ 250 거래일, 짧은 기간 < 긴 기간
  @Override
  void validateSignal(MaCrossStrategyRequest request) {
    int shortWindow = request.shortWindowOrDefault();
    int longWindow = request.longWindowOrDefault();
    if (shortWindow < MovingAverage.MIN_WINDOW || longWindow > MovingAverage.MAX_WINDOW || shortWindow >= longWindow) {
      throw new BacktestException(BacktestResponse.INVALID_MA_WINDOWS);
    }
  }

  @Override
  int window(MaCrossStrategyRequest request) {
    return request.longWindowOrDefault();
  }

  @Override
  TradeSignal signal(MaCrossStrategyRequest request, SignalPrices prices) {
    return new MaCrossSignal(prices, request.shortWindowOrDefault(), request.longWindowOrDefault());
  }
}
