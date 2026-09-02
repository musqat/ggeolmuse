package com.muscat.trade.domain.dto.response;

import com.muscat.trade.domain.entity.Dividend;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "배당금 정보")
public record DividendResponseDto(
    @Schema(description = "배당 ID", example = "1")
    Long id,

    @Schema(description = "거래 ID (배당 기준 매수 거래)", example = "100")
    Long tradeId,

    @Schema(description = "계좌 ID", example = "12345678")
    Long accountId,

    @Schema(description = "종목 심볼", example = "AAPL")
    String symbol,

    @Schema(description = "배당 기준 보유 수량", example = "10.00")
    BigDecimal shares,

    @Schema(description = "주당 배당금", example = "0.24")
    BigDecimal dividendPerShare,

    @Schema(description = "세전 배당금", example = "2.40")
    BigDecimal grossAmount,

    @Schema(description = "원천징수 세액 (15.4%)", example = "0.37")
    BigDecimal taxAmount,

    @Schema(description = "세후 배당금", example = "2.03")
    BigDecimal netAmount,

    @Schema(description = "배당 기준일", example = "2024-03-15")
    LocalDate dividendDate,

    @Schema(description = "처리 일시", example = "2024-03-20T10:30:00")
    LocalDateTime processedAt
) {

    // Entity to DTO 변환
    public static DividendResponseDto from(Dividend dividend) {
        return new DividendResponseDto(
            dividend.getId(),
            dividend.getTradeId(),
            dividend.getAccountId(),
            dividend.getSymbol(),
            dividend.getShares(),
            dividend.getDividendPerShare(),
            dividend.getGrossAmount(),
            dividend.getTaxAmount(),
            dividend.getNetAmount(),
            dividend.getDividendDate(),
            dividend.getProcessedAt()
        );
    }
}
