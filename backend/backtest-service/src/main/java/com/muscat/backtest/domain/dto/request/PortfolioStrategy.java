package com.muscat.backtest.domain.dto.request;

import com.muscat.backtest.common.constants.BacktestConstants.Band;
import com.muscat.backtest.common.constants.BacktestConstants.MovingAverage;
import com.muscat.backtest.common.enums.type.ExecutionTiming;
import com.muscat.backtest.common.enums.type.PortfolioStrategyType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

// 포트폴리오 종목 줄 전략
@Schema(description = "포트폴리오 종목 줄 전략")
public record PortfolioStrategy(
  @Schema(description = "전략 (LUMP_SUM 기간 안 첫 거래일에 금액 전부, DCA 요청의 적립식 주기로 회차일에 회당 금액, MA_CROSS 이동평균 교차, MA_BAND 이동평균 밴드, 비우면 요청의 매수 방식)", example = "MA_CROSS")
  PortfolioStrategyType type,

  @Schema(description = "짧은 이동평균 기간 (MA_CROSS 에서만, 거래일 2 ~ 250, 긴 기간보다 짧게, 비우면 20)", example = "20")
  Integer shortWindow,

  @Schema(description = "긴 이동평균 기간 (MA_CROSS 에서만, 거래일 2 ~ 250, 비우면 60)", example = "60")
  Integer longWindow,

  @Schema(description = "밴드 평균 기간 (MA_BAND 에서만, 거래일 2 ~ 250, 비우면 200)", example = "200")
  Integer bandWindow,

  @Schema(description = "밴드 위 선 비율 (MA_BAND 에서만, 2.5% = 0.025, 0 이상 1 미만, 비우면 0.025)", example = "0.025")
  BigDecimal upperRate,

  @Schema(description = "밴드 아래 선 비율 (MA_BAND 에서만, 2.5% = 0.025, 0 이상 1 미만, 비우면 0.025)", example = "0.025")
  BigDecimal lowerRate,

  @Schema(description = "체결 시점 (MA_CROSS · MA_BAND 에서만, SAME_DAY_CLOSE 신호 난 날 종가, NEXT_DAY_OPEN 신호 다음 거래일 시가(다음 거래일이 없으면 체결 안 함), 비우면 SAME_DAY_CLOSE)", example = "NEXT_DAY_OPEN")
  ExecutionTiming executionTiming
) {

  // 짧은 기간(비우면 20)
  public int shortWindowOrDefault() {
    return shortWindow != null ? shortWindow : MovingAverage.DEFAULT_SHORT_WINDOW;
  }

  // 긴 기간(비우면 60)
  public int longWindowOrDefault() {
    return longWindow != null ? longWindow : MovingAverage.DEFAULT_LONG_WINDOW;
  }

  // 밴드 평균 기간(비우면 200)
  public int bandWindowOrDefault() {
    return bandWindow != null ? bandWindow : Band.DEFAULT_WINDOW;
  }

  // 위 선 비율(비우면 2.5%)
  public BigDecimal upperRateOrDefault() {
    return upperRate != null ? upperRate : Band.DEFAULT_RATE;
  }

  // 아래 선 비율(비우면 2.5%)
  public BigDecimal lowerRateOrDefault() {
    return lowerRate != null ? lowerRate : Band.DEFAULT_RATE;
  }
}
