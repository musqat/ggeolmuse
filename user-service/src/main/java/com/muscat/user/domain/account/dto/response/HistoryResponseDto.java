package com.muscat.user.domain.account.dto.response;

import com.muscat.user.common.enums.type.TransactionType;
import com.muscat.user.domain.account.entity.AccountHistory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Schema(description = "계좌 거래 내역 상세 정보")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class HistoryResponseDto {

  @Schema(description = "거래 내역 고유 식별자", example = "1")
  private Long id;
  @Schema(description = "계좌 고유 식별자", example = "1")
  private Long accountId;
  @Schema(description = "거래 유형", example = "DEPOSIT")
  private TransactionType transactionType;
  @Schema(description = "거래 금액", example = "100000.00")
  private BigDecimal amount;
  @Schema(description = "통화 코드", example = "KRW")
  private String currency;
  @Schema(description = "거래 후 잔액", example = "500000.00")
  private BigDecimal balanceAfter;
  @Schema(description = "거래 설명", example = "KRW 입금")
  private String description;
  @Schema(description = "참조 아이디", example = "TXN-20240918-001")
  private String referenceId;

  // 환전 관련 필드들
  @Schema(description = "원본 통화 코드 (환전 시)", example = "KRW")
  private String fromCurrency;
  @Schema(description = "대상 통화 코드 (환전 시)", example = "USD")
  private String toCurrency;
  @Schema(description = "적용된 환율 (환전 시)", example = "1320.50")
  private BigDecimal exchangeRate;
  @Schema(description = "원본 금액 (환전 시)", example = "100000.00")
  private BigDecimal originalAmount;
  @Schema(description = "환전 거래 여부", example = "true")
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