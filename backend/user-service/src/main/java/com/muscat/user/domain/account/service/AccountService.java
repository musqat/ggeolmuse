package com.muscat.user.domain.account.service;

import com.muscat.messaging.event.DividendPayoutEvent;
import com.muscat.messaging.event.TradeCompletedEvent;
import com.muscat.user.common.enums.type.TransactionType;
import com.muscat.user.domain.account.dto.request.CreateAccountRequestDto;
import com.muscat.user.domain.account.dto.request.IdempotencyKey;
import com.muscat.user.domain.account.dto.response.BalanceResponseDto;
import com.muscat.user.domain.account.dto.response.ExchangeRateRangeDto;
import com.muscat.user.domain.account.entity.Account;
import com.muscat.user.domain.account.entity.DividendSettlement;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Page;

public interface AccountService {

  // 계좌 생성
  Account createAccount(Long userId, CreateAccountRequestDto request);

  // 사용자의 모든 계좌 조회
  List<Account> getUserAccounts(Long userId);

  // 계좌 삭제
  void deleteAccount(Long accountId, Long userId);

  // 계좌 잔액 조회
  BalanceResponseDto getAccountBalance(Long accountId, Long userId);

  // KRW 입금(같은 중복 요청 키는 한 번만)
  void depositKrw(Long accountId, Long userId, BigDecimal krwAmount, IdempotencyKey key);

  // KRW 입금(중복 요청 키 없음)
  default void depositKrw(Long accountId, Long userId, BigDecimal krwAmount) {
    depositKrw(accountId, userId, krwAmount, null);
  }

  // KRW → USD 환전(같은 중복 요청 키는 한 번만)
  void exchangeKrwToUsd(Long accountId, Long userId, BigDecimal krwAmount, BigDecimal exchangeRate,
    IdempotencyKey key);

  // KRW → USD 환전(중복 요청 키 없음)
  default void exchangeKrwToUsd(Long accountId, Long userId, BigDecimal krwAmount, BigDecimal exchangeRate) {
    exchangeKrwToUsd(accountId, userId, krwAmount, exchangeRate, null);
  }

  // USD → KRW 환전(같은 중복 요청 키는 한 번만)
  void exchangeUsdToKrw(Long accountId, Long userId, BigDecimal usdAmount, BigDecimal exchangeRate,
    IdempotencyKey key);

  // USD → KRW 환전(중복 요청 키 없음)
  default void exchangeUsdToKrw(Long accountId, Long userId, BigDecimal usdAmount, BigDecimal exchangeRate) {
    exchangeUsdToKrw(accountId, userId, usdAmount, exchangeRate, null);
  }

  // Trade 서비스 전용: USD 잔고 직접 업데이트
  void updateUsdBalance(Long accountId, Long userId, BigDecimal usdAmount, TransactionType type, String description);

  // 환율 조회 관련
  BigDecimal getCurrentExchangeRate();

  // 환전에 쓸 수 있는 환율 범위(환전 창의 수동 환율 칸 표시 · 환전 때 검사에 같이 씀)
  ExchangeRateRangeDto getExchangeRateRange();

  // Kafka 이벤트 처리
  void processTradeEvent(TradeCompletedEvent event);

  // 배당 몫 목표 금액을 정산 행과 견줘 차이만 반영
  void applyDividendPayout(DividendPayoutEvent event);

  // ==================== ADMIN API ==================== //

  // 사용자 ID로 계좌 목록 조회 (관리자용)
  List<Account> findAccountsByUserId(Long userId);

  // 배당 정산 행을 계좌 · 종목 · 배당락일 순으로 쪽마다 (관리자용)
  Page<DividendSettlement> getDividendSettlements(int page, int size);
}
