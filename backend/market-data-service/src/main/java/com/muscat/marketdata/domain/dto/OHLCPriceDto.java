package com.muscat.marketdata.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "OHLC 가격 데이터 (시가, 고가, 저가, 종가)")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OHLCPriceDto implements Serializable {
  private static final long serialVersionUID = 1L;
  
    @Schema(description = "종목 코드", example = "AAPL")
    private String symbol;
    @Schema(description = "거래일", example = "2024-09-18")
    private LocalDate date;
    @Schema(description = "시가", example = "237.50")
    private BigDecimal openPrice;
    @Schema(description = "고가", example = "242.30")
    private BigDecimal highPrice;
    @Schema(description = "저가", example = "235.80")
    private BigDecimal lowPrice;
    @Schema(description = "종가", example = "238.15")
    private BigDecimal closePrice;
    @Schema(description = "보정 종가 (배당/주식분할 반영)", example = "238.15")
    private BigDecimal adjustedClose;
    @Schema(description = "거래량", example = "45678900")
    private Long volume;
    @Schema(description = "통화 코드", example = "USD")
    private String currency;
    @Schema(description = "데이터 유효성", example = "true")
    private boolean available;
}