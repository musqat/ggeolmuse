package com.muscat.backtest.domain.strategy;

import com.muscat.backtest.common.enums.type.StrategyType;
import com.muscat.backtest.common.validation.BacktestRequestValidator;
import com.muscat.backtest.domain.dto.request.MaBandStrategyRequest;
import com.muscat.backtest.domain.mapper.ResponseMapper;
import com.muscat.backtest.infra.client.MarketDataClientWrapper;
import org.springframework.stereotype.Component;

// 이동평균 밴드 전략: 신호 종목(비우면 매매 종목) 수정 종가가 N 거래일 평균의 위 선보다 높으면 매매 종목의 그 날짜(쉬는 날이면
// 다음 거래일) 종가에 가진 돈 전부로 사고, 아래 선보다 낮으면 전량 팜(사이는 그대로)
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
    BacktestRequestValidator.requireBand(request.bandWindowOrDefault(), request.upperRateOrDefault(),
      request.lowerRateOrDefault());
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
