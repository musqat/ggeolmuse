package com.muscat.marketdata.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "주식 현재가 정보")
public class StockPriceDto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "종목 코드", example = "AAPL")
    private String symbol;

    @Schema(description = "종목 이름", example = "Apple Inc.")
    private String name;

    @Schema(description = "현재가", example = "238.15")
    private BigDecimal currentPrice;

    @Schema(description = "전일 종가", example = "235.90")
    private BigDecimal previousClose;

    @Schema(description = "등락률", example = "0.95")
    private BigDecimal changePercent;

    @Schema(description = "거래량", example = "45678900")
    private Long volume;

    @Schema(description = "데이터 날짜", example = "2024-09-18")
    private LocalDate date;

    @Schema(description = "통화", example = "USD")
    private String currency;

    @Schema(description = "데이터 유효성", example = "true")
    private boolean available;

    @Schema(description = "시가총액", example = "3600000000000")
    private Long marketCap;

    @Schema(description = "자산 유형", example = "EQUITY")
    private String assetType;
}