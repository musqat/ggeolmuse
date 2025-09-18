package com.muscat.user.domain.account.service;

import com.muscat.user.domain.account.dto.response.HistoryListResponseDto;
import com.muscat.user.domain.account.dto.response.HistoryResponseDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

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

  // 계좌별 거래 내역 조회
  HistoryListResponseDto getAccountHistories(
      Long accountId, int page, int size, Long userId,
      Instant from, Instant to
  );

  // 특정 거래 내역 상세 조회
  HistoryResponseDto getAccountHistory(Long accountId, Long historyId, Long userId);

  // 환전 내역만 조회
  List<HistoryResponseDto> getExchangeHistories(Long accountId, Long userId);

  // 특정 통화의 거래 내역 조회
  List<HistoryResponseDto> getHistoriesByCurrency(Long accountId, String currency, Long userId);

  // 기간별 거래 내역 조회
  List<HistoryResponseDto> getHistoriesByDateRange(Long accountId,
      LocalDateTime startDate,
      LocalDateTime endDate,
      Long userId);

  // 최근 N개 거래 내역 조회
  List<HistoryResponseDto> getRecentHistories(Long accountId, int limit, Long userId);
}
