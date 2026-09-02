package com.muscat.commonlib.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "OHLC(Open, High, Low, Close) 가격 정보")
public record OHLCPriceDto(
  @Schema(description = "종목 코드", example = "AAPL", required = true)
  String symbol,

  @Schema(description = "거래 날짜", example = "2024-09-18", required = true)
  LocalDate date,

  @Schema(description = "시가", example = "237.50", required = true)
  BigDecimal openPrice,

  @Schema(description = "고가", example = "240.75", required = true)
  BigDecimal highPrice,

  @Schema(description = "저가", example = "235.20", required = true)
  BigDecimal lowPrice,

  @Schema(description = "종가", example = "238.15", required = true)
  BigDecimal closePrice,

  @Schema(description = "수정 종가", example = "238.15")
  BigDecimal adjustedClose,

  @Schema(description = "거래량", example = "45678900")
  Long volume,

  @Schema(description = "통화", example = "USD")
  String currency,

  @Schema(description = "데이터 유효성", example = "true")
  Boolean available
) {

}
