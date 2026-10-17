package com.muscat.user.domain.account.service;

import com.muscat.messaging.event.DividendReceivedEvent;
import com.muscat.messaging.event.TradeCompletedEvent;
import com.muscat.user.domain.account.dto.request.CreateAccountRequestDto;
import com.muscat.user.domain.account.dto.response.BalanceResponseDto;
import com.muscat.user.domain.account.dto.response.ExchangeRateRangeDto;
import com.muscat.user.domain.account.entity.Account;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface AccountService {

  // 계좌 생성
  Account createAccount(Long userId, CreateAccountRequestDto request);

  // 사용자의 모든 계좌 조회
  List<Account> getUserAccounts(Long userId);

  // 계좌 삭제
  void deleteAccount(Long accountId, Long userId);

  // 계좌 잔액 조회
  BalanceResponseDto getAccountBalance(Long accountId, Long userId);

  // KRW 입금
  void depositKrw(Long accountId, Long userId, BigDecimal krwAmount);

  // KRW → USD 환전
  void exchangeKrwToUsd(Long accountId, Long userId, BigDecimal krwAmount, BigDecimal exchangeRate);

  // USD → KRW 환전
  void exchangeUsdToKrw(Long accountId, Long userId, BigDecimal usdAmount, BigDecimal exchangeRate);

  // Trade 서비스 전용: USD 잔고 직접 업데이트
  void updateUsdBalance(Long accountId, Long userId, BigDecimal usdAmount, String description);

  // 환율 조회 관련
  BigDecimal getCurrentExchangeRate();

  BigDecimal getExchangeRateByDate(LocalDate date);

  // 환전에 쓸 수 있는 환율 범위. 환전 창이 수동 환율 칸에 보여 주고, 환전할 때 같은 범위로 검사한다
  ExchangeRateRangeDto getExchangeRateRange();

  // Kafka 이벤트 처리
  void processTradeEvent(TradeCompletedEvent event);


  void processDividendReceivedEvent(DividendReceivedEvent event);

  // ==================== ADMIN API ==================== //

  // 사용자 ID로 계좌 목록 조회 (관리자용)
  List<Account> findAccountsByUserId(Long userId);
}
