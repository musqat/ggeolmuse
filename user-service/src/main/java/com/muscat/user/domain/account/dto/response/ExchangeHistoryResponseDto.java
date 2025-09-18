package com.muscat.user.domain.account.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeHistoryResponseDto {

  private Long id;
  private String fromCurrency;
  private String toCurrency;
  private BigDecimal originalAmount;    // 환전 전 금액
  private BigDecimal exchangedAmount;   // 환전 후 금액
  private BigDecimal exchangeRate;      // 적용된 환율
  private String description;
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