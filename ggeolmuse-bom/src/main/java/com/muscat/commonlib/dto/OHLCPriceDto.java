package com.muscat.commonlib.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "OHLC(Open, High, Low, Close) 가격 정보")
public class OHLCPriceDto {

    @Schema(description = "종목 코드", example = "AAPL", required = true)
    private String symbol;

    @Schema(description = "거래 날짜", example = "2024-09-18", required = true)
    private LocalDate date;

    @Schema(description = "시가", example = "237.50", required = true)
    private BigDecimal openPrice;

    @Schema(description = "고가", example = "240.75", required = true)
    private BigDecimal highPrice;

    @Schema(description = "저가", example = "235.20", required = true)
    private BigDecimal lowPrice;

    @Schema(description = "종가", example = "238.15", required = true)
    private BigDecimal closePrice;

    @Schema(description = "수정 종가", example = "238.15")
    private BigDecimal adjustedClose;

    @Schema(description = "거래량", example = "45678900")
    private Long volume;

    @Schema(description = "통화", example = "USD")
    private String currency;

    @Schema(description = "데이터 유효성", example = "true")
    private Boolean available;
}