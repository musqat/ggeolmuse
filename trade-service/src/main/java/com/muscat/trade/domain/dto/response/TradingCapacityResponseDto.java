package com.muscat.trade.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "거래 가능 수량 정보")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradingCapacityResponseDto {

    @Schema(description = "종목 심볼", example = "AAPL")
    private String symbol;

    @Schema(description = "거래 날짜", example = "2024-09-18")
    private LocalDate tradeDate;

    @Schema(description = "현재가", example = "165.75")
    private BigDecimal currentPrice;

    @Schema(description = "사용 가능 잔고", example = "50000.00")
    private BigDecimal availableBalance;

    @Schema(description = "최대 매수 가능 주식 수", example = "301.65")
    private BigDecimal maxShares;

    @Schema(description = "총 투자 가능 금액", example = "49998.75")
    private BigDecimal totalValue;

    @Schema(description = "통화", example = "USD")
    private String currency;

    // 매도용 추가 필드
    @Schema(description = "현재 보유 주식 수", example = "100.00")
    private BigDecimal currentHoldings;

    @Schema(description = "최대 매도 가능 주식 수", example = "100.00")
    private BigDecimal maxSellableShares;
}