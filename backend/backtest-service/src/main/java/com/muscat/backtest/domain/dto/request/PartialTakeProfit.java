package com.muscat.backtest.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

// 부분 익절 한 단계
@Schema(description = "부분 익절 단계")
public record PartialTakeProfit(
  @Schema(description = "수익률: 종가가 평균 단가(수수료 포함)보다 이만큼 오르면 팜 (20% = 0.2, 0 초과, 앞 단계보다 크게)", example = "0.2")
  BigDecimal gainRate,

  @Schema(description = "팔 비율: 그 날짜 보유의 이만큼 팜 (25% = 0.25, 0 초과 1 이하)", example = "0.25")
  BigDecimal sellRate
) {

  // 수익률이 앞 단계보다 큰지(비운 목록 · 비운 단계 · 비운 수익률은 넘김)
  public static boolean ascending(List<PartialTakeProfit> steps) {
    if (steps == null) {
      return true;
    }
    BigDecimal previous = null;
    for (PartialTakeProfit step : steps) {
      if (step == null || step.gainRate() == null) {
        continue;
      }
      if (previous != null && step.gainRate().compareTo(previous) <= 0) {
        return false;
      }
      previous = step.gainRate();
    }
    return true;
  }
}
