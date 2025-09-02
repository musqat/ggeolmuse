package com.muscat.marketdata.domain.dto;

import com.muscat.marketdata.domain.entity.Candle;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 일봉(보정 포함) DTO - adjustFactor: 보정계수(액분/병합 반영) 필요 시 사용 - volume: 거래량
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandleDto {

  private String symbol;          // 예: AAPL
  private LocalDate date;         // 거래일(현지)
  private BigDecimal open;        // 보정 반영된 시가
  private BigDecimal high;        // 보정 반영된 고가
  private BigDecimal low;         // 보정 반영된 저가
  private BigDecimal close;       // 보정 반영된 종가
  private BigDecimal adjustedClose; // 보정 종가(일부 소스에서 제공)
  private Long volume;            // 거래량
  private BigDecimal adjustFactor; // 선택: 보정계수(없으면 null)
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
