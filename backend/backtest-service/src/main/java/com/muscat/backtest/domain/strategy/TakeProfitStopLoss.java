package com.muscat.backtest.domain.strategy;

import com.muscat.commonlib.dto.OHLCPriceDto;
import java.math.BigDecimal;
import java.math.RoundingMode;

// 익절 · 손절: 장중 고가 · 저가가 평균 단가(수수료 포함) × (1 ± 비율)에 닿은 날 전량 파는 값, 둘 다 닿은 날은 시가부터 익절 기준가 위면 익절 · 아니면 손절
public final class TakeProfitStopLoss {

  private final BigDecimal takeProfitRate;
  private final BigDecimal stopLossRate;

  /** 판 값 · 익절인지 · 거래 이유("10.0%익절" 꼴) */
  public record Exit(BigDecimal price, boolean takeProfit, String trigger) {}

  private TakeProfitStopLoss(BigDecimal takeProfitRate, BigDecimal stopLossRate) {
    this.takeProfitRate = takeProfitRate;
    this.stopLossRate = stopLossRate;
  }

  // 비운 비율은 보지 않음
  public static TakeProfitStopLoss of(BigDecimal takeProfitRate, BigDecimal stopLossRate) {
    return new TakeProfitStopLoss(takeProfitRate, stopLossRate);
  }

  // 평균 단가 = 보유 원가 ÷ 보유 수량, 그 날짜 일봉이 기준가에 닿으면 판 값(아니면 null)
  public Exit check(OHLCPriceDto candle, BigDecimal held, BigDecimal cost) {
    BigDecimal average = cost.divide(held, IntradayCross.PRICE_SCALE, RoundingMode.HALF_UP);
    BigDecimal profit = takeProfitRate == null ? null : IntradayCross.price(average, candle, takeProfitRate, true);
    BigDecimal loss = stopLossRate == null ? null : IntradayCross.price(average, candle, stopLossRate, false);
    boolean takeProfit = loss == null || IntradayCross.atOpen(profit, candle);
    BigDecimal price = takeProfit ? profit : loss;
    if (price == null) {
      return null;
    }
    return new Exit(price, takeProfit, IntradayCross.label(average, price, takeProfit ? "익절" : "손절"));
  }
}
