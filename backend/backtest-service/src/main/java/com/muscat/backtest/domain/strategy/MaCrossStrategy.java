package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.validation.BacktestRequestValidator;
import com.muscat.backtest.domain.dto.request.MaCrossStrategyRequest;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import org.springframework.stereotype.Component;

// 이동평균 교차 전략: 신호 종목(비우면 매매 종목) 수정 종가 단순 이동평균의 짧은 선이 긴 선을 아래에서 위로 넘으면(골든 크로스)
// 매매 종목의 그 날짜(쉬는 날이면 다음 거래일) 종가에 가진 돈 전부로 사고, 위에서 아래로 넘으면(데드 크로스) 전량 팜
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
    BacktestRequestValidator.requireMaWindows(request.shortWindowOrDefault(), request.longWindowOrDefault());
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
