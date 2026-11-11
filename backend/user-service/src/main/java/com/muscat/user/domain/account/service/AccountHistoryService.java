package com.muscat.user.domain.account.service;

import com.muscat.user.domain.account.dto.response.ExchangeRecordDto;
import com.muscat.user.domain.account.dto.response.HistoryResponseDto;
import java.math.BigDecimal;
import java.util.List;

public interface AccountHistoryService {

  // 입금 거래 내역 생성
  HistoryResponseDto createDepositHistory(Long accountId, BigDecimal amount,
      String currency, String description,
      String referenceId);

  // 환전 거래 내역 생성
  HistoryResponseDto createExchangeHistory(Long accountId, String fromCurrency,
      String toCurrency, BigDecimal originalAmount,
      BigDecimal exchangedAmount, BigDecimal exchangeRate,
      String description, String referenceId);

  // 사용자의 모든 계좌 환전 기록, 오래된 것부터
  List<ExchangeRecordDto> getExchangeRecords(Long userId);

}
