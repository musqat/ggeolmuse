package com.muscat.user.domain.account.service;

import com.muscat.user.domain.account.dto.response.HistoryResponseDto;
import java.math.BigDecimal;

public interface AccountHistoryService {

  // 입금 거래 내역 생성 (내부 호출용)
  HistoryResponseDto createDepositHistory(Long accountId, BigDecimal amount,
      String currency, String description,
      String referenceId);

  // 환전 거래 내역 생성 (내부 호출용)
  HistoryResponseDto createExchangeHistory(Long accountId, String fromCurrency,
      String toCurrency, BigDecimal originalAmount,
      BigDecimal exchangedAmount, BigDecimal exchangeRate,
      String description, String referenceId);

}
