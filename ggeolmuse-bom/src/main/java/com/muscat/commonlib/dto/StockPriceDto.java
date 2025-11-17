package com.muscat.commonlib.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "주식 가격 정보")
public record StockPriceDto(
  @Schema(description = "종목 코드", example = "AAPL", required = true)
  String symbol,

  @Schema(description = "종목 이름", example = "Apple Inc.")
  String name,

  @Schema(description = "현재가", example = "238.15", required = true)
  BigDecimal currentPrice,

  @Schema(description = "전일 종가", example = "235.90")
  BigDecimal previousClose,

  @Schema(description = "등락액", example = "2.25")
  BigDecimal change,

  @Schema(description = "등락률 (%)", example = "0.95")
  BigDecimal changePercent,

  @Schema(description = "거래량", example = "45678900")
  Long volume,

  @Schema(description = "데이터 날짜", example = "2024-09-18")
  LocalDate date,

  @Schema(description = "마지막 업데이트 시간", example = "2024-09-18T15:30:00")
  LocalDateTime lastUpdated,

  // OHLC 데이터 (선택적)
  @Schema(description = "시가 (OHLC)", example = "237.50")
  BigDecimal openPrice,

  @Schema(description = "고가 (OHLC)", example = "240.75")
  BigDecimal highPrice,

  @Schema(description = "저가 (OHLC)", example = "235.20")
  BigDecimal lowPrice,

  @Schema(description = "종가 (OHLC)", example = "238.15")
  BigDecimal closePrice,

  @Schema(description = "수정 종가 (OHLC)", example = "238.15")
  BigDecimal adjustedClose,

  @Schema(description = "통화", example = "USD")
  String currency,

  @Schema(description = "데이터 유효성", example = "true")
  Boolean available,

  @Schema(description = "자산 유형", example = "EQUITY")
  String assetType,

  @Schema(description = "시가총액", example = "3000000000000")
  Long marketCap
) {

}
