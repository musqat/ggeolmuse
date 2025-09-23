package com.muscat.user.domain.account.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "환전 거래 내역 응답")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeHistoryResponseDto {

  @Schema(description = "환전 거래 고유 식별자", example = "1")
  private Long id;
  @Schema(description = "원본 통화 코드", example = "KRW")
  private String fromCurrency;
  @Schema(description = "대상 통화 코드", example = "USD")
  private String toCurrency;
  @Schema(description = "환전 전 금액", example = "100000.00")
  private BigDecimal originalAmount;    // 환전 전 금액
  @Schema(description = "환전 후 금액", example = "75.76")
  private BigDecimal exchangedAmount;   // 환전 후 금액
  @Schema(description = "적용된 환율", example = "1320.50")
  private BigDecimal exchangeRate;      // 적용된 환율
  @Schema(description = "환전 거래 설명", example = "KRW에서 USD로 환전")
  private String description;
  @Schema(description = "환전 거래 일시", example = "2024-09-18T10:30:00")
  private LocalDateTime exchangeDate;   // 환전 날짜

  public static ExchangeHistoryResponseDto from(com.muscat.user.domain.account.entity.AccountHistory accountHistory) {
    return new ExchangeHistoryResponseDto(
        accountHistory.getId(),
        accountHistory.getFromCurrency(),
        accountHistory.getToCurrency(),
        accountHistory.getOriginalAmount(),
        accountHistory.getAmount(),
        accountHistory.getExchangeRate(),
        accountHistory.getDescription(),
        accountHistory.getCreatedAt()
    );
  }

}