package com.muscat.backtest.domain.dto.response;

import com.muscat.commonlib.util.MoneyUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// 하루 평가 가치(달러 · 원화)와 그날 고점 대비 낙폭
@Schema(description = "하루 평가 가치")
public record DailyValue(
  @Schema(description = "날짜", example = "2024-03-04")
  LocalDate date,
  @Schema(description = "평가 가치(USD)", example = "1200.00")
  BigDecimal valueUsd,
  @Schema(description = "평가 가치(KRW)", example = "1560000")
  BigDecimal valueKrw,
  @Schema(description = "달러 기준 고점 대비 낙폭(%), 0 이하", example = "-12.50")
  BigDecimal drawdownUsd,
  @Schema(description = "원화 기준 고점 대비 낙폭(%), 0 이하", example = "-8.30")
  BigDecimal drawdownKrw
) {

  public DailyValue(LocalDate date, BigDecimal valueUsd, BigDecimal valueKrw) {
    this(date, valueUsd, valueKrw, null, null);
  }

  // 날마다 같은 순서의 달러 · 원화 낙폭을 채운 목록
  public static List<DailyValue> withDrawdowns(List<DailyValue> values, List<BigDecimal> usd,
    List<BigDecimal> krw) {
    List<DailyValue> result = new ArrayList<>();
    for (int i = 0; i < values.size(); i++) {
      DailyValue value = values.get(i);
      result.add(new DailyValue(value.date(), value.valueUsd(), value.valueKrw(), usd.get(i), krw.get(i)));
    }
    return result;
  }

  // 달러 평가 가치의 원화
  public static BigDecimal toKrw(BigDecimal usd, BigDecimal fxRate) {
    return usd.signum() > 0 ? MoneyUtils.convertUsdToKrw(usd, fxRate) : BigDecimal.ZERO;
  }
}
