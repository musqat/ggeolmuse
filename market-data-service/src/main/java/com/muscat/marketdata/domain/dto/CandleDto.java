package com.muscat.marketdata.domain.dto;

import com.muscat.marketdata.domain.entity.Candle;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "주식 일봉 데이터 (보정 가격 포함)")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandleDto {

  @Schema(description = "종목 코드", example = "AAPL")
  private String symbol;          // 예: AAPL
  @Schema(description = "거래일", example = "2024-09-18")
  private LocalDate date;         // 거래일(현지)
  @Schema(description = "보정 반영 시가", example = "237.50")
  private BigDecimal open;        // 보정 반영된 시가
  @Schema(description = "보정 반영 고가", example = "242.30")
  private BigDecimal high;        // 보정 반영된 고가
  @Schema(description = "보정 반영 저가", example = "235.80")
  private BigDecimal low;         // 보정 반영된 저가
  @Schema(description = "보정 반영 종가", example = "238.15")
  private BigDecimal close;       // 보정 반영된 종가
  @Schema(description = "보정 종가 (배당/주식분할 반영)", example = "238.15")
  private BigDecimal adjustedClose; // 보정 종가(일부 소스에서 제공)
  @Schema(description = "거래량", example = "45678900")
  private Long volume;            // 거래량
  @Schema(description = "보정계수 (주식분할/배당 반영)", example = "1.0")
  private BigDecimal adjustFactor; // 선택: 보정계수(없으면 null)
  @Schema(description = "통화 코드", example = "USD")
  private String currency;        // 예: USD

  public Candle toEntity() {
    return Candle.builder()
        .symbol(symbol)
        .date(date)
        .currency(currency != null ? currency : "USD")
        .open(open)
        .high(high)
        .low(low)
        .close(close)
        .adjustedClose(adjustedClose != null ? adjustedClose : close)
        .volume(volume != null ? volume : 0L)
        .dividendAmount(BigDecimal.ZERO)
        .splitCoefficient(BigDecimal.ONE)
        .build();
  }

}
