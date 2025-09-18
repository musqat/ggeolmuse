package com.muscat.user.domain.account.dto.response;

import com.muscat.user.common.enums.type.TransactionType;
import com.muscat.user.domain.account.entity.AccountHistory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class HistoryResponseDto {

  private Long id;
  private Long accountId;
  private TransactionType transactionType;
  private BigDecimal amount;
  private String currency;
  private BigDecimal balanceAfter;
  private String description;
  private String referenceId;

  // 환전 관련 필드들
  private String fromCurrency;
  private String toCurrency;
  private BigDecimal exchangeRate;
  private BigDecimal originalAmount;
  private boolean isExchange;

  public static HistoryResponseDto from(AccountHistory accountHistory) {
    return new HistoryResponseDto(
        accountHistory.getId(),
        accountHistory.getAccount().getId(),
        accountHistory.getTransactionType(),
        accountHistory.getAmount(),
        accountHistory.getCurrency(),
        accountHistory.getBalanceAfter(),
        accountHistory.getDescription(),
        accountHistory.getReferenceId(),
        accountHistory.getFromCurrency(),
        accountHistory.getToCurrency(),
        accountHistory.getExchangeRate(),
        accountHistory.getOriginalAmount(),
        hasExchangeInfo(accountHistory)
    );
  }

  //환전 정보가 있는지 확인
  private static boolean hasExchangeInfo(AccountHistory accountHistory) {
    return accountHistory.getFromCurrency() != null &&
        accountHistory.getToCurrency() != null &&
        accountHistory.getExchangeRate() != null;
  }

}